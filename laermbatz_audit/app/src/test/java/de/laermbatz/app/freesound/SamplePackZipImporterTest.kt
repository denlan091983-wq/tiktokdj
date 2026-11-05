package de.laermbatz.app.freesound

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SamplePackZipImporterTest {
    private lateinit var root: File
    private lateinit var target: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("sample-pack-import-test").toFile()
        target = File(root, "target")
        assertTrue(target.mkdir())
    }

    @After
    fun tearDown() {
        removeTree(root)
    }

    @Test
    fun importsValidWavAndReportsItsHashAndName() {
        val wav = validPcmWav()

        val result = importer(zip("folder/My Loop.WAV" to wav)).import()

        assertEquals(ImportStatus.COMPLETE, result.status)
        assertEquals(1, result.importedFiles.size)
        assertEquals("My_Loop.wav", result.importedFiles.single().name.substringBeforeLast('_') + ".wav")
        assertEquals(sha256(wav), result.importedFiles.single().sha256)
        assertEquals(wav.toList(), result.importedFiles.single().file.readBytes().toList())
    }

    @Test
    fun importsOnlyWavFiles() {
        val result = importer(zip(
            "notes.txt" to "notes".toByteArray(),
            "sound.wav" to validPcmWav(),
            "folder/" to byteArrayOf()
        )).import()

        assertEquals(1, result.importedFiles.size)
        assertEquals(2, result.skippedNonWavCount)
    }

    @Test
    fun flattensTraversalNamesWithoutCreatingArchivePaths() {
        val outside = File(root, "escape.wav")

        val result = importer(zip("../../escape.wav" to validPcmWav())).import()

        assertEquals(1, result.importedFiles.size)
        assertEquals(target, result.importedFiles.single().file.parentFile)
        assertFalse(outside.exists())
        assertTrue(result.importedFiles.single().name.endsWith(".wav"))
    }

    @Test
    fun countsInvalidWavWithoutImportingIt() {
        val result = importer(zip("bad.wav" to byteArrayOf(1, 2, 3, 4))).import()

        assertEquals(ImportStatus.COMPLETE, result.status)
        assertEquals(1, result.invalidWavCount)
        assertTrue(result.importedFiles.isEmpty())
    }

    @Test
    fun deduplicatesBySha256AcrossImports() {
        val wav = validPcmWav()
        val first = importer(zip("first.wav" to wav)).import()
        val second = importer(zip("renamed.wav" to wav)).import()

        assertEquals(1, first.importedFiles.size)
        assertEquals(1, second.duplicateCount)
        assertTrue(second.importedFiles.isEmpty())
        assertEquals(1, target.listFiles()?.count { it.extension == "wav" })
    }

    @Test
    fun blockedMarkerRollsBackAllStagedFilesWithoutTouchingEarlierImports() {
        val previous = importer(zip("previous.wav" to validPcmWav(byte = 1))).import().importedFiles.single().file
        val result = importer(
            zip("new.wav" to validPcmWav(byte = 2), "LICENSE.TXT" to byteArrayOf(9)),
            rejectEntryNames = listOf("license.txt")
        ).import()

        assertEquals(ImportStatus.REJECTED, result.status)
        assertNotNull(result.rejectionReason)
        assertTrue(result.importedFiles.isEmpty())
        assertTrue(previous.exists())
        assertEquals(1, target.listFiles()?.count { it.extension == "wav" })
        assertNoStagingDirectories()
    }

    @Test
    fun stopsAtMaximumEntryCountAndDoesNotCommitPartialArchive() {
        val result = importer(
            zip("one.wav" to validPcmWav(byte = 1), "two.wav" to validPcmWav(byte = 2)),
            maxEntries = 1
        ).import()

        assertEquals(ImportStatus.MAX_ENTRIES_REACHED, result.status)
        assertTrue(result.importedFiles.isEmpty())
        assertTrue(target.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun skipsWavThatExceedsPerFileLimit() {
        val wav = validPcmWav()
        val result = importer(zip("too-big.wav" to wav), maxWavBytes = wav.size.toLong() - 1).import()

        assertEquals(1, result.oversizedWavCount)
        assertTrue(result.importedFiles.isEmpty())
    }

    @Test
    fun veryLargeNonWavAbortsArchiveWithinOneByteOfTotalLimitAndCleansStaging() {
        val previous = importer(zip("previous.wav" to validPcmWav(byte = 1))).import().importedFiles.single().file
        val stagedWav = validPcmWav(byte = 2)
        val limit = stagedWav.size.toLong() + 32

        val result = importer(
            zip("new.wav" to stagedWav, "large.bin" to ByteArray(1024 * 1024) { 7 }),
            maxTotalDecompressedBytes = limit
        ).import()

        assertEquals(ImportStatus.TOTAL_DECOMPRESSED_SIZE_LIMIT, result.status)
        assertTrue(result.importedFiles.isEmpty())
        assertEquals(limit + 1, result.decompressedBytesRead)
        assertTrue(previous.exists())
        assertEquals(1, target.listFiles().orEmpty().count { it.extension == "wav" })
        assertNoStagingDirectories()
    }

    @Test
    fun cumulativeDecompressedBytesAcrossEntriesAbortWithoutPartialCommit() {
        val previous = importer(zip("previous.wav" to validPcmWav(byte = 1))).import().importedFiles.single().file
        val stagedWav = validPcmWav(byte = 2)
        val firstNonWav = ByteArray(5) { 1 }
        val secondNonWav = ByteArray(5) { 2 }
        val limit = stagedWav.size.toLong() + firstNonWav.size + 2

        val result = importer(
            zip("new.wav" to stagedWav, "one.bin" to firstNonWav, "two.bin" to secondNonWav),
            maxTotalDecompressedBytes = limit
        ).import()

        assertEquals(ImportStatus.TOTAL_DECOMPRESSED_SIZE_LIMIT, result.status)
        assertTrue(result.importedFiles.isEmpty())
        assertEquals(limit + 1, result.decompressedBytesRead)
        assertTrue(previous.exists())
        assertEquals(1, target.listFiles().orEmpty().count { it.extension == "wav" })
        assertNoStagingDirectories()
    }

    @Test
    fun totalLimitKeepsAlreadyCommittedSamplesAndReportsBlockedVolume() {
        val first = validPcmWav(byte = 1)
        val second = validPcmWav(byte = 2)
        val result = importer(
            zip("first.wav" to first, "second.wav" to second),
            maxTotalWavBytes = first.size.toLong(),
            minimumFreeSpaceBytes = 0
        ).import()

        assertEquals(ImportStatus.TOTAL_WAV_LIMIT_REACHED, result.status)
        assertEquals(1, result.importedFiles.size)
        assertEquals(second.size.toLong(), result.limitedVolumeBytes)
    }

    @Test
    fun corruptZipCleansStagingAndDoesNotCommit() {
        val corrupted = zip("will-fail.wav" to validPcmWav()).also { bytes ->
            val nameLength = 13 // "will-fail.wav"
            val payloadStart = 30 + nameLength
            bytes[payloadStart] = (bytes[payloadStart].toInt() xor 0x01).toByte()
        }

        val result = importer(corrupted).import()

        assertEquals(ImportStatus.INVALID_ZIP, result.status)
        assertTrue(result.importedFiles.isEmpty())
        assertTrue(target.listFiles().orEmpty().isEmpty())
        assertNoStagingDirectories()
    }

    private fun importer(
        archive: ByteArray,
        rejectEntryNames: List<String> = emptyList(),
        maxEntries: Int = SamplePackZipImporter.DEFAULT_MAX_ENTRIES,
        maxWavBytes: Long = SamplePackZipImporter.DEFAULT_MAX_WAV_BYTES,
        maxTotalWavBytes: Long = SamplePackZipImporter.DEFAULT_MAX_TOTAL_WAV_BYTES,
        maxTotalDecompressedBytes: Long = SamplePackZipImporter.DEFAULT_MAX_TOTAL_DECOMPRESSED_BYTES,
        minimumFreeSpaceBytes: Long = 0
    ) = SamplePackZipImporter(
        inputStream = archive.inputStream(),
        archiveDisplayName = "test pack.zip",
        destinationDirectory = target,
        rejectEntryNames = rejectEntryNames,
        maxEntries = maxEntries,
        maxWavBytes = maxWavBytes,
        maxTotalWavBytes = maxTotalWavBytes,
        maxTotalDecompressedBytes = maxTotalDecompressedBytes,
        minimumFreeSpaceBytes = minimumFreeSpaceBytes,
        freeSpaceProvider = { Long.MAX_VALUE }
    )

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray =
        ByteArrayOutputStream().also { bytes ->
            ZipOutputStream(bytes).use { zip ->
                entries.forEach { (name, content) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content)
                    zip.closeEntry()
                }
            }
        }.toByteArray()

    private fun validPcmWav(byte: Int = 0): ByteArray {
        val fmt = ByteArrayOutputStream().apply {
            writeLittleEndianShort(1)
            writeLittleEndianShort(1)
            writeLittleEndianInt(44_100)
            writeLittleEndianInt(88_200)
            writeLittleEndianShort(2)
            writeLittleEndianShort(16)
        }.toByteArray()
        val body = ByteArrayOutputStream().apply {
            write("WAVE".toByteArray(Charsets.US_ASCII))
            writeChunk("fmt ", fmt)
            writeChunk("data", byteArrayOf(byte.toByte(), 0))
        }.toByteArray()
        return ByteArrayOutputStream().apply {
            write("RIFF".toByteArray(Charsets.US_ASCII))
            writeLittleEndianInt(body.size)
            write(body)
        }.toByteArray()
    }

    private fun ByteArrayOutputStream.writeChunk(name: String, content: ByteArray) {
        write(name.toByteArray(Charsets.US_ASCII))
        writeLittleEndianInt(content.size)
        write(content)
        if (content.size % 2 != 0) write(0)
    }

    private fun ByteArrayOutputStream.writeLittleEndianShort(value: Int) {
        write(value and 0xff)
        write((value ushr 8) and 0xff)
    }

    private fun ByteArrayOutputStream.writeLittleEndianInt(value: Int) {
        write(value and 0xff)
        write((value ushr 8) and 0xff)
        write((value ushr 16) and 0xff)
        write((value ushr 24) and 0xff)
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private fun assertNoStagingDirectories() {
        assertTrue(target.listFiles().orEmpty().none { it.name.startsWith(".sample-pack-import-") })
    }

    private fun removeTree(file: File) {
        if (file.isDirectory) file.listFiles()?.forEach(::removeTree)
        file.delete()
    }
}
