package de.laermbatz.app.freesound

import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.util.Properties
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FreesoundLibraryStoreTest {
    private lateinit var directory: File
    private lateinit var store: FreesoundLibraryStore

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("freesound-library-test").toFile()
        store = FreesoundLibraryStore(directory)
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun listsOnlyWavsInStableTitleThenFilenameOrder() {
        val zulu = wav("zulu.wav")
        val alphaZ = wav("alpha-z.WAV")
        val alphaA = wav("alpha-a.wav")
        wav("ignored.mp3")
        File(directory, "not-a-file.wav").mkdir()
        writeMetadata(zulu, "Zulu")
        writeMetadata(alphaZ, "Alpha")
        writeMetadata(alphaA, "Alpha")

        val listed = store.list()

        assertEquals(listOf(alphaA.name, alphaZ.name, zulu.name), listed.map { it.file.name })
        assertEquals(listOf("Alpha", "Alpha", "Zulu"), listed.map { it.title })
    }

    @Test
    fun missingOlderAndUnknownCategoriesDefaultToUncategorized() {
        val withoutSidecar = wav("without-sidecar.wav")
        val olderSidecar = wav("older.wav")
        val unknownCategory = wav("unknown.wav")
        writeMetadata(olderSidecar, "Older", includeCategory = false)
        writeMetadata(unknownCategory, "Unknown", category = "FUTURE_CATEGORY")

        val categories = store.list().associate { it.file.name to it.category }

        assertEquals(FreesoundLoopCategory.UNCATEGORIZED, categories[withoutSidecar.name])
        assertEquals(FreesoundLoopCategory.UNCATEGORIZED, categories[olderSidecar.name])
        assertEquals(FreesoundLoopCategory.UNCATEGORIZED, categories[unknownCategory.name])
    }

    @Test
    fun setCategoryPersistsAndPreservesExistingMetadata() {
        val wav = wav("sample.wav")
        writeMetadata(wav, "Original title", category = "KICK", extra = mapOf("provider" to "Freesound", "custom" to "kept", "tags" to "dark techno bassline"))
        val item = store.list().single()

        val updated = store.setCategory(item, FreesoundLoopCategory.BASSLINE)
        val reloaded = store.list().single()
        val properties = loadSidecar(wav)

        assertEquals(FreesoundLoopCategory.BASSLINE, updated.category)
        assertEquals(FreesoundLoopCategory.BASSLINE, reloaded.category)
        assertTrue(reloaded.categoryManuallySet)
        assertEquals("Original title", reloaded.title)
        assertEquals("offline-user", reloaded.username)
        assertEquals("Creative Commons 0", reloaded.license)
        assertEquals("https://freesound.org/sounds/42/", reloaded.originalUrl)
        assertEquals(123456789L, reloaded.downloadedAtEpochMillis)
        assertEquals("dark techno bassline", reloaded.tags)
        assertEquals("BASSLINE", properties.getProperty("category"))
        assertEquals("true", properties.getProperty("category_manually_set"))
        assertEquals("Freesound", properties.getProperty("provider"))
        assertEquals("kept", properties.getProperty("custom"))
    }

    @Test
    fun categoryAndDeleteRejectFilesOutsideTheLibrary() {
        val outside = File(directory.parentFile, "outside-library.wav")
        outside.writeBytes(byteArrayOf(1, 2, 3))
        try {
            val outsideItem = FreesoundLibraryItem(
                file = outside,
                title = "Outside",
                username = "",
                license = "",
                originalUrl = "",
                downloadedAtEpochMillis = 0L,
                category = FreesoundLoopCategory.UNCATEGORIZED
            )

            assertThrows(IllegalArgumentException::class.java) {
                store.setCategory(outsideItem, FreesoundLoopCategory.KICK)
            }
            assertThrows(IllegalArgumentException::class.java) {
                store.delete(outsideItem)
            }
            assertTrue(outside.exists())
        } finally {
            outside.delete()
        }
    }

    @Test
    fun deleteRemovesOnlyTheSelectedWavAndItsSidecar() {
        val selected = wav("selected.wav")
        val other = wav("other.wav")
        writeMetadata(selected, "Selected")
        writeMetadata(other, "Other")
        val selectedSidecar = File(directory, "selected.properties")
        val otherSidecar = File(directory, "other.properties")

        assertTrue(store.delete(store.list().single { it.file.name == selected.name }))

        assertFalse(selected.exists())
        assertFalse(selectedSidecar.exists())
        assertTrue(other.exists())
        assertTrue(otherSidecar.exists())
        assertEquals(listOf(other.name), store.list().map { it.file.name })
    }

    @Test
    fun automaticCategoryDoesNotLockOutLaterAutoClassification() {
        val wav = wav("auto.wav")
        writeMetadata(wav, "Auto")
        val categorized = store.setCategory(store.list().single(), FreesoundLoopCategory.KICK, manualOverride = false)

        assertEquals(FreesoundLoopCategory.KICK, categorized.category)
        assertFalse(categorized.categoryManuallySet)
        assertFalse(store.list().single().categoryManuallySet)
    }

    private fun wav(name: String): File = File(directory, name).apply {
        writeBytes(byteArrayOf(1, 2, 3, 4))
    }

    private fun writeMetadata(
        wav: File,
        title: String,
        category: String? = "UNCATEGORIZED",
        includeCategory: Boolean = true,
        extra: Map<String, String> = emptyMap()
    ) {
        val properties = Properties().apply {
            setProperty("name", title)
            setProperty("username", "offline-user")
            setProperty("license", "Creative Commons 0")
            setProperty("original_url", "https://freesound.org/sounds/42/")
            setProperty("downloaded_at_epoch_ms", "123456789")
            if (includeCategory && category != null) setProperty("category", category)
            extra.forEach { (key, value) -> setProperty(key, value) }
        }
        File(directory, "${wav.nameWithoutExtension}.properties").outputStream().use { properties.store(it, "test metadata") }
    }

    private fun loadSidecar(wav: File): Properties = Properties().apply {
        FileInputStream(File(directory, "${wav.nameWithoutExtension}.properties")).use(::load)
    }
}
