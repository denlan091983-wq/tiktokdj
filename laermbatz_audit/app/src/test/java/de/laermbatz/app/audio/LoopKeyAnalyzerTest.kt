package de.laermbatz.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

class LoopKeyAnalyzerTest {
    @Test
    fun identifiesCMajorChord() {
        val result = LoopKeyAnalyzer.analyze(chord(intArrayOf(60, 64, 67)))

        assertEquals("C major", result.key)
        assertTrue(result.confidence > 0f)
        assertTrue(result.tonality > 0f)
    }

    @Test
    fun identifiesAMinorChord() {
        val result = LoopKeyAnalyzer.analyze(chord(intArrayOf(57, 60, 64)))

        assertEquals("A minor", result.key)
        assertTrue(result.confidence > 0f)
        assertTrue(result.tonality > 0f)
    }

    @Test
    fun computesShortestSemitoneShiftsOnlyWithinTheSameMode() {
        assertEquals(2, LoopKeyAnalyzer.semitoneShift("C major", "D major"))
        assertEquals(-2, LoopKeyAnalyzer.semitoneShift("D minor", "C minor"))
        assertEquals(-6, LoopKeyAnalyzer.semitoneShift("C major", "F# major"))
        assertNull(LoopKeyAnalyzer.semitoneShift("C major", "A minor"))
        assertNull(LoopKeyAnalyzer.semitoneShift("Db major", "C major"))
    }

    @Test
    fun silenceProducesNoKey() {
        val result = LoopKeyAnalyzer.analyze(DecodedWav(22_050, 1, FloatArray(12_000), 12_000))

        assertNull(result.key)
        assertEquals(0f, result.confidence, 0f)
    }

    @Test
    fun broadbandNoiseIsNotReportedAsAConfidentKey() {
        val random = Random(1234)
        val samples = FloatArray(24_000) { random.nextFloat() * 2f - 1f }
        val result = LoopKeyAnalyzer.analyze(DecodedWav(22_050, 1, samples, samples.size))

        assertNull(result.key)
        assertTrue(result.tonality < 0.12f)
    }

    @Test
    fun rejectsInvalidSampleRateAndSampleData() {
        expectIllegalArgument {
            LoopKeyAnalyzer.analyze(DecodedWav(0, 1, FloatArray(0), 0))
        }
        expectIllegalArgument {
            LoopKeyAnalyzer.analyze(DecodedWav(22_050, 1, floatArrayOf(0f), 2))
        }
        expectIllegalArgument {
            LoopKeyAnalyzer.analyze(DecodedWav(22_050, 1, floatArrayOf(Float.NaN), 1))
        }
    }

    private fun chord(midiNotes: IntArray): DecodedWav {
        val sampleRate = 22_050
        val frameCount = sampleRate * 2
        val samples = FloatArray(frameCount)
        val frequencies = DoubleArray(midiNotes.size) { index ->
            440.0 * 2.0.pow((midiNotes[index] - 69) / 12.0)
        }
        for (frame in samples.indices) {
            val time = frame.toDouble() / sampleRate
            var value = 0.0
            for (frequency in frequencies) value += sin(2.0 * PI * frequency * time)
            samples[frame] = (value / frequencies.size).toFloat()
        }
        return DecodedWav(sampleRate, 1, samples, frameCount)
    }

    private fun expectIllegalArgument(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected validation failure.
        }
    }
}
