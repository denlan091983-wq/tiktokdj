package de.laermbatz.app.audio

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LoopPitchShifterTest {
    @Test
    fun zeroSemitonesReturnsTheOriginalWavUnchanged() {
        val wav = DecodedWav(44_100, 2, floatArrayOf(0.1f, -0.2f, 0.3f, -0.4f), 2)

        val shifted = LoopPitchShifter.shift(wav, 0)

        assertSame(wav, shifted)
    }

    @Test
    fun rejectsTwelveSemitonesOutsideTheSupportedRange() {
        val wav = sineWav(frequencyHz = 440.0, frameCount = 1_000)

        expectIllegalArgument { LoopPitchShifter.shift(wav, 12) }
        expectIllegalArgument { LoopPitchShifter.shift(wav, -12) }
    }

    @Test
    fun sixSemitonesRaisesMeasuredFrequencyByTheExpectedRatio() {
        val sampleRate = 8_000
        val inputFrequencyHz = 400.0
        val wav = sineWav(inputFrequencyHz, sampleRate * 2, sampleRate)

        val shifted = LoopPitchShifter.shift(wav, 6)
        val positiveCrossings = (1 until shifted.frameCount).count { frame ->
            shifted.samples[frame - 1] <= 0f && shifted.samples[frame] > 0f
        }
        val measuredFrequencyHz = positiveCrossings.toDouble() * sampleRate / shifted.frameCount
        val expectedRatio = 2.0.pow(6.0 / 12.0)

        assertEquals((wav.frameCount / expectedRatio), shifted.frameCount.toDouble(), 1.0)
        assertEquals(wav.sampleRate, shifted.sampleRate)
        assertEquals(wav.channels, shifted.channels)
        assertEquals(expectedRatio, measuredFrequencyHz / inputFrequencyHz, 0.02)
    }

    @Test
    fun interpolatesStereoChannelsIndependently() {
        val frames = 100
        val samples = FloatArray(frames * 2) { index ->
            if (index % 2 == 0) 0f else 1f
        }
        val wav = DecodedWav(8_000, 2, samples, frames)

        val shifted = LoopPitchShifter.shift(wav, 6)

        assertEquals(2, shifted.channels)
        for (frame in 0 until shifted.frameCount) {
            assertEquals(0f, shifted.samples[frame * 2], 0f)
            assertEquals(1f, shifted.samples[frame * 2 + 1], 0f)
        }
    }

    @Test
    fun rejectsInvalidWavMetadataAndSamples() {
        expectIllegalArgument { LoopPitchShifter.shift(DecodedWav(0, 1, floatArrayOf(0f), 1), 1) }
        expectIllegalArgument { LoopPitchShifter.shift(DecodedWav(8_000, 3, floatArrayOf(0f, 0f, 0f), 1), 1) }
        expectIllegalArgument { LoopPitchShifter.shift(DecodedWav(8_000, 1, FloatArray(0), 0), 1) }
        expectIllegalArgument { LoopPitchShifter.shift(DecodedWav(8_000, 1, floatArrayOf(Float.NaN), 1), 1) }
        expectIllegalArgument { LoopPitchShifter.shift(DecodedWav(8_000, 1, floatArrayOf(0f, 1f), 1), 1) }
    }

    private fun sineWav(
        frequencyHz: Double,
        frameCount: Int,
        sampleRate: Int = 8_000
    ): DecodedWav {
        val samples = FloatArray(frameCount) { frame ->
            sin(2.0 * PI * frequencyHz * frame / sampleRate).toFloat()
        }
        return DecodedWav(sampleRate, 1, samples, frameCount)
    }

    private fun expectIllegalArgument(block: () -> Unit) {
        var thrown = false
        try {
            block()
        } catch (_: IllegalArgumentException) {
            thrown = true
        }
        assertTrue("Expected IllegalArgumentException", thrown)
    }
}
