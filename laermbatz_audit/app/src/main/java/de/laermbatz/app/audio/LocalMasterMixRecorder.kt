package de.laermbatz.app.audio

import android.content.ContentValues
import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.os.SystemClock
import java.io.File
import java.io.IOException
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/** AAC recorder fed only from AudioEngine's final Oboe stereo master mix. */
internal class LocalMasterMixRecorder(
    context: Context,
    private val onProgress: (seconds: Int) -> Unit,
    private val onFinished: (success: Boolean, message: String) -> Unit
) {
    private val appContext = context.applicationContext
    private val stopRequested = AtomicBoolean(false)
    @Volatile private var worker: Thread? = null

    val isActive: Boolean get() = worker?.isAlive == true

    fun start(): Boolean {
        if (isActive) return false
        stopRequested.set(false)
        worker = Thread(::record, "laermbatz-aac-recorder").apply { start() }
        return true
    }

    fun stop() {
        stopRequested.set(true)
    }

    fun awaitFinish(timeoutMillis: Long) {
        worker?.takeIf { it !== Thread.currentThread() }?.join(timeoutMillis)
    }

    private fun record() {
        var uri: Uri? = null
        var legacyFile: File? = null
        var descriptor: ParcelFileDescriptor? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var encoderStarted = false
        var muxerStarted = false
        var outputTrack = -1
        var captureStarted = false
        var completed = false
        var failure: String? = null
        try {
            val created = createDestination()
            uri = created.first
            legacyFile = created.second
            descriptor = created.third
            muxer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaMuxer(descriptor!!.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            } else {
                MediaMuxer(legacyFile!!.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            }

            val rate = AudioEngine.recordingSampleRate().takeIf { it >= 8000 }
                ?: throw IOException("Der Oboe-Masterstream ist nicht verfügbar.")
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, 2).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, INPUT_FRAMES * 4)
                setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            }
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            encoderStarted = true

            val actualRate = AudioEngine.startRecording()
            if (actualRate <= 0) throw IOException("Aufnahme benötigt einen laufenden Stereo-Oboe-Mixer.")
            if (actualRate != rate) throw IOException("Die Abtastrate des Audiostreams hat sich geändert; bitte erneut starten.")
            captureStarted = true
            val captureStartMs = SystemClock.elapsedRealtime()
            val pcm = FloatArray(INPUT_FRAMES * 2)
            val info = MediaCodec.BufferInfo()
            var framesQueued = 0L
            var eosQueued = false
            var encoderEos = false
            var lastProgress = -1
            val maximumFrames = MAX_DURATION_SECONDS.toLong() * actualRate

            while (!encoderEos) {
                val elapsed = (SystemClock.elapsedRealtime() - captureStartMs).coerceAtLeast(0L)
                if (elapsed >= MAX_DURATION_MS) stopRequested.set(true)
                if (stopRequested.get() && captureStarted) {
                    AudioEngine.stopRecording()
                    captureStarted = false
                }
                if (AudioEngine.recordingDroppedFrames() > 0L) {
                    throw IOException("Audio-Puffer überlief; Aufnahme aus Sicherheitsgründen beendet.")
                }
                val inputIndex = if (!eosQueued) encoder.dequeueInputBuffer(DEQUEUE_TIMEOUT_US) else -1
                if (inputIndex >= 0) {
                    val input = encoder.getInputBuffer(inputIndex)
                        ?: throw IOException("AAC-Encoder lieferte keinen Eingabepuffer.")
                    input.clear()
                    input.order(ByteOrder.LITTLE_ENDIAN)
                    val capacityFrames = (input.remaining() / 4).coerceAtMost(INPUT_FRAMES)
                    if (capacityFrames < INPUT_FRAMES) throw IOException("AAC-Eingabepuffer ist unerwartet klein.")
                    if (framesQueued >= maximumFrames) {
                        encoder.queueInputBuffer(inputIndex, 0, 0, framesQueued * 1_000_000L / actualRate,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        eosQueued = true
                        continue
                    }
                    val frames = AudioEngine.readRecording(pcm)
                    val framesToQueue = minOf(frames.toLong(), maximumFrames - framesQueued).toInt()
                    if (framesToQueue > 0) {
                        // Avoid per-sample JNI or callback work; this conversion runs on the worker.
                        val availableFloats = framesToQueue * 2
                        for (i in 0 until availableFloats) {
                            val sample = pcm[i].coerceIn(-1.0f, 1.0f)
                            val value = if (sample < 0f) (sample * 32768f).toInt() else (sample * 32767f).toInt()
                            input.putShort(value.toShort())
                        }
                        val ptsUs = framesQueued * 1_000_000L / actualRate
                        encoder.queueInputBuffer(inputIndex, 0, availableFloats * 2, ptsUs, 0)
                        framesQueued += framesToQueue
                        if (framesQueued >= maximumFrames) stopRequested.set(true)
                    } else if (stopRequested.get()) {
                        if (captureStarted) {
                            AudioEngine.stopRecording()
                            captureStarted = false
                        }
                        encoder.queueInputBuffer(inputIndex, 0, 0, framesQueued * 1_000_000L / actualRate,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        eosQueued = true
                    } else {
                        // Return the unused input buffer and let the output side/queue refill.
                        encoder.queueInputBuffer(inputIndex, 0, 0, framesQueued * 1_000_000L / actualRate, 0)
                    }
                }

                var outputIndex = encoder.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US)
                while (outputIndex >= 0) {
                    val encoded = encoder.getOutputBuffer(outputIndex)
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                    if (info.size > 0) {
                        if (!muxerStarted) throw IOException("AAC-Ausgabeformat wurde vor den Audiodaten nicht bereitgestellt.")
                        encoded ?: throw IOException("AAC-Encoder lieferte keinen Ausgabepuffer.")
                        encoded.position(info.offset)
                        encoded.limit(info.offset + info.size)
                        muxer.writeSampleData(outputTrack, encoded, info)
                    }
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) encoderEos = true
                    encoder.releaseOutputBuffer(outputIndex, false)
                    outputIndex = encoder.dequeueOutputBuffer(info, 0)
                }
                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (muxerStarted) throw IOException("AAC-Encoder änderte das Format mehrfach.")
                    outputTrack = muxer.addTrack(encoder.outputFormat)
                    muxer.start()
                    muxerStarted = true
                }
                val seconds = (framesQueued / actualRate).toInt()
                if (seconds != lastProgress) {
                    lastProgress = seconds
                    onProgress(seconds)
                }
            }
            completed = framesQueued > 0 && muxerStarted
            if (!completed) throw IOException("Keine AAC-Audiodaten wurden aufgenommen.")
        } catch (error: Throwable) {
            failure = error.message ?: error.javaClass.simpleName
        } finally {
            if (captureStarted) runCatching { AudioEngine.stopRecording() }
            if (encoderStarted) runCatching { encoder?.stop() }.onFailure {
                if (failure == null) failure = "AAC-Encoder konnte nicht sauber gestoppt werden: ${it.message}"
            }
            runCatching { encoder?.release() }
            if (muxerStarted) runCatching { muxer?.stop() }.onFailure {
                if (failure == null) failure = "MP4-Container konnte nicht finalisiert werden: ${it.message}"
            }
            runCatching { muxer?.release() }
            runCatching { descriptor?.close() }
            if (completed && failure == null) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri != null) {
                        val values = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
                        appContext.contentResolver.update(uri, values, null, null)
                    } else {
                        legacyFile?.let {
                            MediaScannerConnection.scanFile(appContext, arrayOf(it.absolutePath), arrayOf("audio/mp4"), null)
                        }
                    }
                } catch (error: Throwable) {
                    failure = "Aufnahme erstellt, konnte aber nicht veröffentlicht werden: ${error.message}"
                }
            }
            if (!completed || failure != null) {
                uri?.let { runCatching { appContext.contentResolver.delete(it, null, null) } }
                legacyFile?.let { runCatching { it.delete() } }
            }
            if (failure == null) onFinished(true, "Aufnahme in Music/LÄRMBATZ gespeichert.")
            else onFinished(false, "Aufnahmefehler: $failure")
        }
    }

    private fun createDestination(): Triple<Uri?, File?, ParcelFileDescriptor?> {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val name = "Laermbatz_$stamp.m4a"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, name)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/LÄRMBATZ")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val uri = appContext.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("MediaStore konnte keine Audiodatei anlegen.")
            val pfd = appContext.contentResolver.openFileDescriptor(uri, "w") ?: run {
                appContext.contentResolver.delete(uri, null, null)
                throw IOException("MediaStore-Datei konnte nicht geöffnet werden.")
            }
            return Triple(uri, null, pfd)
        }
        val music = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val directory = File(music, "LÄRMBATZ")
        if (!directory.exists() && !directory.mkdirs()) throw IOException("Music/LÄRMBATZ konnte nicht angelegt werden.")
        return Triple(null, File(directory, name), null)
    }

    companion object {
        const val MAX_DURATION_SECONDS = 60 * 60
        private const val MAX_DURATION_MS = MAX_DURATION_SECONDS * 1000L
        private const val BIT_RATE = 192_000
        private const val INPUT_FRAMES = 512
        private const val DEQUEUE_TIMEOUT_US = 10_000L
    }
}
