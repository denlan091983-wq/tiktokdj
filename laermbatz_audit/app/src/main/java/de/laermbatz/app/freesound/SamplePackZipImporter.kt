package de.laermbatz.app.freesound

import de.laermbatz.app.audio.WavDecoder
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.PushbackInputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/** Imports validated WAV files from one ZIP archive into a private, flat directory. */
class SamplePackZipImporter(
    private val inputStream: InputStream,
    private val archiveDisplayName: String,
    private val destinationDirectory: File,
    rejectEntryNames: List<String> = emptyList(),
    private val maxCompressedBytes: Long = DEFAULT_MAX_COMPRESSED_BYTES,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val maxWavBytes: Long = DEFAULT_MAX_WAV_BYTES,
    private val maxTotalWavBytes: Long = DEFAULT_MAX_TOTAL_WAV_BYTES,
    private val minimumFreeSpaceBytes: Long = DEFAULT_MINIMUM_FREE_SPACE_BYTES,
    private val maxTotalDecompressedBytes: Long = DEFAULT_MAX_TOTAL_DECOMPRESSED_BYTES,
    private val freeSpaceProvider: (File) -> Long = { it.usableSpace }
) {
    private val rejectedBasenames = rejectEntryNames
        .map { basename(it).lowercase(Locale.ROOT) }
        .toSet()

    init {
        require(maxCompressedBytes > 0) { "maxCompressedBytes must be positive" }
        require(maxEntries > 0) { "maxEntries must be positive" }
        require(maxWavBytes > 0) { "maxWavBytes must be positive" }
        require(maxTotalWavBytes >= 0) { "maxTotalWavBytes must not be negative" }
        require(maxTotalDecompressedBytes >= 0) { "maxTotalDecompressedBytes must not be negative" }
        require(minimumFreeSpaceBytes >= 0) { "minimumFreeSpaceBytes must not be negative" }
    }

    fun import(): ImportResult {
        val stats = MutableStats()
        var stagingDirectory: File? = null
        var status = ImportStatus.COMPLETE
        var rejectionReason: String? = null
        val candidates = mutableListOf<StagedSample>()

        try {
            if (!destinationDirectory.exists() && !destinationDirectory.mkdirs()) {
                throw IOException("Could not create destination directory")
            }
            if (!destinationDirectory.isDirectory) {
                throw IOException("Destination is not a directory")
            }
            stagingDirectory = createStagingDirectory(destinationDirectory)
            val countedInput = LimitedCountingInputStream(inputStream, maxCompressedBytes)
            val pushback = PushbackInputStream(BufferedInputStream(countedInput), 4)
            try {
                val prefix = ByteArray(4)
                var prefixLength = 0
                while (prefixLength < prefix.size) {
                    val read = pushback.read(prefix, prefixLength, prefix.size - prefixLength)
                    if (read < 0) break
                    prefixLength += read
                }
                pushback.unread(prefix, 0, prefixLength)
                if (!isZipSignature(prefix, prefixLength)) {
                    status = ImportStatus.INVALID_ZIP
                } else {
                    ZipInputStream(pushback).use { zip ->
                        var entryCount = 0
                        while (true) {
                            val entry = zip.nextEntry ?: break
                            entryCount++
                            if (entryCount > maxEntries) {
                                status = ImportStatus.MAX_ENTRIES_REACHED
                                break
                            }

                            val entryBasename = basename(entry.name)
                            if (entryBasename.lowercase(Locale.ROOT) in rejectedBasenames) {
                                status = ImportStatus.REJECTED
                                rejectionReason = "Archive contains blocked entry basename: $entryBasename"
                                break
                            }

                            val isWav = !entry.isDirectory && entryBasename.endsWith(".wav", ignoreCase = true)
                            val budgetedEntry = TotalDecompressedBudgetInputStream(zip, stats, maxTotalDecompressedBytes)
                            if (!isWav) {
                                stats.skippedNonWavCount++
                                drainEntry(budgetedEntry)
                                zip.closeEntry()
                                continue
                            }

                            val stagedFile = File(stagingDirectory, "entry_${entryCount.toString().padStart(5, '0')}.stage")
                            val copied = copyEntryBounded(budgetedEntry, stagedFile, maxWavBytes)
                            if (copied.exceededLimit) {
                                stats.oversizedWavCount++
                                stagedFile.delete()
                                zip.closeEntry()
                                continue
                            }
                            zip.closeEntry()

                            try {
                                FileInputStream(stagedFile).use { WavDecoder.decode(it) }
                                candidates.add(
                                    StagedSample(
                                        stagedFile = stagedFile,
                                        displayName = sanitizedWavName(entryBasename),
                                        sha256 = sha256(stagedFile),
                                        archivePath = sanitizedArchivePath(entry.name),
                                        sizeBytes = stagedFile.length()
                                    )
                                )
                            } catch (_: IllegalArgumentException) {
                                stats.invalidWavCount++
                                stagedFile.delete()
                            }
                        }
                    }
                }
            } catch (_: TotalDecompressedSizeLimitException) {
                status = ImportStatus.TOTAL_DECOMPRESSED_SIZE_LIMIT
            } catch (_: InsufficientFreeSpaceException) {
                status = ImportStatus.INSUFFICIENT_SPACE
            } catch (_: CompressedSizeLimitException) {
                status = ImportStatus.COMPRESSED_SIZE_LIMIT
            } catch (_: ZipException) {
                status = ImportStatus.INVALID_ZIP
            } catch (_: IOException) {
                status = ImportStatus.INVALID_ZIP
            } finally {
                stats.compressedBytesRead = countedInput.bytesRead
            }
        } catch (_: IOException) {
            status = if (status == ImportStatus.COMPLETE) ImportStatus.IO_ERROR else status
        } finally {
            try {
                inputStream.close()
            } catch (_: IOException) {
                // The archive result is still useful; the stream has already been consumed or rejected.
            }
        }

        if (status == ImportStatus.REJECTED || status == ImportStatus.INVALID_ZIP ||
            status == ImportStatus.COMPRESSED_SIZE_LIMIT || status == ImportStatus.TOTAL_DECOMPRESSED_SIZE_LIMIT ||
            status == ImportStatus.MAX_ENTRIES_REACHED || status == ImportStatus.INSUFFICIENT_SPACE ||
            status == ImportStatus.IO_ERROR
        ) {
            candidates.clear()
        } else {
            status = commitCandidates(candidates, stats)
        }

        stagingDirectory?.let(::deleteTree)
        return ImportResult(
            archiveDisplayName = archiveDisplayName,
            importedFiles = stats.importedFiles.toList(),
            duplicateFiles = stats.duplicateFiles.toList(),
            skippedNonWavCount = stats.skippedNonWavCount,
            invalidWavCount = stats.invalidWavCount,
            oversizedWavCount = stats.oversizedWavCount,
            duplicateCount = stats.duplicateCount,
            limitedVolumeBytes = stats.limitedVolumeBytes,
            compressedBytesRead = stats.compressedBytesRead,
            decompressedBytesRead = stats.decompressedBytes,
            status = status,
            rejectionReason = rejectionReason
        )
    }

    private fun commitCandidates(candidates: List<StagedSample>, stats: MutableStats): ImportStatus {
        val existingFiles = destinationDirectory.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".wav", ignoreCase = true) }
            .orEmpty()
        var existingWavBytes = existingFiles.sumOf { it.length() }
        val knownFilesByHash = existingFiles.mapNotNull { file ->
            try {
                sha256(file) to file
            } catch (_: IOException) {
                null
            }
        }.toMap().toMutableMap()
        var importedBytes = 0L
        var commitStatus = ImportStatus.COMPLETE

        for ((index, candidate) in candidates.withIndex()) {
            val duplicateFile = knownFilesByHash[candidate.sha256]
            if (duplicateFile != null) {
                stats.duplicateCount++
                stats.duplicateFiles.add(
                    ImportedSample(duplicateFile, duplicateFile.name, candidate.sha256, candidate.archivePath)
                )
                continue
            }
            val pendingNewBytes = candidate.sizeBytes + candidates.drop(index + 1)
                .filter { it.sha256 !in knownFilesByHash }
                .distinctBy { it.sha256 }
                .sumOf { it.sizeBytes }
            val allowance = (maxTotalWavBytes - existingWavBytes).coerceAtLeast(0L)
            if (candidate.sizeBytes > allowance) {
                stats.limitedVolumeBytes += pendingNewBytes
                commitStatus = ImportStatus.TOTAL_WAV_LIMIT_REACHED
                break
            }

            val available = try {
                freeSpaceProvider(destinationDirectory)
            } catch (_: RuntimeException) {
                0L
            }
            if (available < minimumFreeSpaceBytes || candidate.sizeBytes > available - minimumFreeSpaceBytes) {
                stats.limitedVolumeBytes += pendingNewBytes
                commitStatus = ImportStatus.INSUFFICIENT_SPACE
                break
            }

            var finalFile: File? = null
            try {
                while (true) {
                    val targetFile = collisionSafeTarget(candidate)
                    if (targetFile.createNewFile()) {
                        finalFile = targetFile
                        break
                    }
                }
                copyFile(candidate.stagedFile, checkNotNull(finalFile))
                if (!candidate.stagedFile.delete()) candidate.stagedFile.deleteOnExit()
            } catch (_: IOException) {
                finalFile?.delete()
                stats.limitedVolumeBytes += pendingNewBytes
                commitStatus = ImportStatus.IO_ERROR
                break
            }
            val committedFile = checkNotNull(finalFile)
            stats.importedFiles.add(
                ImportedSample(committedFile, committedFile.name, candidate.sha256, candidate.archivePath)
            )
            knownFilesByHash[candidate.sha256] = committedFile
            importedBytes += candidate.sizeBytes
            existingWavBytes += candidate.sizeBytes
        }
        // Kept explicit to make the per-invocation growth accounting auditable.
        @Suppress("UNUSED_VARIABLE")
        val committedGrowthBytes = importedBytes
        return commitStatus
    }

    private fun collisionSafeTarget(sample: StagedSample): File {
        val basename = sample.displayName.removeSuffix(".wav")
        var prefixLength = 12
        while (prefixLength <= sample.sha256.length) {
            val candidate = File(destinationDirectory, "${basename}_${sample.sha256.take(prefixLength)}.wav")
            if (!candidate.exists()) return candidate
            prefixLength += 4
        }
        var suffix = 2
        while (true) {
            val candidate = File(destinationDirectory, "${basename}_${sample.sha256}_$suffix.wav")
            if (!candidate.exists()) return candidate
            suffix++
        }
    }

    private fun createStagingDirectory(destination: File): File {
        val directory = kotlin.io.path.createTempDirectory(destination.toPath(), ".sample-pack-import-").toFile()
        if (!directory.isDirectory) throw IOException("Could not create staging directory")
        return directory
    }

    private fun basename(entryName: String): String =
        entryName.substringAfterLast('/').substringAfterLast('\\')

    private fun sanitizedWavName(rawBasename: String): String {
        val rawStem = rawBasename.substring(0, rawBasename.length - 4)
        val safeStem = rawStem.trim()
            .replace(Regex("[^A-Za-z0-9_-]+"), "_")
            .trim('_', '-')
            .take(MAX_DISPLAY_STEM_LENGTH)
            .ifEmpty { "sample" }
        return "$safeStem.wav"
    }

    private fun drainEntry(input: InputStream) {
        val buffer = ByteArray(COPY_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) return
            if (count == 0) {
                if (input.read() < 0) return
            }
        }
    }

    private fun copyEntryBounded(input: InputStream, outputFile: File, maximumBytes: Long): CopyResult {
        var countTotal = 0L
        var exceeded = false
        BufferedOutputStream(FileOutputStream(outputFile)).use { output ->
            val buffer = ByteArray(COPY_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) {
                    val single = input.read()
                    if (single < 0) break
                    countTotal++
                    if (countTotal <= maximumBytes) {
                        ensureFreeSpaceForStaging(1L)
                        output.write(single)
                    } else exceeded = true
                } else {
                    countTotal += count
                    val permitted = (maximumBytes - (countTotal - count)).coerceAtLeast(0L).coerceAtMost(count.toLong()).toInt()
                    if (permitted > 0) {
                        ensureFreeSpaceForStaging(permitted.toLong())
                        output.write(buffer, 0, permitted)
                    }
                    if (permitted < count) exceeded = true
                }
            }
        }
        return CopyResult(countTotal, exceeded)
    }

    private fun ensureFreeSpaceForStaging(bytesToWrite: Long) {
        val available = try {
            freeSpaceProvider(destinationDirectory)
        } catch (_: RuntimeException) {
            0L
        }
        if (available < minimumFreeSpaceBytes || bytesToWrite > available - minimumFreeSpaceBytes) {
            throw InsufficientFreeSpaceException()
        }
    }

    private fun copyFile(source: File, target: File) {
        FileInputStream(source).use { input ->
            BufferedOutputStream(FileOutputStream(target)).use { output ->
                val buffer = ByteArray(COPY_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
            }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(COPY_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun deleteTree(file: File) {
        if (file.isDirectory) file.listFiles()?.forEach(::deleteTree)
        file.delete()
    }

    private fun isZipSignature(bytes: ByteArray, length: Int): Boolean =
        length == 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() &&
            ((bytes[2] == 3.toByte() && bytes[3] == 4.toByte()) ||
                (bytes[2] == 5.toByte() && bytes[3] == 6.toByte()) ||
                (bytes[2] == 7.toByte() && bytes[3] == 8.toByte()))

    private data class StagedSample(
        val stagedFile: File,
        val displayName: String,
        val sha256: String,
        val archivePath: String,
        val sizeBytes: Long
    )

    private data class CopyResult(val bytesRead: Long, val exceededLimit: Boolean)

    private class TotalDecompressedSizeLimitException : IOException("ZIP exceeds configured total decompressed size limit")
    private class InsufficientFreeSpaceException : IOException("Not enough free storage for safe ZIP staging")

    private class TotalDecompressedBudgetInputStream(
        input: InputStream,
        private val stats: MutableStats,
        private val limit: Long
    ) : java.io.FilterInputStream(input) {
        override fun read(): Int {
            val value = `in`.read()
            if (value >= 0) increment(1)
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            val remaining = (limit - stats.decompressedBytes).coerceAtLeast(0L)
            val boundedLength = if (remaining >= length.toLong()) length else (remaining + 1L).toInt()
            val count = `in`.read(buffer, offset, boundedLength)
            if (count > 0) increment(count.toLong())
            return count
        }

        private fun increment(count: Long) {
            val remaining = (limit - stats.decompressedBytes).coerceAtLeast(0L)
            if (count > remaining) {
                stats.decompressedBytes = if (limit == Long.MAX_VALUE) Long.MAX_VALUE else limit + 1L
                throw TotalDecompressedSizeLimitException()
            }
            stats.decompressedBytes += count
        }
    }

    private class MutableStats {
        val importedFiles = mutableListOf<ImportedSample>()
        val duplicateFiles = mutableListOf<ImportedSample>()
        var skippedNonWavCount = 0
        var invalidWavCount = 0
        var oversizedWavCount = 0
        var duplicateCount = 0
        var limitedVolumeBytes = 0L
        var compressedBytesRead = 0L
        var decompressedBytes = 0L
    }

    private class CompressedSizeLimitException : IOException("Compressed ZIP exceeds configured size limit")

    private class LimitedCountingInputStream(
        input: InputStream,
        private val limit: Long
    ) : java.io.FilterInputStream(input) {
        var bytesRead = 0L
            private set

        override fun read(): Int {
            val value = `in`.read()
            if (value >= 0) increment(1)
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            val allowed = (limit - bytesRead + 1L).coerceAtLeast(1L).coerceAtMost(length.toLong()).toInt()
            val count = `in`.read(buffer, offset, allowed)
            if (count > 0) increment(count.toLong())
            return count
        }

        private fun increment(count: Long) {
            bytesRead += count
            if (bytesRead > limit) throw CompressedSizeLimitException()
        }
    }

    companion object {
        const val DEFAULT_MAX_COMPRESSED_BYTES: Long = 512L * 1024 * 1024
        const val DEFAULT_MAX_ENTRIES: Int = 3_000
        const val DEFAULT_MAX_WAV_BYTES: Long = 32L * 1024 * 1024
        const val DEFAULT_MAX_TOTAL_WAV_BYTES: Long = 2_684_354_560L
        const val DEFAULT_MAX_TOTAL_DECOMPRESSED_BYTES: Long = 3L * 1024 * 1024 * 1024
        const val DEFAULT_MINIMUM_FREE_SPACE_BYTES: Long = 128L * 1024 * 1024
        const val DEFAULT_STALE_STAGING_AGE_MILLIS: Long = 2L * 60 * 60 * 1000
        private const val STAGING_DIRECTORY_PREFIX = ".sample-pack-import-"
        private const val COPY_BUFFER_SIZE = 16 * 1024
        private const val MAX_DISPLAY_STEM_LENGTH = 64

        /** Removes only old importer-owned staging directories, never recent imports. */
        fun cleanupStaleStaging(
            destinationDirectory: File,
            nowMillis: Long = System.currentTimeMillis(),
            maxAgeMillis: Long = DEFAULT_STALE_STAGING_AGE_MILLIS
        ): Int {
            require(maxAgeMillis >= 0L) { "maxAgeMillis must not be negative" }
            val cutoff = nowMillis - maxAgeMillis
            val stale = destinationDirectory.listFiles()
                ?.filter {
                    it.isDirectory && it.name.startsWith(STAGING_DIRECTORY_PREFIX) && it.lastModified() <= cutoff
                }
                .orEmpty()
            fun deleteRecursively(file: File) {
                if (file.isDirectory) file.listFiles()?.forEach(::deleteRecursively)
                file.delete()
            }
            stale.forEach(::deleteRecursively)
            return stale.count { !it.exists() }
        }
    }
}

data class ImportedSample(
    val file: File,
    val name: String,
    val sha256: String,
    /** Sanitized source path for categorization only; never used as a destination path. */
    val archivePath: String = name
)

data class ImportResult(
    val archiveDisplayName: String,
    val importedFiles: List<ImportedSample>,
    val duplicateFiles: List<ImportedSample> = emptyList(),
    val skippedNonWavCount: Int,
    val invalidWavCount: Int,
    val oversizedWavCount: Int,
    val duplicateCount: Int,
    val limitedVolumeBytes: Long,
    val compressedBytesRead: Long,
    val decompressedBytesRead: Long,
    val status: ImportStatus,
    val rejectionReason: String?
)

enum class ImportStatus {
    COMPLETE,
    REJECTED,
    INVALID_ZIP,
    COMPRESSED_SIZE_LIMIT,
    TOTAL_DECOMPRESSED_SIZE_LIMIT,
    MAX_ENTRIES_REACHED,
    TOTAL_WAV_LIMIT_REACHED,
    INSUFFICIENT_SPACE,
    IO_ERROR
}

private fun sanitizedArchivePath(path: String): String = path
    .replace('\\', '/')
    .split('/')
    .filter { it.isNotBlank() && it != "." && it != ".." }
    .joinToString("/") { segment ->
        segment.map { character -> if (character.isISOControl()) '_' else character }.joinToString("")
    }
    .take(512)
