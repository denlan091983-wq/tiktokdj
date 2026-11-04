package de.laermbatz.app.audio

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WavDecoderTest {
    @Test
    fun decodesPcm16Mono() {
        val wav = wav(fmtChunk(channels = 1, bitsPerSample = 16), chunk("data", bytes(0x00, 0x80, 0xff, 0x7f)))

        val decoded = WavDecoder.decode(wav)

        assertEquals(44_100, decoded.sampleRate)
        assertEquals(1, decoded.channels)
        assertEquals(2, decoded.frameCount)
        assertArrayEquals(floatArrayOf(-1.0f, 32767.0f / 32768.0f), decoded.samples, 0.000001f)
    }

    @Test
    fun decodesPcm24StereoAndInterleavesChannels() {
        val data = bytes(
            0x00, 0x00, 0x80, // -8388608
            0xff, 0xff, 0x7f, // +8388607
            0x00, 0x00, 0x00, // 0
            0xff, 0xff, 0xff  // -1
        )
        val decoded = WavDecoder.decode(wav(fmtChunk(channels = 2, bitsPerSample = 24), chunk("data", data)))

        assertEquals(2, decoded.channels)
        assertEquals(2, decoded.frameCount)
        assertArrayEquals(
            floatArrayOf(-1.0f, 8_388_607.0f / 8_388_608.0f, 0.0f, -1.0f / 8_388_608.0f),
            decoded.samples,
            0.000001f
        )
    }

    @Test
    fun decodesPcm32() {
        val decoded = WavDecoder.decode(
            wav(fmtChunk(channels = 1, bitsPerSample = 32), chunk("data", bytes(0x00, 0x00, 0x00, 0x80, 0xff, 0xff, 0xff, 0x7f)))
        )

        assertArrayEquals(floatArrayOf(-1.0f, 2_147_483_647.0f / 2_147_483_648.0f), decoded.samples, 0.000001f)
    }

    @Test
    fun decodesIeeeFloat32() {
        val data = ByteArrayOutputStream().apply {
            writeLittleEndianInt(java.lang.Float.floatToIntBits(-0.25f))
            writeLittleEndianInt(java.lang.Float.floatToIntBits(0.75f))
        }.toByteArray()
        val decoded = WavDecoder.decode(
            wav(fmtChunk(channels = 1, bitsPerSample = 32, format = 3), chunk("data", data))
        )

        assertArrayEquals(floatArrayOf(-0.25f, 0.75f), decoded.samples, 0.0f)
    }

    @Test
    fun skipsUnknownOddSizedChunkAndAcceptsDataBeforeFormat() {
        val wav = wav(
            chunk("data", bytes(0x00, 0x00)),
            chunk("JUNK", bytes(0x11, 0x22, 0x33)),
            fmtChunk(channels = 1, bitsPerSample = 16)
        )

        val decoded = WavDecoder.decode(wav)

        assertEquals(1, decoded.frameCount)
        assertArrayEquals(floatArrayOf(0.0f), decoded.samples, 0.0f)
    }

    @Test
    fun acceptsPcmWaveFormatExtensible() {
        val wav = wav(extensibleFmtChunk(channels = 1, bitsPerSample = 16), chunk("data", bytes(0x00, 0x40)))

        val decoded = WavDecoder.decode(wav)

        assertEquals(0.5f, decoded.samples.single(), 0.000001f)
    }

    @Test
    fun rejectsTruncatedRiffAndMisalignedData() {
        val valid = wav(fmtChunk(channels = 1, bitsPerSample = 16), chunk("data", bytes(0x00, 0x00)))
        expectFailure("Truncated WAV") { WavDecoder.decode(valid.copyOf(valid.size - 1)) }

        val misaligned = wav(fmtChunk(channels = 1, bitsPerSample = 16), chunk("data", bytes(0x00)))
        expectFailure("data length") { WavDecoder.decode(misaligned) }
    }

    @Test
    fun rejectsUnsupportedFormatAndNonFiniteFloatSamples() {
        val unsupported = wav(fmtChunk(channels = 1, bitsPerSample = 16, format = 6), chunk("data", bytes(0x00, 0x00)))
        expectFailure("Unsupported WAV audio format") { WavDecoder.decode(unsupported) }

        val nonFinite = wav(
            fmtChunk(channels = 1, bitsPerSample = 32, format = 3),
            chunk("data", ByteArrayOutputStream().apply { writeLittleEndianInt(0x7f800000) }.toByteArray())
        )
        expectFailure("NaN or infinite") { WavDecoder.decode(nonFinite) }
    }

    @Test
    fun enforcesSampleInputAndChannelLimits() {
        val tooManySamples = wav(
            fmtChunk(channels = 1, bitsPerSample = 16),
            chunk("data", ByteArray(4_000_001 * 2))
        )
        expectFailure("interleaved samples") { WavDecoder.decode(tooManySamples) }

        val tooManyChannels = wav(
            fmtChunk(channels = 3, bitsPerSample = 16),
            chunk("data", ByteArray(6))
        )
        expectFailure("channel count") { WavDecoder.decode(tooManyChannels) }

        expectFailure("maximum size") { WavDecoder.decode(ByteArray(32 * 1024 * 1024 + 1)) }
    }

    @Test
    fun leavesInputStreamOpen() {
        val wav = wav(fmtChunk(channels = 1, bitsPerSample = 16), chunk("data", bytes(0x00, 0x00)))
        val stream = TrackingInputStream(wav)

        WavDecoder.decode(stream)

        assertFalse(stream.closed)
    }

    private fun expectFailure(messagePart: String, operation: () -> Unit) {
        try {
            operation()
            throw AssertionError("Expected failure containing '$messagePart'")
        } catch (exception: IllegalArgumentException) {
            assertTrue("Unexpected error message: ${exception.message}", exception.message?.contains(messagePart, ignoreCase = true) == true)
        }
    }

    private fun fmtChunk(
        channels: Int,
        bitsPerSample: Int,
        sampleRate: Int = 44_100,
        format: Int = 1
    ): ByteArray {
        val bytesPerSample = bitsPerSample / 8
        val blockAlign = channels * bytesPerSample
        return chunk("fmt ", ByteArrayOutputStream().apply {
            writeLittleEndianShort(format)
            writeLittleEndianShort(channels)
            writeLittleEndianInt(sampleRate)
            writeLittleEndianInt(sampleRate * blockAlign)
            writeLittleEndianShort(blockAlign)
            writeLittleEndianShort(bitsPerSample)
        }.toByteArray())
    }

    private fun extensibleFmtChunk(channels: Int, bitsPerSample: Int, sampleRate: Int = 44_100): ByteArray {
        val blockAlign = channels * bitsPerSample / 8
        return chunk("fmt ", ByteArrayOutputStream().apply {
            writeLittleEndianShort(0xfffe)
            writeLittleEndianShort(channels)
            writeLittleEndianInt(sampleRate)
            writeLittleEndianInt(sampleRate * blockAlign)
            writeLittleEndianShort(blockAlign)
            writeLittleEndianShort(bitsPerSample)
            writeLittleEndianShort(22) // cbSize
            writeLittleEndianShort(bitsPerSample) // valid bits
            writeLittleEndianInt(0) // channel mask
            writeLittleEndianInt(1) // KSDATAFORMAT_SUBTYPE_PCM Data1
            write(byteArrayOf(0x00, 0x00, 0x10, 0x00, 0x80.toByte(), 0x00, 0x00, 0xaa.toByte(), 0x00, 0x38, 0x9b.toByte(), 0x71))
        }.toByteArray())
    }

    private fun chunk(id: String, content: ByteArray): ByteArray {
        require(id.length == 4)
        return ByteArrayOutputStream().apply {
            write(id.toByteArray(Charsets.US_ASCII))
            writeLittleEndianInt(content.size)
            write(content)
            if (content.size % 2 != 0) write(0)
        }.toByteArray()
    }

    private fun wav(vararg chunks: ByteArray): ByteArray {
        val body = ByteArrayOutputStream().apply {
            write("WAVE".toByteArray(Charsets.US_ASCII))
            chunks.forEach { write(it) }
        }.toByteArray()
        return ByteArrayOutputStream().apply {
            write("RIFF".toByteArray(Charsets.US_ASCII))
            writeLittleEndianInt(body.size)
            write(body)
        }.toByteArray()
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

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

    private class TrackingInputStream(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        var closed = false
            private set

        override fun close() {
            closed = true
            super.close()
        }
    }
}
