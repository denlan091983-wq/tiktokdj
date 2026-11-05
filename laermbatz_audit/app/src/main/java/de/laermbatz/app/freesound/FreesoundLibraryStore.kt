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
    KICK("Kick"),
    BASSLINE("Bassline"),
    HI_HAT("Hi-Hat"),
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
    val sourcePack: String = ""
)

class FreesoundLibraryStore(private val directory: File) {
    fun registerImportedPackSample(
        file: File,
        policy: SamplePackLicenseSummary,
        category: FreesoundLoopCategory
    ): FreesoundLibraryItem {
        val wav = requireLibraryWav(file)
        val properties = Properties().apply {
            setProperty("name", wav.nameWithoutExtension.replace('_', ' '))
            setProperty("username", policy.vendor)
            setProperty("license", policy.libraryLicenseValue())
            setProperty("provider", policy.vendor)
            setProperty("source_pack", policy.packName)
            setProperty("original_url", policy.productUrl)
            setProperty("downloaded_at_epoch_ms", System.currentTimeMillis().toString())
            setProperty("tags", wav.nameWithoutExtension)
            setProperty("category", category.name)
            setProperty("category_manually_set", "false")
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
        val category = properties.getProperty("category")
            ?.let { stored -> FreesoundLoopCategory.entries.firstOrNull { it.name == stored } }
            ?: FreesoundLoopCategory.UNCATEGORIZED
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
            sourcePack = properties.getProperty("source_pack") ?: ""
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
                properties.store(output, "Freesound source and license metadata")
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
}
