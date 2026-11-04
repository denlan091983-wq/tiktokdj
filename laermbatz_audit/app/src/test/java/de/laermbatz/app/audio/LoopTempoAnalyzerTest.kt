package de.laermbatz.app.audio

import kotlin.math.exp
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoopTempoAnalyzerTest {
    @Test
    fun detects120BpmAndFitsFourBarsToTargetTempo() {
        val analysis = LoopTempoAnalyzer.analyze(clickLoop(sourceBpm = 120.0, bars = 4), targetBpm = 128f)

        assertEquals(120.0f, analysis.detectedBpm!!, 2.0f)
        assertEquals(4, analysis.bars)
        assertFalse(analysis.usedDurationFallback)
        assertTrue(analysis.tempoAmbiguous)
        assertEquals(7.5, analysis.targetDurationSeconds, 0.01)
        assertEquals(8.0 / 7.5, analysis.playbackRate, 0.02)
    }

    @Test
    fun infersBarsFromSourceTempoWhenTargetTempoIsDifferent() {
        val analysis = LoopTempoAnalyzer.analyze(clickLoop(sourceBpm = 100.0, bars = 4), targetBpm = 128f)

        assertEquals(100.0f, analysis.detectedBpm!!, 2.0f)
        assertEquals(4, analysis.bars)
        assertFalse(analysis.tempoAmbiguous)
        assertEquals(9.6 / 7.5, analysis.playbackRate, 0.03)
    }

    @Test
    fun fallsBackToDurationWhenNoRhythmicOnsetsExist() {
        val sampleRate = 8_000
        val frames = sampleRate * 4
        val silentWav = DecodedWav(sampleRate, 1, FloatArray(frames), frames)

        val analysis = LoopTempoAnalyzer.analyze(silentWav, targetBpm = 120f)

        assertNull(analysis.detectedBpm)
        assertTrue(analysis.usedDurationFallback)
        assertEquals(2, analysis.bars)
        assertEquals(4.0, analysis.targetDurationSeconds, 0.01)
        assertEquals(1.0, analysis.playbackRate, 0.01)
    }

    @Test
    fun marksVeryShortWavForVarispeedFallback() {
        val frames = 256
        val tinyWav = DecodedWav(8_000, 1, FloatArray(frames), frames)

        val analysis = LoopTempoAnalyzer.analyze(tinyWav, targetBpm = 120f)
        val highRateFrames = 1_000
        val highRateTinyWav = DecodedWav(192_000, 1, FloatArray(highRateFrames), highRateFrames)
        val highRateAnalysis = LoopTempoAnalyzer.analyze(highRateTinyWav, targetBpm = 120f)
        val roundedBoundaryFrames = 882
        val roundedBoundaryWav = DecodedWav(44_150, 1, FloatArray(roundedBoundaryFrames), roundedBoundaryFrames)
        val roundedBoundaryAnalysis = LoopTempoAnalyzer.analyze(roundedBoundaryWav, targetBpm = 120f)

        assertFalse(analysis.usesPitchPreservingStretch)
        assertFalse(highRateAnalysis.usesPitchPreservingStretch)
        assertFalse(roundedBoundaryAnalysis.usesPitchPreservingStretch)
    }

    private fun clickLoop(sourceBpm: Double, bars: Int): DecodedWav {
        val sampleRate = 8_000
        val duration = bars * 240.0 / sourceBpm
        val frameCount = (duration * sampleRate).roundToInt()
        val samples = FloatArray(frameCount)
        val beatFrames = (sampleRate * 60.0 / sourceBpm).roundToInt()
        val clickFrames = sampleRate / 100 // 10 ms decaying transient

        for (beat in 0 until bars * 4) {
            val start = beat * beatFrames
            for (offset in 0 until clickFrames) {
                val index = start + offset
                if (index >= samples.size) break
                samples[index] = (0.8 * exp(-offset.toDouble() / (sampleRate * 0.002))).toFloat()
            }
        }
        return DecodedWav(sampleRate, 1, samples, frameCount)
    }
}
