package de.laermbatz.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

/** Spielt eine lokale WAV-Datei unabhängig vom Mixer als Rohvorschau ab. */
class WavPreviewPlayer : AutoCloseable {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "laermbatz-wav-preview").apply { isDaemon = true }
    }
    private val generation = AtomicInteger(0)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun play(file: File, onFinished: (String?) -> Unit) {
        val token = generation.incrementAndGet()
        executor.execute {
            var audioTrack: AudioTrack? = null
            var errorMessage: String? = null
            try {
                val wav = file.inputStream().use { WavDecoder.decode(it) }
                if (token != generation.get()) return@execute
                require(wav.channels == 1 || wav.channels == 2) { "Vorschau unterstützt Mono- oder Stereo-WAV." }
                val channelMask = if (wav.channels == 1) {
                    AudioFormat.CHANNEL_OUT_MONO
                } else {
                    AudioFormat.CHANNEL_OUT_STEREO
                }
                val minBufferBytes = AudioTrack.getMinBufferSize(
                    wav.sampleRate,
                    channelMask,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                if (minBufferBytes <= 0) throw IOException("Android unterstützt dieses WAV-Audioformat nicht.")
                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(wav.sampleRate)
                            .setChannelMask(channelMask)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .build()
                    )
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(maxOf(minBufferBytes, PREVIEW_BUFFER_BYTES))
                    .build()
                if (audioTrack.state != AudioTrack.STATE_INITIALIZED) {
                    throw IOException("Android konnte den Vorschau-Audiostream nicht öffnen.")
                }
                if (token != generation.get()) return@execute
                audioTrack.play()

                val pcm = ShortArray(CHUNK_SAMPLES)
                var sourceOffset = 0
                while (sourceOffset < wav.samples.size && token == generation.get()) {
                    val count = minOf(pcm.size, wav.samples.size - sourceOffset)
                    for (i in 0 until count) {
                        val sample = wav.samples[sourceOffset + i].coerceIn(-1.0f, 1.0f)
                        pcm[i] = (sample * if (sample < 0f) 32768f else 32767f)
                            .roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    }
                    var written = 0
                    while (written < count && token == generation.get()) {
                        val result = audioTrack.write(pcm, written, count - written, AudioTrack.WRITE_BLOCKING)
                        if (result < 0) throw IOException("Android-Audiowiedergabe fehlgeschlagen (Code $result).")
                        if (result == 0) Thread.yield() else written += result
                    }
                    sourceOffset += written
                }
            } catch (error: Exception) {
                if (token == generation.get()) errorMessage = error.message ?: "WAV-Vorschau fehlgeschlagen."
            } finally {
                runCatching { audioTrack?.stop() }
                audioTrack?.release()
                if (token == generation.get()) {
                    val message = errorMessage
                    mainHandler.post { onFinished(message) }
                }
            }
        }
    }

    fun stop() {
        generation.incrementAndGet()
    }

    override fun close() {
        stop()
        executor.shutdownNow()
    }

    private companion object {
        const val CHUNK_SAMPLES = 4096
        const val PREVIEW_BUFFER_BYTES = 16 * 1024
    }
}
