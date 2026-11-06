package de.laermbatz.app.freesound

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.Properties

enum class FreesoundLoopCategory(val label: String) {
    UNCATEGORIZED("Nicht einsortiert"),
    KICK("Kick / Bassdrum"),
    BASSLINE("Bassline"),
    HI_HAT("Hi-Hat"),
    SNARE("Snare"),
    PERCUSSION("Percussion"),
    SYNTH("Synth"),
    EFFECTS("Effekte")
}

data class FreesoundLibraryItem(
    val file: File,
    val title: String,
    val username: String,
    val license: String,
    val originalUrl: String,
    val downloadedAtEpochMillis: Long,
    val category: FreesoundLoopCategory,
    val tags: String = "",
    val categoryManuallySet: Boolean = false,
    val provider: String = "Freesound",
    val sourcePack: String = "",
    val bpm: Float? = null,
    val key: String? = null,
    val keyConfidence: Float = 0f,
    val genre: String = "Unbekannt",
    val archivePath: String = ""
)

class FreesoundLibraryStore(private val directory: File) {
    fun registerImportedPackSample(
        file: File,
        policy: SamplePackLicenseSummary,
        category: FreesoundLoopCategory,
        bpm: Float? = null,
        key: String? = null,
        keyConfidence: Float = 0f,
        genre: String = "Unbekannt",
        archivePath: String = ""
    ): FreesoundLibraryItem = registerLocalSample(
        file = file,
        provider = policy.vendor,
        sourcePack = policy.packName,
        license = policy.libraryLicenseValue(),
        originalUrl = policy.productUrl,
        category = category,
        bpm = bpm,
        key = key,
        keyConfidence = keyConfidence,
        genre = genre,
        archivePath = archivePath,
        provenanceType = "explicit_catalog_policy",
        tags = listOf(file.nameWithoutExtension, archivePath, genre).filter(String::isNotBlank).joinToString(" ")
    )

    /** Registers a user-provided ZIP without claiming that its license was independently verified. */
    fun registerUserZipSample(
        file: File,
        archiveName: String,
        category: FreesoundLoopCategory,
        bpm: Float? = null,
        key: String? = null,
        keyConfidence: Float = 0f,
        genre: String = "Unbekannt",
        archivePath: String = ""
    ): FreesoundLibraryItem = registerLocalSample(
        file = file,
        provider = "Vom Nutzer importiertes ZIP",
        sourcePack = archiveName.take(160),
        license = "$LOCAL_USER_ZIP_LICENSE_PREFIX Lizenz nicht unabhängig geprüft; lokaler Import, keine Rohdatei-Weitergabe.",
        originalUrl = "",
        category = category,
        bpm = bpm,
        key = key,
        keyConfidence = keyConfidence,
        genre = genre,
        archivePath = archivePath,
        provenanceType = "user_selected_zip_unverified",
        tags = listOf(file.nameWithoutExtension, archivePath, archiveName, genre)
            .filter(String::isNotBlank).joinToString(" ")
    )

    /** True if a legacy/partial sidecar needs repair from a re-imported duplicate ZIP entry. */
    fun needsUserZipMetadata(file: File): Boolean {
        val wav = requireLibraryWav(file)
        val properties = loadProperties(sidecarFor(wav))
        val license = properties.getProperty("license").orEmpty()
        val provenance = properties.getProperty("provenance_type").orEmpty()
        return license.isBlank() ||
            license.startsWith(LOCAL_SAMPLE_PACK_LICENSE_PREFIX) && provenance.isBlank()
    }

    fun registerUserZipSampleIfMissing(
        file: File,
        archiveName: String,
        category: FreesoundLoopCategory,
        bpm: Float? = null,
        key: String? = null,
        keyConfidence: Float = 0f,
        genre: String = "Unbekannt",
        archivePath: String = ""
    ): Boolean {
        if (!needsUserZipMetadata(file)) return false
        registerUserZipSample(file, archiveName, category, bpm, key, keyConfidence, genre, archivePath)
        return true
    }

    private fun registerLocalSample(
        file: File,
        provider: String,
        sourcePack: String,
        license: String,
        originalUrl: String,
        category: FreesoundLoopCategory,
        bpm: Float?,
        key: String?,
        keyConfidence: Float,
        genre: String,
        archivePath: String,
        provenanceType: String,
        tags: String
    ): FreesoundLibraryItem {
        val wav = requireLibraryWav(file)
        require(bpm == null || bpm.isFinite() && bpm in 60f..180f) { "Detected BPM is outside 60–180" }
        require(keyConfidence.isFinite() && keyConfidence in 0f..1f) { "Key confidence must be between 0 and 1" }
        val properties = Properties().apply {
            setProperty("name", wav.nameWithoutExtension.replace('_', ' '))
            setProperty("username", provider)
            setProperty("license", license)
            setProperty("provider", provider)
            setProperty("source_pack", sourcePack)
            setProperty("original_url", originalUrl)
            setProperty("downloaded_at_epoch_ms", System.currentTimeMillis().toString())
            setProperty("tags", tags.take(1_024))
            setProperty("category", category.name)
            setProperty("category_manually_set", "false")
            bpm?.let { setProperty("source_bpm", it.toString()) }
            key?.let { setProperty("musical_key", it.take(24)) }
            setProperty("key_confidence", keyConfidence.toString())
            setProperty("genre", genre.ifBlank { "Unbekannt" }.take(64))
            setProperty("archive_path", archivePath.take(512))
            setProperty("provenance_type", provenanceType)
        }
        writePropertiesAtomically(sidecarFor(wav), properties)
        return readItem(wav)
    }

    fun list(): List<FreesoundLibraryItem> {
        val canonicalDirectory = directory.canonicalFile
        return (directory.listFiles() ?: emptyArray())
            .asSequence()
            .filter { it.isFile && it.extension.equals("wav", ignoreCase = true) }
            .filter { wav ->
                try {
                    wav.canonicalFile.parentFile == canonicalDirectory
                } catch (_: IOException) {
                    false
                }
            }
            .map { wav -> readItem(wav) }
            .sortedWith(compareBy<FreesoundLibraryItem> { it.title }.thenBy { it.file.name })
            .toList()
    }

    fun setCategory(
        item: FreesoundLibraryItem,
        category: FreesoundLoopCategory,
        manualOverride: Boolean = true
    ): FreesoundLibraryItem {
        val wav = requireLibraryWav(item.file)
        val sidecar = sidecarFor(wav)
        val properties = loadProperties(sidecar)
        properties.setProperty("category", category.name)
        properties.setProperty("category_manually_set", manualOverride.toString())
        writePropertiesAtomically(sidecar, properties)
        return item.copy(file = wav, category = category, categoryManuallySet = manualOverride)
    }

    fun delete(item: FreesoundLibraryItem): Boolean {
        val wav = requireLibraryWav(item.file)
        if (!wav.delete()) return false
        val sidecar = sidecarFor(wav)
        if (sidecar.exists()) sidecar.delete()
        return true
    }

    private fun readItem(wav: File): FreesoundLibraryItem {
        val properties = loadProperties(sidecarFor(wav))
        val oldLicense = properties.getProperty("license").orEmpty()
        if (oldLicense.startsWith(LOCAL_SAMPLE_PACK_LICENSE_PREFIX) &&
            properties.getProperty("provenance_type").isNullOrBlank()
        ) {
            properties.setProperty(
                "license",
                "$LOCAL_USER_ZIP_LICENSE_PREFIX Lizenz nicht unabhängig geprüft; ältere Zuordnung beruhte nur auf einem ZIP-Dateinamen."
            )
            properties.setProperty("provider", "Vom Nutzer importiertes ZIP")
            properties.setProperty("username", "Vom Nutzer importiertes ZIP")
            properties.setProperty("provenance_type", "legacy_filename_unverified")
            writePropertiesAtomically(sidecarFor(wav), properties)
        }
        val category = properties.getProperty("category")
            ?.let { stored -> FreesoundLoopCategory.entries.firstOrNull { it.name == stored } }
            ?: FreesoundLoopCategory.UNCATEGORIZED
        val key = properties.getProperty("musical_key")?.takeIf { KEY_FORMAT.matches(it) }
        val keyConfidence = properties.getProperty("key_confidence")?.toFloatOrNull()
            ?.takeIf { it.isFinite() && it in 0f..1f } ?: 0f
        val bpm = properties.getProperty("source_bpm")?.toFloatOrNull()
            ?.takeIf { it.isFinite() && it in 60f..180f }
        return FreesoundLibraryItem(
            file = wav,
            title = properties.getProperty("name") ?: wav.nameWithoutExtension,
            username = properties.getProperty("username") ?: "",
            license = properties.getProperty("license") ?: "",
            originalUrl = properties.getProperty("original_url") ?: "",
            downloadedAtEpochMillis = properties.getProperty("downloaded_at_epoch_ms")?.toLongOrNull() ?: 0L,
            category = category,
            tags = properties.getProperty("tags") ?: "",
            categoryManuallySet = properties.getProperty("category_manually_set")?.toBooleanStrictOrNull() ?: false,
            provider = properties.getProperty("provider") ?: "Freesound",
            sourcePack = properties.getProperty("source_pack") ?: "",
            bpm = bpm,
            key = key,
            keyConfidence = keyConfidence,
            genre = properties.getProperty("genre")?.takeIf(String::isNotBlank) ?: "Unbekannt",
            archivePath = properties.getProperty("archive_path") ?: ""
        )
    }

    private fun requireLibraryWav(file: File): File {
        val canonicalDirectory = directory.canonicalFile
        val canonicalWav = file.canonicalFile
        require(canonicalWav.isFile && canonicalWav.extension.equals("wav", ignoreCase = true)) {
            "The item must refer to an existing WAV file in the library directory"
        }
        require(canonicalWav.parentFile == canonicalDirectory) {
            "The item must be a direct child of the library directory"
        }
        return canonicalWav
    }

    private fun sidecarFor(wav: File): File = File(wav.parentFile, "${wav.nameWithoutExtension}.properties")

    private fun loadProperties(sidecar: File): Properties = Properties().apply {
        if (sidecar.isFile) FileInputStream(sidecar).use(::load)
    }

    private fun writePropertiesAtomically(sidecar: File, properties: Properties) {
        val parent = sidecar.parentFile ?: throw IOException("Library metadata has no parent directory")
        val temporary = Files.createTempFile(parent.toPath(), "${sidecar.name}.", ".tmp")
        try {
            FileOutputStream(temporary.toFile()).use { output ->
                properties.store(output, "Local loop source, license and analysis metadata")
            }
            try {
                Files.move(temporary, sidecar.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, sidecar.toPath(), REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    companion object {
        private val KEY_FORMAT = Regex("^(?:A|B|C|D|E|F|G)(?:#)? (?:major|minor)$")
    }
}
