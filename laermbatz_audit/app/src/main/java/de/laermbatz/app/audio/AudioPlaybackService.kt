package de.laermbatz.app.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import de.laermbatz.app.MainActivity
import de.laermbatz.app.R

class AudioPlaybackService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var audioManager: AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false
    private var streamStarted = false
    private var stopping = false
    private var pendingPlaybackStop = false
    private var recorder: LocalMasterMixRecorder? = null

    private val stopRunnable = Runnable { if (stopping) finishStop() }
    private val streamWatchdog = object : Runnable {
        override fun run() {
            if (!streamStarted || stopping) return
            if (!AudioEngine.isStreamRunning()) {
                streamStarted = false
                recorder?.stop()
                if (hasAudioFocus && AudioEngine.start()) {
                    streamStarted = true
                    publishState(STATE_PLAYING, "Audiostream nach Unterbrechung wiederhergestellt.")
                } else {
                    publishState(STATE_ERROR, "Audiostream ist ausgefallen. Mixer bitte erneut starten.")
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    abandonFocus()
                    stopSelf()
                    return
                }
            }
            handler.postDelayed(this, STREAM_WATCHDOG_MILLIS)
        }
    }
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                if (!stopping && !streamStarted) resumeAfterFocusGain()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                hasAudioFocus = false
                if (streamStarted) {
                    AudioEngine.stop()
                    streamStarted = false
                }
                recorder?.stop()
                publishState(STATE_PAUSED, "Wiedergabe und Aufnahme wegen Audio-Fokus pausiert.")
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                hasAudioFocus = false
                stopPlayback("Wiedergabe wegen dauerhaftem Audio-Fokusverlust beendet.")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopPlayback("Wiedergabe wird weich ausgeblendet …")
            ACTION_RECORD_START -> startRecording()
            ACTION_RECORD_STOP -> stopRecording()
            else -> startPlayback(startId)
        }
        return START_NOT_STICKY
    }

    private fun startPlayback(startId: Int) {
        handler.removeCallbacks(stopRunnable)
        stopping = false
        startForeground(NOTIFICATION_ID, buildNotification())
        if (streamStarted && AudioEngine.isStreamRunning()) {
            publishState(STATE_PLAYING, "Audio-Mixer läuft.")
            scheduleStreamWatchdog()
            return
        }
        streamStarted = false
        if (AudioEngine.loadedTrackCount() == 0) {
            publishState(STATE_ERROR, "Importiere zuerst mindestens eine WAV-Spur.")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelfResult(startId)
            return
        }
        if (!requestFocus()) {
            publishState(STATE_ERROR, "Audio-Fokus konnte nicht angefordert werden.")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelfResult(startId)
            return
        }
        if (!AudioEngine.start()) {
            streamStarted = false
            abandonFocus()
            publishState(STATE_ERROR, "Oboe-Audiostream konnte nicht gestartet werden.")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelfResult(startId)
            return
        }
        streamStarted = true
        publishState(STATE_PLAYING, "Autopilot-Mixer läuft.")
        scheduleStreamWatchdog()
    }

    private fun resumeAfterFocusGain() {
        if (!AudioEngine.start()) {
            streamStarted = false
            recorder?.stop()
            abandonFocus()
            publishState(STATE_ERROR, "Oboe-Audiostream konnte nach Fokusgewinn nicht fortgesetzt werden.")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        streamStarted = true
        publishState(STATE_PLAYING, "Audio-Fokus zurück – Mixer läuft.")
        scheduleStreamWatchdog()
    }

    private fun startRecording() {
        if (stopping) {
            publishRecording(false, 0, "Mixer wird gerade gestoppt; Aufnahme nicht gestartet.")
            return
        }
        if (!ensurePlaybackStream()) {
            publishRecording(false, 0, "Kein aktiver Stereo-Audiostream. Mixer bitte stoppen und erneut starten.")
            return
        }
        if (recorder?.isActive == true) return
        val newRecorder = LocalMasterMixRecorder(
            this,
            onProgress = { seconds ->
                handler.post { publishRecording(true, seconds, "Mastermix wird lokal aufgenommen.") }
            },
            onFinished = { success, message ->
                handler.post {
                    recorder = null
                    publishRecording(false, 0, message)
                    if (pendingPlaybackStop) finishStop()
                    else updateForegroundNotification()
                }
            }
        )
        recorder = newRecorder
        if (!newRecorder.start()) {
            recorder = null
            publishRecording(false, 0, "Aufnahme konnte nicht gestartet werden.")
        } else {
            publishRecording(true, 0, "AAC-Aufnahme wird vorbereitet …")
        }
    }

    private fun stopRecording() {
        val activeRecorder = recorder
        if (activeRecorder == null) publishRecording(false, 0, "Keine aktive Aufnahme.")
        else {
            activeRecorder.stop()
            publishRecording(true, currentRecordingSeconds, "Aufnahme wird finalisiert …")
        }
    }

    private fun stopPlayback(message: String = "Wiedergabe wird weich ausgeblendet …") {
        if (stopping) return
        stopping = true
        handler.removeCallbacks(streamWatchdog)
        handler.removeCallbacks(stopRunnable)
        if (streamStarted) {
            AudioEngine.stop()
            streamStarted = false
        }
        recorder?.stop()
        publishState(STATE_STOPPING, message)
        handler.postDelayed(stopRunnable, RAMP_MILLIS)
    }

    private fun finishStop() {
        handler.removeCallbacks(streamWatchdog)
        if (recorder?.isActive == true) {
            pendingPlaybackStop = true
            recorder?.stop()
            publishState(STATE_STOPPING, "Aufnahme wird abgeschlossen, bevor der Mixer freigegeben wird …")
            return
        }
        pendingPlaybackStop = false
        AudioEngine.release()
        streamStarted = false
        abandonFocus()
        publishState(STATE_STOPPED, "Wiedergabe gestoppt.")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun requestFocus(): Boolean {
        if (hasAudioFocus) return true
        val request = focusRequest ?: run {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(focusListener, handler)
                .setWillPauseWhenDucked(true)
                .build()
        }
        val granted = audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (granted) {
            focusRequest = request
            hasAudioFocus = true
        } else hasAudioFocus = false
        return granted
    }

    private fun abandonFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
        hasAudioFocus = false
    }

    private fun ensurePlaybackStream(): Boolean {
        if (AudioEngine.isStreamRunning()) {
            streamStarted = true
            scheduleStreamWatchdog()
            return true
        }
        streamStarted = false
        if (!requestFocus() || !AudioEngine.start()) return false
        streamStarted = true
        publishState(STATE_PLAYING, "Audiostream für Aufnahme neu gestartet.")
        scheduleStreamWatchdog()
        return true
    }

    private fun scheduleStreamWatchdog() {
        handler.removeCallbacks(streamWatchdog)
        if (streamStarted && !stopping) handler.postDelayed(streamWatchdog, STREAM_WATCHDOG_MILLIS)
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopAction = PendingIntent.getService(
            this, 2, Intent(this, AudioPlaybackService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("LÄRMBATZ – Audio-Mixer")
            .setContentText(if (currentRecording) "Aufnahme ${currentRecordingSeconds}s / 3600s" else "Audio läuft im Vordergrund.")
            .setContentIntent(openApp)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(android.R.drawable.ic_media_pause, "Stoppen", stopAction)
            .build()
    }

    private fun updateForegroundNotification() {
        if (streamStarted || currentRecording) {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "LÄRMBATZ Wiedergabe", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun publishState(state: String, message: String) {
        currentState = state
        currentMessage = message
        publishBroadcast()
    }

    private fun publishRecording(active: Boolean, seconds: Int, message: String) {
        currentRecording = active
        currentRecordingSeconds = seconds.coerceIn(0, MAX_RECORDING_SECONDS)
        currentRecordingMessage = message
        publishBroadcast()
        updateForegroundNotification()
    }

    private fun publishBroadcast() {
        sendBroadcast(Intent(ACTION_STATE_CHANGED).setPackage(packageName)
            .putExtra(EXTRA_STATE, currentState)
            .putExtra(EXTRA_MESSAGE, currentMessage)
            .putExtra(EXTRA_RECORDING, currentRecording)
            .putExtra(EXTRA_RECORDING_SECONDS, currentRecordingSeconds)
            .putExtra(EXTRA_RECORDING_MESSAGE, currentRecordingMessage))
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        recorder?.let {
            it.stop()
            runCatching { it.awaitFinish(10_000L) }
        }
        AudioEngine.stop()
        AudioEngine.release()
        streamStarted = false
        if (::audioManager.isInitialized) abandonFocus()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "de.laermbatz.app.audio.STOP"
        const val ACTION_RECORD_START = "de.laermbatz.app.audio.RECORD_START"
        const val ACTION_RECORD_STOP = "de.laermbatz.app.audio.RECORD_STOP"
        const val ACTION_STATE_CHANGED = "de.laermbatz.app.audio.STATE_CHANGED"
        const val EXTRA_STATE = "state"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_RECORDING = "recording"
        const val EXTRA_RECORDING_SECONDS = "recording_seconds"
        const val EXTRA_RECORDING_MESSAGE = "recording_message"
        const val STATE_PLAYING = "playing"
        const val STATE_PAUSED = "paused"
        const val STATE_STOPPING = "stopping"
        const val STATE_STOPPED = "stopped"
        const val STATE_ERROR = "error"
        const val MAX_RECORDING_SECONDS = LocalMasterMixRecorder.MAX_DURATION_SECONDS

        @Volatile var currentState: String = STATE_STOPPED
            private set
        @Volatile var currentMessage: String = "Bereit."
            private set
        @Volatile var currentRecording: Boolean = false
            private set
        @Volatile var currentRecordingSeconds: Int = 0
            private set
        @Volatile var currentRecordingMessage: String = "Aufnahme nicht aktiv."
            private set
        private const val CHANNEL_ID = "laermbatz_playback"
        private const val NOTIFICATION_ID = 1001
        private const val RAMP_MILLIS = 30L
        private const val STREAM_WATCHDOG_MILLIS = 750L
    }
}
