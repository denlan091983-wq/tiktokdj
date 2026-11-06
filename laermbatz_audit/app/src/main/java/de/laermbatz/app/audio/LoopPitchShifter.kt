package de.laermbatz.app.audio

import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Simple offline pitch transposition by resampling with linear interpolation.
 * This is not a high-quality phase vocoder: it changes pitch and duration together
 * and can introduce the artifacts typical of basic linear interpolation.
 */
object LoopPitchShifter {
    private const val MIN_SEMITONES = -6
    private const val MAX_SEMITONES = 6
    // Leave headroom below the JVM's theoretical maximum array length.
    private const val MAX_ARRAY_SIZE = Int.MAX_VALUE - 8

    /** Returns [wav] unchanged for zero semitones; otherwise transposes by resampling. */
    fun shift(wav: DecodedWav, semitones: Int): DecodedWav {
        validate(wav)
        require(semitones in MIN_SEMITONES..MAX_SEMITONES) {
            "Pitch shift must be between $MIN_SEMITONES and $MAX_SEMITONES semitones"
        }
        if (semitones == 0) return wav

        val ratio = 2.0.pow(semitones / 12.0)
        val outputFramesDouble = wav.frameCount.toDouble() / ratio
        require(outputFramesDouble.isFinite() && outputFramesDouble >= 1.0) {
            "Calculated output frame count is invalid: $outputFramesDouble"
        }
        val maxOutputFrames = MAX_ARRAY_SIZE / wav.channels
        require(outputFramesDouble <= maxOutputFrames.toDouble()) {
            "Calculated output exceeds the maximum supported sample array size"
        }
        val outputFramesLong = outputFramesDouble.roundToLong()
        require(outputFramesLong in 1L..maxOutputFrames.toLong()) {
            "Calculated output frame count is outside the supported range"
        }
        val outputFrames = outputFramesLong.toInt()
        val outputSampleCountLong = outputFramesLong * wav.channels.toLong()
        require(outputSampleCountLong <= MAX_ARRAY_SIZE.toLong()) {
            "Calculated output exceeds the maximum supported sample array size"
        }
        val outputSamples = FloatArray(outputSampleCountLong.toInt())
        val lastInputFrame = wav.frameCount - 1

        for (outputFrame in 0 until outputFrames) {
            // Mapping output time back to input time yields the desired playback-rate change.
            val sourcePosition = (outputFrame * ratio).coerceIn(0.0, lastInputFrame.toDouble())
            val leftFrame = sourcePosition.toInt()
            val rightFrame = minOf(leftFrame + 1, lastInputFrame)
            val fraction = sourcePosition - leftFrame

            for (channel in 0 until wav.channels) {
                val left = wav.samples[leftFrame * wav.channels + channel].toDouble()
                val right = wav.samples[rightFrame * wav.channels + channel].toDouble()
                // Use Double intermediates so opposite, large finite Float values cannot overflow.
                outputSamples[outputFrame * wav.channels + channel] =
                    (left + (right - left) * fraction).toFloat()
            }
        }

        return DecodedWav(wav.sampleRate, wav.channels, outputSamples, outputFrames)
    }

    private fun validate(wav: DecodedWav) {
        require(wav.sampleRate > 0) { "Sample rate must be positive" }
        require(wav.channels == 1 || wav.channels == 2) { "Only mono and stereo audio are supported" }
        require(wav.samples.isNotEmpty()) { "Sample array must not be empty" }
        require(wav.frameCount > 0) { "Frame count must be positive" }
        val expectedSampleCount = wav.frameCount.toLong() * wav.channels
        require(expectedSampleCount == wav.samples.size.toLong()) {
            "Sample array size ${wav.samples.size} does not match frame count ${wav.frameCount} and channels ${wav.channels}"
        }
        for (index in wav.samples.indices) {
            require(wav.samples[index].isFinite()) { "Sample at index $index is NaN or infinite" }
        }
    }
}
