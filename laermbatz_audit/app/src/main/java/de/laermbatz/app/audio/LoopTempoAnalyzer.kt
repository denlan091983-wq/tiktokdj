package de.laermbatz.app.audio

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Ergebnis der Offline-Analyse und der Anpassung einer WAV-Datei an das globale Tempo. */
data class LoopTempoAnalysis(
    val detectedBpm: Float?,
    val bars: Int,
    val confidence: Float,
    val sourceDurationSeconds: Double,
    val targetDurationSeconds: Double,
    /** Zeitdehnungsfaktor: > 1 kürzere Zieldauer, < 1 längere Zieldauer. */
    val playbackRate: Double,
    val usedDurationFallback: Boolean,
    /** True, wenn Halb- oder Doppeltempo fast ebenso gut zur Onset-Hülle passt. */
    val tempoAmbiguous: Boolean,
    /** Sehr kurze WAVs haben nicht genug Material für WSOLA und nutzen Varispeed. */
    val usesPitchPreservingStretch: Boolean
)

/**
 * Best-effort Tempoanalyse für rhythmische WAV-Loops. Berechnet RMS-Onset-Stärke in 10-ms-Fenstern,
 * sucht per normierter Autokorrelation nach einem Beat zwischen 60 und 180 BPM und schätzt daraus
 * die ganzzahlige Taktzahl. Bei zu wenig periodischer Energie wird die Taktzahl aus der Dauer und
 * dem beim Import gesetzten Zieltempo geschätzt.
 */
object LoopTempoAnalyzer {
    private const val MIN_BPM = 60.0
    private const val MAX_BPM = 180.0
    private const val MAX_BARS = 64
    private const val MIN_CONFIDENCE = 0.18
    private const val TIE_TOLERANCE = 0.02

    fun analyze(wav: DecodedWav, targetBpm: Float): LoopTempoAnalysis {
        require(wav.sampleRate in 8_000..192_000) { "Unsupported sample rate ${wav.sampleRate}" }
        require(wav.channels == 1 || wav.channels == 2) { "Only mono/stereo WAV is supported" }
        require(wav.frameCount > 0 && wav.samples.size == wav.frameCount * wav.channels) {
            "Decoded WAV frame/sample counts are inconsistent"
        }
        require(targetBpm.isFinite() && targetBpm in MIN_BPM.toFloat()..MAX_BPM.toFloat()) {
            "Target BPM must be between 60 and 180"
        }

        val duration = wav.frameCount.toDouble() / wav.sampleRate
        val estimate = detectBpm(wav)
        val fallback = estimate == null
        val sourceBpm = estimate?.bpm
        val inferredBars = if (sourceBpm != null) {
            (duration * sourceBpm / 240.0).roundToInt()
        } else {
            (duration * targetBpm / 240.0).roundToInt()
        }
        val bars = inferredBars.coerceIn(1, MAX_BARS)
        val targetDuration = bars * 240.0 / targetBpm
        val playbackRate = duration / targetDuration

        return LoopTempoAnalysis(
            detectedBpm = sourceBpm?.toFloat(),
            bars = bars,
            confidence = estimate?.confidence?.toFloat() ?: 0.0f,
            sourceDurationSeconds = duration,
            targetDurationSeconds = targetDuration,
            playbackRate = playbackRate,
            usedDurationFallback = fallback,
            tempoAmbiguous = estimate?.ambiguous ?: false,
            usesPitchPreservingStretch = wav.frameCount >= maxOf(512, (wav.sampleRate + 49) / 50)
        )
    }

    private fun detectBpm(wav: DecodedWav): Estimate? {
        val framesPerWindow = max(1, wav.sampleRate / 100) // approximately 10 ms
        val windowCount = ceil(wav.frameCount.toDouble() / framesPerWindow).toInt()
        if (windowCount < 100) return null

        val rms = DoubleArray(windowCount)
        for (window in 0 until windowCount) {
            val startFrame = window * framesPerWindow
            val endFrame = min(wav.frameCount, startFrame + framesPerWindow)
            val sampleCount = (endFrame - startFrame) * wav.channels
            if (sampleCount <= 0) continue
            var squareSum = 0.0
            var sampleIndex = startFrame * wav.channels
            val endSampleIndex = endFrame * wav.channels
            while (sampleIndex < endSampleIndex) {
                val sample = wav.samples[sampleIndex].toDouble()
                squareSum += sample * sample
                sampleIndex++
            }
            rms[window] = sqrt(squareSum / sampleCount)
        }

        val novelty = DoubleArray(windowCount)
        var noveltySum = 0.0
        for (index in 1 until windowCount) {
            val onset = max(0.0, rms[index] - rms[index - 1])
            novelty[index] = onset
            noveltySum += onset
        }
        if (noveltySum <= 1e-10) return null

        val mean = noveltySum / windowCount
        var energy = 0.0
        for (index in novelty.indices) {
            novelty[index] = max(0.0, novelty[index] - mean)
            energy += novelty[index] * novelty[index]
        }
        if (energy <= 1e-10) return null

        val windowSeconds = framesPerWindow.toDouble() / wav.sampleRate
        val minimumLag = max(1, (60.0 / (MAX_BPM * windowSeconds)).roundToInt())
        val maximumLag = (60.0 / (MIN_BPM * windowSeconds)).roundToInt()
        val scores = DoubleArray(maximumLag + 1)
        var bestScore = 0.0
        var bestLag = -1

        for (lag in minimumLag..maximumLag) {
            // Require at least three beat intervals for a meaningful periodicity estimate.
            if ((windowCount - lag).toDouble() / lag < 3.0) continue
            var cross = 0.0
            var leftEnergy = 0.0
            var rightEnergy = 0.0
            for (index in lag until windowCount) {
                val left = novelty[index]
                val right = novelty[index - lag]
                cross += left * right
                leftEnergy += left * left
                rightEnergy += right * right
            }
            val denominator = sqrt(leftEnergy * rightEnergy)
            val score = if (denominator > 1e-12) (cross / denominator).coerceIn(0.0, 1.0) else 0.0
            scores[lag] = score
            if (score > bestScore) {
                bestScore = score
                bestLag = lag
            }
        }
        if (bestLag < 0 || bestScore < MIN_CONFIDENCE) return null

        // Beat/half-time ambiguity is common in loops. If candidates are almost equally periodic,
        // prefer the faster beat rather than silently interpreting every second beat as a bar beat.
        val selectedLag = (minimumLag..maximumLag)
            .filter { scores[it] >= bestScore * (1.0 - TIE_TOLERANCE) && scores[it] >= MIN_CONFIDENCE }
            .minOrNull() ?: bestLag
        val alternateLags = listOf(selectedLag * 2, (selectedLag / 2.0).roundToInt())
        val tempoAmbiguous = alternateLags.any { lag ->
            lag in minimumLag..maximumLag && scores[lag] >= bestScore * (1.0 - TIE_TOLERANCE)
        }
        val offset = if (selectedLag in 1 until maximumLag) {
            val left = scores[selectedLag - 1]
            val center = scores[selectedLag]
            val right = scores[selectedLag + 1]
            val denominator = left - 2.0 * center + right
            if (abs(denominator) > 1e-12) {
                (0.5 * (left - right) / denominator).coerceIn(-0.5, 0.5)
            } else 0.0
        } else 0.0
        val interpolatedLag = max(1e-6, selectedLag + offset)
        val bpm = 60.0 / (interpolatedLag * windowSeconds)
        if (bpm !in MIN_BPM..MAX_BPM) return null
        return Estimate(bpm, scores[selectedLag], tempoAmbiguous)
    }

    private data class Estimate(val bpm: Double, val confidence: Double, val ambiguous: Boolean)
}
