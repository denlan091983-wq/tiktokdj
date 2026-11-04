package de.laermbatz.app.audio

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Decodierte, unkomprimierte Audiodaten. [samples] enthält pro Frame die Kanäle
 * direkt hintereinander (z. B. L, R, L, R bei Stereo).
 */
data class DecodedWav(
    val sampleRate: Int,
    val channels: Int,
    val samples: FloatArray,
    val frameCount: Int
)

/**
 * Kleiner RIFF/WAVE-Decoder für PCM Integer (16/24/32 Bit) und IEEE Float (32 Bit).
 *
 * Die Eingabeströme werden vollständig, aber auf maximal 32 MiB begrenzt gelesen.
 * Diese Methode schließt den übergebenen [InputStream] nicht; das Schließen bleibt
 * Aufgabe des Aufrufers.
 */
object WavDecoder {
    private const val MAX_INPUT_BYTES = 32 * 1024 * 1024
    private const val MAX_INTERLEAVED_SAMPLES = 4_000_000
    private const val RIFF_HEADER_SIZE = 12L
    private const val CHUNK_HEADER_SIZE = 8L

    private const val FORMAT_PCM = 0x0001
    private const val FORMAT_IEEE_FLOAT = 0x0003
    private const val FORMAT_EXTENSIBLE = 0xfffe

    /** Liest und decodiert eine WAV-Datei, ohne den Stream zu schließen. */
    @Throws(IOException::class)
    fun decode(input: InputStream): DecodedWav {
        val bytes = readBounded(input)
        return decodeBytes(bytes)
    }

    /** Decodiert WAV-Daten im Speicher. */
    fun decode(bytes: ByteArray): DecodedWav {
        require(bytes.size <= MAX_INPUT_BYTES) {
            "WAV input exceeds the maximum size of $MAX_INPUT_BYTES bytes"
        }
        return decodeBytes(bytes)
    }

    private fun readBounded(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) {
                // Some InputStream implementations may return zero for a non-empty buffer.
                val singleByte = input.read()
                if (singleByte < 0) break
                if (output.size() >= MAX_INPUT_BYTES) {
                    throw IllegalArgumentException("WAV input exceeds the maximum size of $MAX_INPUT_BYTES bytes")
                }
                output.write(singleByte)
                continue
            }
            if (count > MAX_INPUT_BYTES - output.size()) {
                throw IllegalArgumentException("WAV input exceeds the maximum size of $MAX_INPUT_BYTES bytes")
            }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun decodeBytes(bytes: ByteArray): DecodedWav {
        require(bytes.size >= RIFF_HEADER_SIZE) { "Truncated WAV: expected a 12-byte RIFF/WAVE header" }
        requireAscii(bytes, 0, "RIFF", "Invalid WAV: missing RIFF signature")
        requireAscii(bytes, 8, "WAVE", "Invalid WAV: missing WAVE form type")

        val riffSize = uint32(bytes, 4)
        require(riffSize >= 4L) { "Invalid RIFF size: the WAVE form type is missing" }
        val riffEndLong = riffSize + 8L
        require(riffEndLong <= bytes.size.toLong()) {
            "Truncated WAV: RIFF declares $riffEndLong bytes, but only ${bytes.size} are available"
        }
        val riffEnd = riffEndLong.toInt()

        var position = RIFF_HEADER_SIZE.toInt()
        var format: AudioFormat? = null
        var dataOffset = -1
        var dataLength = -1
        while (position < riffEnd) {
            require(riffEnd - position >= CHUNK_HEADER_SIZE) {
                "Truncated WAV: incomplete chunk header at byte $position"
            }
            val chunkId = ascii(bytes, position, 4)
            val chunkLengthLong = uint32(bytes, position + 4)
            val chunkStartLong = position.toLong() + CHUNK_HEADER_SIZE
            val chunkEndLong = chunkStartLong + chunkLengthLong
            require(chunkEndLong <= riffEnd.toLong()) {
                "Truncated WAV: '$chunkId' chunk at byte $position declares $chunkLengthLong bytes, " +
                    "ending at $chunkEndLong beyond RIFF boundary $riffEnd"
            }
            val chunkStart = chunkStartLong.toInt()
            val chunkEnd = chunkEndLong.toInt()

            when (chunkId) {
                "fmt " -> {
                    require(format == null) { "Invalid WAV: duplicate fmt chunk" }
                    format = parseFormat(bytes, chunkStart, chunkLengthLong.toInt())
                }
                "data" -> {
                    require(dataOffset < 0) { "Invalid WAV: duplicate data chunk" }
                    dataOffset = chunkStart
                    dataLength = chunkLengthLong.toInt()
                }
                // Unknown chunks are deliberately ignored; their length and padding are still checked.
            }

            val paddedEndLong = chunkEndLong + (chunkLengthLong and 1L)
            require(paddedEndLong <= riffEnd.toLong()) {
                "Truncated WAV: missing odd-byte padding after '$chunkId' chunk"
            }
            position = paddedEndLong.toInt()
        }
        require(position == riffEnd) { "Invalid WAV: chunk layout does not end at the RIFF boundary" }
        val audioFormat = format ?: throw IllegalArgumentException("Invalid WAV: missing fmt chunk")
        require(dataOffset >= 0) { "Invalid WAV: missing data chunk" }
        return decodeSamples(bytes, dataOffset, dataLength, audioFormat)
    }

    private fun parseFormat(bytes: ByteArray, offset: Int, length: Int): AudioFormat {
        require(length >= 16) { "Invalid WAV fmt chunk: expected at least 16 bytes, found $length" }
        val encodedFormat = uint16(bytes, offset)
        val channels = uint16(bytes, offset + 2)
        val sampleRateLong = uint32(bytes, offset + 4)
        val byteRate = uint32(bytes, offset + 8)
        val blockAlign = uint16(bytes, offset + 12)
        val bitsPerSample = uint16(bytes, offset + 14)

        require(channels == 1 || channels == 2) {
            "Unsupported WAV channel count $channels; only mono or stereo is supported"
        }
        require(sampleRateLong in 8_000L..192_000L) {
            "Unsupported WAV sample rate $sampleRateLong Hz; expected 8000–192000 Hz"
        }

        var formatCode = encodedFormat
        var validBits = bitsPerSample
        if (encodedFormat == FORMAT_EXTENSIBLE) {
            require(length >= 40) {
                "Invalid WAVE_FORMAT_EXTENSIBLE fmt chunk: expected at least 40 bytes, found $length"
            }
            val extensionSize = uint16(bytes, offset + 16)
            require(extensionSize >= 22 && 18L + extensionSize <= length.toLong()) {
                "Invalid WAVE_FORMAT_EXTENSIBLE fmt chunk: cbSize must include the 22-byte extension"
            }
            validBits = uint16(bytes, offset + 18)
            formatCode = extensibleSubformat(bytes, offset + 24)
            require(formatCode == FORMAT_PCM || formatCode == FORMAT_IEEE_FLOAT) {
                "Unsupported WAVE_FORMAT_EXTENSIBLE subformat ${formatCode.toString(16)}"
            }
            require(validBits == bitsPerSample) {
                "Unsupported WAVE_FORMAT_EXTENSIBLE valid-bits value $validBits for $bitsPerSample-bit samples"
            }
        }

        val bytesPerSample = when (formatCode) {
            FORMAT_PCM -> {
                require(bitsPerSample == 16 || bitsPerSample == 24 || bitsPerSample == 32) {
                    "Unsupported PCM bit depth $bitsPerSample; supported depths are 16, 24, and 32 bits"
                }
                bitsPerSample / 8
            }
            FORMAT_IEEE_FLOAT -> {
                require(bitsPerSample == 32) {
                    "Unsupported IEEE-float bit depth $bitsPerSample; only 32-bit float is supported"
                }
                4
            }
            else -> throw IllegalArgumentException("Unsupported WAV audio format 0x${formatCode.toString(16)}")
        }
        val expectedBlockAlign = channels * bytesPerSample
        require(blockAlign == expectedBlockAlign) {
            "Invalid WAV block align $blockAlign; expected $expectedBlockAlign for $channels channel(s)"
        }
        val expectedByteRate = sampleRateLong * expectedBlockAlign
        require(byteRate == expectedByteRate) {
            "Invalid WAV byte rate $byteRate; expected $expectedByteRate"
        }
        return AudioFormat(formatCode, channels, sampleRateLong.toInt(), bitsPerSample, bytesPerSample, blockAlign)
    }

    private fun extensibleSubformat(bytes: ByteArray, offset: Int): Int {
        // KSDATAFORMAT_SUBTYPE_{PCM,IEEE_FLOAT}, stored as a little-endian GUID.
        val data1 = uint32(bytes, offset)
        val expectedTail = byteArrayOf(
            0x00, 0x00, 0x10, 0x00, 0x80.toByte(), 0x00,
            0x00, 0xaa.toByte(), 0x00, 0x38, 0x9b.toByte(), 0x71
        )
        for (index in expectedTail.indices) {
            require(bytes[offset + 4 + index] == expectedTail[index]) {
                "Unsupported WAVE_FORMAT_EXTENSIBLE subformat GUID"
            }
        }
        require(data1 == FORMAT_PCM.toLong() || data1 == FORMAT_IEEE_FLOAT.toLong()) {
            "Unsupported WAVE_FORMAT_EXTENSIBLE subformat GUID"
        }
        return data1.toInt()
    }

    private fun decodeSamples(
        bytes: ByteArray,
        offset: Int,
        length: Int,
        format: AudioFormat
    ): DecodedWav {
        require(length % format.blockAlign == 0) {
            "Invalid WAV data length $length: it is not a multiple of block align ${format.blockAlign}"
        }
        val frameCountLong = length.toLong() / format.blockAlign
        val sampleCountLong = frameCountLong * format.channels
        require(sampleCountLong <= MAX_INTERLEAVED_SAMPLES) {
            "WAV contains $sampleCountLong interleaved samples; maximum is $MAX_INTERLEAVED_SAMPLES"
        }
        val samples = FloatArray(sampleCountLong.toInt())
        var source = offset
        for (index in samples.indices) {
            val sample = when (format.audioFormat) {
                FORMAT_PCM -> when (format.bitsPerSample) {
                    16 -> (signed16(bytes, source) / 32768.0).toFloat().also { source += 2 }
                    24 -> (signed24(bytes, source) / 8_388_608.0).toFloat().also { source += 3 }
                    32 -> (signed32(bytes, source) / 2_147_483_648.0).toFloat().also { source += 4 }
                    else -> throw IllegalStateException("Unexpected PCM bit depth ${format.bitsPerSample}")
                }
                FORMAT_IEEE_FLOAT -> {
                    val value = java.lang.Float.intBitsToFloat(signed32(bytes, source))
                    source += 4
                    require(value.isFinite()) {
                        "Invalid WAV sample at interleaved index $index: IEEE float is NaN or infinite"
                    }
                    value
                }
                else -> throw IllegalStateException("Unexpected WAV format ${format.audioFormat}")
            }
            samples[index] = sample
        }
        return DecodedWav(format.sampleRate, format.channels, samples, frameCountLong.toInt())
    }

    private fun requireAscii(bytes: ByteArray, offset: Int, expected: String, message: String) {
        require(ascii(bytes, offset, expected.length) == expected) { message }
    }

    private fun ascii(bytes: ByteArray, offset: Int, length: Int): String =
        String(bytes, offset, length, Charsets.US_ASCII)

    private fun uint16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

    private fun uint32(bytes: ByteArray, offset: Int): Long =
        (bytes[offset].toLong() and 0xffL) or
            ((bytes[offset + 1].toLong() and 0xffL) shl 8) or
            ((bytes[offset + 2].toLong() and 0xffL) shl 16) or
            ((bytes[offset + 3].toLong() and 0xffL) shl 24)

    private fun signed16(bytes: ByteArray, offset: Int): Int {
        val value = uint16(bytes, offset)
        return if (value >= 0x8000) value - 0x1_0000 else value
    }

    private fun signed24(bytes: ByteArray, offset: Int): Int {
        val value = (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16)
        return if (value and 0x800000 != 0) value - 0x1000000 else value
    }

    private fun signed32(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)

    private data class AudioFormat(
        val audioFormat: Int,
        val channels: Int,
        val sampleRate: Int,
        val bitsPerSample: Int,
        val bytesPerSample: Int,
        val blockAlign: Int
    )
}
