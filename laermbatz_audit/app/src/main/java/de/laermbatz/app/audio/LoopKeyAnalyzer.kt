package de.laermbatz.app.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.ln1p
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

/** Ergebnis einer spektralen Tonartschätzung. */
data class LoopKeyAnalysis(
    val key: String?,
    val confidence: Float,
    val margin: Float,
    val tonality: Float,
    val ambiguous: Boolean
)

/** Schätzt die Tonart per FFT-Chroma und Krumhansl-Schmuckler-Profilen. */
object LoopKeyAnalyzer {
    private const val FFT_SIZE = 8192
    private const val HOP_SIZE = 4096
    private const val MIN_SAMPLE_RATE = 8_000
    private const val MAX_SAMPLE_RATE = 192_000
    private const val MAX_ANALYSIS_SECONDS = 30
    private const val ZERO_EPSILON = 1e-10

    private val keyNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val majorProfile = doubleArrayOf(6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88)
    private val minorProfile = doubleArrayOf(6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17)

    /** Analysiert mono oder interleaved Stereo-Samples; ungültige Metadaten/Samples werfen [IllegalArgumentException]. */
    fun analyze(wav: DecodedWav): LoopKeyAnalysis {
        require(wav.sampleRate in MIN_SAMPLE_RATE..MAX_SAMPLE_RATE) {
            "Unsupported sample rate ${wav.sampleRate}; expected $MIN_SAMPLE_RATE–$MAX_SAMPLE_RATE Hz"
        }
        require(wav.channels == 1 || wav.channels == 2) { "Unsupported channel count ${wav.channels}; expected 1 or 2" }
        require(wav.frameCount >= 0) { "Invalid negative frame count ${wav.frameCount}" }
        require(wav.frameCount.toLong() * wav.channels == wav.samples.size.toLong()) {
            "Invalid sample array length ${wav.samples.size} for ${wav.frameCount} frames and ${wav.channels} channels"
        }
        for (index in wav.samples.indices) {
            require(wav.samples[index].isFinite()) { "Invalid non-finite sample at interleaved index $index" }
        }
        if (wav.frameCount == 0) return emptyResult()

        val window = DoubleArray(FFT_SIZE) { index ->
            0.5 - 0.5 * cos(2.0 * PI * index / (FFT_SIZE - 1))
        }
        val real = DoubleArray(FFT_SIZE)
        val imaginary = DoubleArray(FFT_SIZE)
        val chroma = DoubleArray(12)
        val maxFrequency = min(5_000.0, wav.sampleRate / 2.0)
        val highestBin = min(FFT_SIZE / 2, (maxFrequency * FFT_SIZE / wav.sampleRate).toInt())
        val lowestBin = max(1, kotlin.math.ceil(55.0 * FFT_SIZE / wav.sampleRate).toInt())
        if (lowestBin > highestBin) return emptyResult()

        val analysisFrameCount = min(wav.frameCount, wav.sampleRate * MAX_ANALYSIS_SECONDS)
        val lastStart = max(0, analysisFrameCount - FFT_SIZE)
        var start = 0
        while (true) {
            java.util.Arrays.fill(real, 0.0)
            java.util.Arrays.fill(imaginary, 0.0)
            val frameLength = min(FFT_SIZE, analysisFrameCount - start)

            var mean = 0.0
            for (offset in 0 until frameLength) {
                val sampleIndex = (start + offset) * wav.channels
                mean += if (wav.channels == 1) {
                    wav.samples[sampleIndex].toDouble()
                } else {
                    (wav.samples[sampleIndex].toDouble() + wav.samples[sampleIndex + 1]) * 0.5
                }
            }
            mean /= frameLength
            for (offset in 0 until frameLength) {
                val sampleIndex = (start + offset) * wav.channels
                val mono = if (wav.channels == 1) {
                    wav.samples[sampleIndex].toDouble()
                } else {
                    (wav.samples[sampleIndex].toDouble() + wav.samples[sampleIndex + 1]) * 0.5
                }
                real[offset] = (mono - mean) * window[offset]
            }

            fft(real, imaginary)
            for (bin in lowestBin..highestBin) {
                val frequency = bin.toDouble() * wav.sampleRate / FFT_SIZE
                val midi = round(69.0 + 12.0 * ln(frequency / 440.0) / LN_2).toInt()
                val pitchClass = ((midi % 12) + 12) % 12
                val magnitude = sqrt(real[bin] * real[bin] + imaginary[bin] * imaginary[bin])
                chroma[pitchClass] += magnitude / sqrt(max(frequency, 1.0))
            }

            if (start == lastStart) break
            start = min(start + HOP_SIZE, lastStart)
        }

        var chromaSum = 0.0
        for (value in chroma) chromaSum += value
        if (chromaSum <= ZERO_EPSILON) return emptyResult()

        val observed = DoubleArray(12)
        var observedMean = 0.0
        for (index in 0 until 12) {
            observed[index] = ln1p(chroma[index])
            observedMean += observed[index]
        }
        observedMean /= 12.0
        var observedNormSquared = 0.0
        for (index in 0 until 12) {
            observed[index] -= observedMean
            observedNormSquared += observed[index] * observed[index]
        }
        val observedNorm = sqrt(observedNormSquared)
        if (observedNorm <= ZERO_EPSILON) return emptyResult()

        var bestScore = Double.NEGATIVE_INFINITY
        var secondScore = Double.NEGATIVE_INFINITY
        var bestName = "C major"
        for (scale in 0..1) {
            val profile = if (scale == 0) majorProfile else minorProfile
            var profileMean = 0.0
            for (value in profile) profileMean += value
            profileMean /= 12.0
            var profileNormSquared = 0.0
            for (value in profile) {
                val centered = value - profileMean
                profileNormSquared += centered * centered
            }
            val profileNorm = sqrt(profileNormSquared)
            for (tonic in 0 until 12) {
                var dot = 0.0
                for (pitchClass in 0 until 12) {
                    val profileIndex = (pitchClass - tonic + 12) % 12
                    dot += observed[pitchClass] * (profile[profileIndex] - profileMean)
                }
                val score = dot / (observedNorm * profileNorm)
                if (score > bestScore) {
                    secondScore = bestScore
                    bestScore = score
                    bestName = "${keyNames[tonic]} ${if (scale == 0) "major" else "minor"}"
                } else if (score > secondScore) {
                    secondScore = score
                }
            }
        }

        var entropy = 0.0
        for (value in chroma) {
            val probability = value / chromaSum
            if (probability > 0.0) entropy -= probability * ln(probability)
        }
        val tonality = (1.0 - entropy / LN_12).coerceIn(0.0, 1.0)
        val margin = max(0.0, bestScore - secondScore)
        val acceptedKey = bestName.takeIf { bestScore >= 0.12 && margin >= 0.008 && tonality >= 0.12 }
        return LoopKeyAnalysis(
            key = acceptedKey,
            confidence = max(0.0, bestScore).toFloat().rounded4(),
            margin = margin.toFloat().rounded4(),
            tonality = tonality.toFloat().rounded4(),
            ambiguous = acceptedKey != null && margin < 0.02
        )
    }

    /** Returns a shortest semitone shift to the target tonic, only when major/minor mode matches. */
    fun semitoneShift(sourceKey: String, targetKey: String): Int? {
        val source = KEY_PATTERN.matchEntire(sourceKey) ?: return null
        val target = KEY_PATTERN.matchEntire(targetKey) ?: return null
        if (source.groupValues[2] != target.groupValues[2]) return null
        val sourcePitch = keyNames.indexOf(source.groupValues[1])
        val targetPitch = keyNames.indexOf(target.groupValues[1])
        if (sourcePitch < 0 || targetPitch < 0) return null
        return ((targetPitch - sourcePitch + 6 + 12) % 12) - 6
    }

    /** In-place radix-2 Cooley–Tukey FFT. */
    private fun fft(real: DoubleArray, imaginary: DoubleArray) {
        val size = real.size
        var j = 0
        for (i in 1 until size) {
            var bit = size shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val realTemp = real[i]
                real[i] = real[j]
                real[j] = realTemp
                val imaginaryTemp = imaginary[i]
                imaginary[i] = imaginary[j]
                imaginary[j] = imaginaryTemp
            }
        }

        var length = 2
        while (length <= size) {
            val angle = -2.0 * PI / length
            val stepReal = cos(angle)
            val stepImaginary = sin(angle)
            val halfLength = length shr 1
            var block = 0
            while (block < size) {
                var twiddleReal = 1.0
                var twiddleImaginary = 0.0
                for (offset in 0 until halfLength) {
                    val even = block + offset
                    val odd = even + halfLength
                    val oddReal = real[odd] * twiddleReal - imaginary[odd] * twiddleImaginary
                    val oddImaginary = real[odd] * twiddleImaginary + imaginary[odd] * twiddleReal
                    real[odd] = real[even] - oddReal
                    imaginary[odd] = imaginary[even] - oddImaginary
                    real[even] += oddReal
                    imaginary[even] += oddImaginary
                    val nextTwiddleReal = twiddleReal * stepReal - twiddleImaginary * stepImaginary
                    twiddleImaginary = twiddleReal * stepImaginary + twiddleImaginary * stepReal
                    twiddleReal = nextTwiddleReal
                }
                block += length
            }
            length = length shl 1
        }
    }

    private fun emptyResult() = LoopKeyAnalysis(null, 0f, 0f, 0f, false)

    private fun Float.rounded4(): Float = (round(this * 10_000f) / 10_000f)

    private const val LN_2 = 0.6931471805599453
    private const val LN_12 = 2.4849066497880004
    private val KEY_PATTERN = Regex("^(C#|D#|F#|G#|A#|[CDEFGAB]) (major|minor)$")
}
