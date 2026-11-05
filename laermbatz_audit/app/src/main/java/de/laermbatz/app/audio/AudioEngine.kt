package de.laermbatz.app.audio

/** JNI-Fassade für den callback-sicheren nativen Sechs-Spur-Mixer. */
object AudioEngine {
    init {
        System.loadLibrary("laermbatz_audio")
    }

    fun start(): Boolean = nativeStart()
    fun stop() = nativeStop()
    fun release() = nativeRelease()
    fun isStreamRunning(): Boolean = nativeIsStreamRunning()
    fun lastOutputPeak(): Float = nativeGetLastOutputPeak()
    fun loadTrack(index: Int, wav: DecodedWav, bars: Int): Boolean =
        nativeLoadTrack(index, wav.samples, wav.sampleRate, wav.channels, bars)
    fun clearTrack(index: Int): Boolean = nativeClearTrack(index)
    fun setTrackEnabled(index: Int, enabled: Boolean) = nativeSetTrackEnabled(index, enabled)
    fun setTrackGain(index: Int, gain: Float) = nativeSetTrackGain(index, gain)
    fun setTrackEffects(
        index: Int,
        lowPassHz: Float,
        delayDivision: Int,
        delayMix: Float,
        delayFeedback: Float,
        reverbSend: Float
    ) = nativeSetTrackEffects(index, lowPassHz, delayDivision, delayMix, delayFeedback, reverbSend)
    fun setBpm(bpm: Float) = nativeSetBpm(bpm)
    fun setAutopilot(enabled: Boolean) = nativeSetAutopilot(enabled)
    fun setAutopilotIntensity(intensity: Float) = nativeSetAutopilotIntensity(intensity)
    fun autopilotBarPosition(): Float = nativeGetAutopilotBarPosition()
    fun autopilotPhaseIntensity(): Float = nativeGetAutopilotPhaseIntensity()
    fun loadedTrackCount(): Int = nativeGetLoadedTrackCount()
    fun startRecording(): Int = nativeStartRecording()
    fun stopRecording() = nativeStopRecording()
    fun readRecording(into: FloatArray): Int = nativeReadRecording(into)
    fun recordingDroppedFrames(): Long = nativeRecordingDroppedFrames()
    fun recordingSampleRate(): Int = nativeRecordingSampleRate()

    private external fun nativeStart(): Boolean
    private external fun nativeStop()
    private external fun nativeRelease()
    private external fun nativeIsStreamRunning(): Boolean
    private external fun nativeGetLastOutputPeak(): Float
    private external fun nativeLoadTrack(
        index: Int,
        samples: FloatArray,
        sampleRate: Int,
        channels: Int,
        bars: Int
    ): Boolean
    private external fun nativeClearTrack(index: Int): Boolean
    private external fun nativeSetTrackEnabled(index: Int, enabled: Boolean)
    private external fun nativeSetTrackGain(index: Int, gain: Float)
    private external fun nativeSetTrackEffects(
        index: Int,
        lowPassHz: Float,
        delayDivision: Int,
        delayMix: Float,
        delayFeedback: Float,
        reverbSend: Float
    )
    private external fun nativeSetBpm(bpm: Float)
    private external fun nativeSetAutopilot(enabled: Boolean)
    private external fun nativeSetAutopilotIntensity(intensity: Float)
    private external fun nativeGetAutopilotBarPosition(): Float
    private external fun nativeGetAutopilotPhaseIntensity(): Float
    private external fun nativeGetLoadedTrackCount(): Int
    private external fun nativeStartRecording(): Int
    private external fun nativeStopRecording()
    private external fun nativeReadRecording(into: FloatArray): Int
    private external fun nativeRecordingDroppedFrames(): Long
    private external fun nativeRecordingSampleRate(): Int
}
