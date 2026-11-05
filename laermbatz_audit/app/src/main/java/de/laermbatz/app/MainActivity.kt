package de.laermbatz.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startForegroundService
import de.laermbatz.app.audio.AudioEngine
import de.laermbatz.app.audio.AudioPlaybackService
import de.laermbatz.app.audio.DecodedWav
import de.laermbatz.app.audio.LoopTempoAnalysis
import de.laermbatz.app.audio.LoopTempoAnalyzer
import de.laermbatz.app.audio.WavDecoder
import de.laermbatz.app.audio.WavPreviewPlayer
import de.laermbatz.app.events.TikTokLiveEventSource
import de.laermbatz.app.freesound.FreesoundApiClient
import de.laermbatz.app.freesound.FreesoundCredentials
import de.laermbatz.app.freesound.FreesoundCredentialsDialog
import de.laermbatz.app.freesound.FreesoundCredentialStore
import de.laermbatz.app.freesound.FreesoundAutoCategorizer
import de.laermbatz.app.freesound.FreesoundLibraryDialog
import de.laermbatz.app.freesound.FreesoundLibraryItem
import de.laermbatz.app.freesound.FreesoundLibraryStore
import de.laermbatz.app.freesound.FreesoundLicensePolicy
import de.laermbatz.app.freesound.FreesoundLoopCategory
import de.laermbatz.app.freesound.FreesoundOAuthFlow
import de.laermbatz.app.freesound.FreesoundSound
import de.laermbatz.app.freesound.SamplePackLicenseCatalog
import de.laermbatz.app.freesound.SamplePackZipImporter
import de.laermbatz.app.freesound.LOCAL_SAMPLE_PACK_LICENSE_PREFIX
import de.laermbatz.app.freesound.ImportStatus
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.Properties
import java.util.concurrent.Executors
import kotlin.math.log10
import kotlin.math.roundToInt

private data class TrackUiState(
    val index: Int,
    val label: String,
    val fileName: String = "Noch kein WAV geladen",
    val isLoaded: Boolean = false,
    val isLoading: Boolean = false,
    val enabled: Boolean = true,
    val gain: Float = 0.55f,
    val lowPassHz: Float = 20000f,
    val delayDivision: Int = 0,
    val delayMix: Float = 0.25f,
    val delayFeedback: Float = 0.35f,
    val reverbSend: Float = 0f,
    val effectsExpanded: Boolean = false,
    val detail: String = "",
    val sourceAttribution: String = ""
)

private data class AutoSetSelection(
    val trackIndex: Int,
    val file: File,
    val displayName: String,
    val sourceAttribution: String
)

private class AutoSetNeedsSetupException(message: String) : IOException(message)
private class AutoSetCancelledException : IOException("Auto-Set abgebrochen")

class MainActivity : ComponentActivity() {
    private var playing by mutableStateOf(false)
    private var recording by mutableStateOf(false)
    private var recordingSeconds by mutableStateOf(0)
    private var recordingStatus by mutableStateOf("Aufnahme nicht aktiv.")
    private var statusMessage by mutableStateOf(
        "Importiere WAV-Loops in bis zu sechs Spuren. TikTok-LIVE-Events sind nicht verbunden."
    )
    private var autopilotEnabled by mutableStateOf(true)
    private var autopilotIntensity by mutableStateOf(1.0f)
    private var autopilotBarPosition by mutableStateOf(0.0f)
    private var autopilotPhaseIntensity by mutableStateOf(0.0f)
    private var liveOutputPeak by mutableStateOf(0.0f)
    private var liveOutputStatus by mutableStateOf("Noch nicht gestartet")
    private var mediaVolumeText by mutableStateOf("—")
    private var availableOutputText by mutableStateOf("—")
    private lateinit var audioManager: AudioManager
    private val previewPlayer = WavPreviewPlayer()
    private var previewingTrack by mutableStateOf(-1)
    private var previewRequestSerial = 0
    private var bpm by mutableStateOf(128f)
    private var tracksRestored by mutableStateOf(false)
    private val freesoundApiClient = FreesoundApiClient()
    private lateinit var freesoundCredentialStore: FreesoundCredentialStore
    private lateinit var freesoundLibraryStore: FreesoundLibraryStore
    private val freesoundLibraryItems = mutableStateListOf<FreesoundLibraryItem>()
    private var freesoundCredentials by mutableStateOf(FreesoundCredentials())
    private var showFreesoundSettings by mutableStateOf(false)
    private var showFreesoundLibrary by mutableStateOf(false)
    private var freesoundLibraryFilter by mutableStateOf<FreesoundLoopCategory?>(null)
    private var freesoundQuery by mutableStateOf("techno loop")
    private val freesoundResults = mutableStateListOf<FreesoundSound>()
    private var freesoundBusy by mutableStateOf(false)
    private var samplePackBusy by mutableStateOf(false)
    private var samplePackStatus by mutableStateOf("Drive-ZIPs werden ausschließlich lokal importiert; öffentliche Stream-Rechte sind nicht automatisch bestätigt.")
    private var showSamplePackImportNotice by mutableStateOf(false)
    private var samplePackNoticeConfirmed by mutableStateOf(false)
    private var autoSetPreparing by mutableStateOf(false)
    private var autoStartOnOpen by mutableStateOf(false)
    @Volatile private var autoSetCancelled = false
    private var freesoundStatus by mutableStateOf("API-Zugang und Nutzungsfreigabe einrichten, um Freesound zu verwenden.")
    private var freesoundPendingState by mutableStateOf<String?>(null)
    private var selectedFreesoundTrack by mutableStateOf(0)
    private var pendingTrackIndex = -1
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val tracks = mutableStateListOf(
        TrackUiState(0, "KICK"),
        TrackUiState(1, "BASS"),
        TrackUiState(2, "HI-HAT"),
        TrackUiState(3, "PERCUSSION"),
        TrackUiState(4, "SYNTH"),
        TrackUiState(5, "EFFECTS")
    )
    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressPoller = object : Runnable {
        override fun run() {
            autopilotBarPosition = AudioEngine.autopilotBarPosition()
            autopilotPhaseIntensity = AudioEngine.autopilotPhaseIntensity()
            if (playing) {
                liveOutputPeak = AudioEngine.lastOutputPeak()
                liveOutputStatus = if (AudioEngine.isStreamRunning()) "Oboe-Stream aktiv" else "Oboe-Stream nicht aktiv"
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                mediaVolumeText = "$currentVolume / $maxVolume"
                availableOutputText = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    .map(::describeOutputDevice)
                    .distinct()
                    .ifEmpty { listOf("Kein Audioausgang erkannt") }
                    .joinToString(" · ")
            } else {
                liveOutputStatus = "Wiedergabe gestoppt"
                liveOutputPeak = 0.0f
            }
            progressHandler.postDelayed(this, 250L)
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startPlayback()
        else statusMessage =
            "Benachrichtigungen sind deaktiviert. Erlaube sie in den Android-App-Einstellungen, " +
                "damit Wiedergabe und Stopp-Steuerung sichtbar bleiben."
    }

    private val recordingStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startLocalRecording()
        else recordingStatus = "Speicherberechtigung fehlt; Aufnahme wurde nicht gestartet."
    }

    private val wavPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val index = pendingTrackIndex
        pendingTrackIndex = -1
        if (uri != null && index in tracks.indices) importWav(index, uri, bpm)
    }

    private val samplePackPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) importSamplePacks(uris)
    }

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val state = intent?.getStringExtra(AudioPlaybackService.EXTRA_STATE)
            playing = state.isActivePlaybackState()
            statusMessage = intent?.getStringExtra(AudioPlaybackService.EXTRA_MESSAGE) ?: statusMessage
            recording = intent?.getBooleanExtra(AudioPlaybackService.EXTRA_RECORDING, recording) ?: recording
            recordingSeconds = intent?.getIntExtra(AudioPlaybackService.EXTRA_RECORDING_SECONDS, recordingSeconds)
                ?: recordingSeconds
            recordingStatus = intent?.getStringExtra(AudioPlaybackService.EXTRA_RECORDING_MESSAGE) ?: recordingStatus
        }
    }

    private fun describeOutputDevice(device: AudioDeviceInfo): String = when (device.type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Telefonlautsprecher"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Hörer"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Kabelkopfhörer"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth"
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB-Audio"
        AudioDeviceInfo.TYPE_HDMI -> "HDMI-Audio"
        else -> device.productName.toString().ifBlank { "Audioausgang ${device.id}" }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        setVolumeControlStream(AudioManager.STREAM_MUSIC)
        autopilotEnabled = savedInstanceState?.getBoolean(KEY_AUTOPILOT) ?: true
        bpm = savedInstanceState?.getFloat(KEY_BPM) ?: 128f
        freesoundCredentialStore = FreesoundCredentialStore(this)
        freesoundLibraryStore = FreesoundLibraryStore(File(filesDir, FREESOUND_LIBRARY_DIR))
        freesoundCredentials = freesoundCredentialStore.load()
        freesoundPendingState = savedInstanceState?.getString(KEY_FREESOUND_PENDING_STATE)
        val preferences = getSharedPreferences(PREF_TRACK_CONTROLS, MODE_PRIVATE)
        autoStartOnOpen = preferences.getBoolean(KEY_AUTOSET_ON_OPEN, false)
        autopilotIntensity = (savedInstanceState?.getFloat(KEY_AUTOPILOT_INTENSITY)
            ?: preferences.getFloat(KEY_AUTOPILOT_INTENSITY, 1.0f)).coerceIn(0.5f, 1.25f)
        AudioEngine.setAutopilot(autopilotEnabled)
        AudioEngine.setAutopilotIntensity(autopilotIntensity)
        AudioEngine.setBpm(bpm)
        restoreTrackControls(savedInstanceState)
        restoreImportedTracks()
        refreshFreesoundLibrary()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFFB3261E),
                onPrimary = Color.White,
                background = Color(0xFF111111),
                surface = Color(0xFF1A1A1A)
            )) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF111111)) {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("LÄRMBATZ", style = MaterialTheme.typography.headlineLarge, color = Color.White)
                        Text("BUILD THE TECHNO", style = MaterialTheme.typography.titleMedium, color = Color(0xFFCC4444))
                        Text("LOOP-MIXER · 6 SPUREN", style = MaterialTheme.typography.labelLarge, color = Color.LightGray)

                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2024))) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("FREESOUND · OFFIZIELLE API", color = Color.White)
                                        Text(
                                            if (freesoundCredentials.clientSecretOrApiKey.isBlank()) "Noch nicht eingerichtet" else "API-Zugang lokal konfiguriert",
                                            color = Color.LightGray,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        OutlinedButton(
                                            onClick = { showFreesoundSettings = true },
                                            enabled = !freesoundBusy,
                                            modifier = Modifier.weight(1f)
                                        ) { Text("Einrichten") }
                                        OutlinedButton(
                                            onClick = { showFreesoundLibrary = true },
                                            enabled = !freesoundBusy,
                                            modifier = Modifier.weight(1f)
                                        ) { Text("Bibliothek (${freesoundLibraryItems.size})") }
                                    }
                                }
                                Text(
                                    "Standard: CC0-WAVs. CC BY nur nach Opt-in; erforderliche Credits müssen auch beim Stream sichtbar sein. Downloads werden vorab lokal vorgeladen; kein Scraping.",
                                    color = Color.LightGray,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                OutlinedButton(
                                    onClick = {
                                        samplePackNoticeConfirmed = false
                                        showSamplePackImportNotice = true
                                    },
                                    enabled = !freesoundBusy && !samplePackBusy,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (samplePackBusy) "Samplepack-Import läuft …" else "ZIPs aus Drive lokal importieren")
                                }
                                Text(samplePackStatus, color = Color(0xFFFFD180), style = MaterialTheme.typography.bodySmall)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Auto-Start beim Öffnen", color = Color.White)
                                        Text(
                                            "Optional: Auto-Set beim nächsten App-Start ohne Loop-Auswahl vorbereiten und starten.",
                                            color = Color.LightGray,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    Switch(
                                        checked = autoStartOnOpen,
                                        onCheckedChange = { updateAutoStartPreference(it) },
                                        enabled = !autoSetPreparing
                                    )
                                }
                                OutlinedTextField(
                                    value = freesoundQuery,
                                    onValueChange = { freesoundQuery = it },
                                    label = { Text("Suche, z. B. techno kick loop") },
                                    singleLine = true,
                                    enabled = !freesoundBusy,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedButton(
                                    onClick = {
                                        selectedFreesoundTrack = (selectedFreesoundTrack + 1) % tracks.size
                                    },
                                    enabled = !freesoundBusy,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Zielspur: ${selectedFreesoundTrack + 1}. ${tracks[selectedFreesoundTrack].label} · ändern")
                                }
                                Button(
                                    onClick = { searchFreesound() },
                                    enabled = !freesoundBusy && freesoundCredentials.apiUseConfirmed &&
                                        freesoundCredentials.clientSecretOrApiKey.isNotBlank() && freesoundQuery.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (freesoundBusy) "Freesound wird abgefragt …" else "Freesound nach WAV-Loops durchsuchen")
                                }
                                Text(freesoundStatus, color = Color(0xFFFFB4AB), style = MaterialTheme.typography.bodySmall)
                                freesoundResults.forEach { sound ->
                                    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(sound.name, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                                        val durationLabel = sound.duration?.let {
                                            String.format(Locale.getDefault(), "%.1f s", it)
                                        } ?: "Dauer unbekannt"
                                        Text(
                                            "${sound.username} · ${sound.license} · $durationLabel · WAV",
                                            color = Color.LightGray,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        if (sound.license == "Attribution") {
                                            Text(
                                                "CC BY: Credit wird gespeichert; du musst ihn auch für dein Publikum veröffentlichen.",
                                                color = Color(0xFFFFD180),
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = { downloadFreesound(sound, selectedFreesoundTrack) },
                                            enabled = !freesoundBusy && freesoundCredentials.apiUseConfirmed &&
                                                freesoundCredentials.accessToken.isNotBlank() &&
                                                !tracks[selectedFreesoundTrack].isLoading,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Herunterladen und in Spur ${selectedFreesoundTrack + 1} laden")
                                        }
                                    }
                                }
                            }
                        }

                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(statusMessage, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Autopilot", color = Color.White)
                                    Spacer(Modifier.weight(1f))
                                    Switch(
                                        checked = autopilotEnabled,
                                        enabled = !autoSetPreparing,
                                        onCheckedChange = {
                                            autopilotEnabled = it
                                            AudioEngine.setAutopilot(it)
                                            statusMessage = if (it) {
                                                "Autopilot aktiv: 64-Takt-Arrangement steuert Pegel und Effekte."
                                            } else {
                                                "Manueller Modus aktiv. Aktive Spuren werden von dir gesteuert."
                                            }
                                        }
                                    )
                                }
                                Text(
                                    if (autopilotEnabled) "Intro → Groove → Build → Drop → Break → Build → Drop. Über 8 Takte werden Spurpegel und Filter überblendet; Delay/Hall folgen dem Arrangement. Spur-Level sind Trims, FX-Regler setzen Maxima."
                                    else "Spuren werden nur abgespielt, wenn du sie manuell aktivierst.",
                                    color = Color.LightGray,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (autopilotEnabled) {
                                    Text(autopilotProgressLabel(), color = Color(0xFFFFB4AB), style = MaterialTheme.typography.bodySmall)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Energie ${(autopilotIntensity * 100).toInt()}%", color = Color.White)
                                        Slider(
                                            value = autopilotIntensity,
                                            onValueChange = {
                                                autopilotIntensity = it
                                                AudioEngine.setAutopilotIntensity(it)
                                            },
                                            valueRange = 0.5f..1.25f,
                                            modifier = Modifier.weight(1f),
                                            enabled = tracksRestored && tracks.none { it.isLoading } && !autoSetPreparing,
                                            onValueChangeFinished = { persistAutopilotIntensity() }
                                        )
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${bpm.toInt()} BPM", color = Color.White)
                                    Slider(
                                        value = bpm,
                                        onValueChange = {
                                            bpm = it
                                            AudioEngine.setBpm(it)
                                        },
                                        valueRange = 80f..160f,
                                        modifier = Modifier.weight(1f),
                                        enabled = tracksRestored && tracks.none { it.isLoading } && !autoSetPreparing
                                    )
                                }
                                Text(
                                    "Loops werden taktgenau mit WSOLA-Pitch-Lock angepasst; sehr kurze Clips können die Tonhöhe ändern.",
                                    color = Color.LightGray,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        tracks.forEach { track ->
                            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF202020))) {
                                Column(
                                    Modifier.fillMaxWidth().padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text("${track.index + 1}. ${track.label}", color = Color.White)
                                            Text(
                                                when {
                                                    track.isLoading -> "WAV wird geprüft und importiert …"
                                                    track.isLoaded -> "${track.fileName} · ${track.detail}"
                                                    else -> track.fileName
                                                },
                                                color = Color.LightGray,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            if (track.sourceAttribution.isNotBlank()) {
                                                Text(
                                                    track.sourceAttribution,
                                                    color = Color(0xFFFFD180),
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                        OutlinedButton(
                                            enabled = !track.isLoading && !autoSetPreparing,
                                            onClick = {
                                                pendingTrackIndex = track.index
                                                wavPicker.launch(arrayOf("audio/wav", "audio/x-wav", "audio/*"))
                                            }
                                        ) {
                                            Text(if (track.isLoaded) "Ersetzen" else "WAV wählen")
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = track.enabled,
                                            enabled = track.isLoaded && !autopilotEnabled && !track.isLoading,
                                            onCheckedChange = { enabled ->
                                                updateTrack(track.index) { it.copy(enabled = enabled) }
                                                AudioEngine.setTrackEnabled(track.index, enabled)
                                                persistTrackControls(track.index)
                                            }
                                        )
                                        Text(
                                            if (autopilotEnabled) "Autopilot steuert diese Spur" else "Manuell aktiv",
                                            color = Color.LightGray,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Spacer(Modifier.weight(1f))
                                        if (track.isLoaded) {
                                            TextButton(
                                                enabled = !track.isLoading && !playing && !autoSetPreparing,
                                                onClick = { toggleTrackPreview(track.index) }
                                            ) {
                                                Text(if (previewingTrack == track.index) "Vorschau stoppen" else "Abspielen")
                                            }
                                            TextButton(
                                                enabled = !track.isLoading && !autoSetPreparing,
                                                onClick = { clearTrack(track.index) }
                                            ) { Text("Entfernen") }
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "${if (autopilotEnabled) "Trim" else "Lautstärke"} ${(track.gain * 100).toInt()}%",
                                            color = Color.LightGray
                                        )
                                        Slider(
                                            value = track.gain,
                                            onValueChange = { gain ->
                                                updateTrackControls(track.index) { it.copy(gain = gain) }
                                            },
                                            valueRange = 0f..1.25f,
                                            enabled = track.isLoaded && !track.isLoading,
                                            modifier = Modifier.weight(1f),
                                            onValueChangeFinished = { persistTrackControls(track.index) }
                                        )
                                    }
                                    if (track.isLoaded) {
                                        TextButton(
                                            enabled = !track.isLoading,
                                            onClick = {
                                                updateTrack(track.index) {
                                                    it.copy(effectsExpanded = !it.effectsExpanded)
                                                }
                                            }
                                        ) {
                                            Text(if (track.effectsExpanded) "Effekte ausblenden" else "Effekte öffnen")
                                        }
                                        if (track.effectsExpanded) {
                                            Column(
                                                Modifier.fillMaxWidth().padding(start = 8.dp),
                                                verticalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Text(
                                                    "LOW-PASS · ${String.format(Locale.getDefault(), "%.1f kHz", track.lowPassHz / 1000f)}",
                                                    color = Color.LightGray,
                                                    style = MaterialTheme.typography.labelMedium
                                                )
                                                Slider(
                                                    value = track.lowPassHz,
                                                    onValueChange = { cutoff ->
                                                        updateTrackControls(track.index) {
                                                            it.copy(lowPassHz = cutoff)
                                                        }
                                                    },
                                                    valueRange = 200f..20000f,
                                                    enabled = !track.isLoading,
                                                    onValueChangeFinished = { persistTrackControls(track.index) }
                                                )
                                                Text("BEAT-DELAY", color = Color.LightGray, style = MaterialTheme.typography.labelMedium)
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    listOf(
                                                        0 to if (autopilotEnabled) "Auto" else "Aus",
                                                        1 to "1/8", 2 to "1/4", 3 to "1/2"
                                                    ).forEach { (division, label) ->
                                                        val onClick = {
                                                            updateTrackControls(track.index, persist = true) {
                                                                it.copy(delayDivision = division)
                                                            }
                                                        }
                                                        if (track.delayDivision == division) {
                                                            Button(
                                                                onClick = onClick,
                                                                enabled = !track.isLoading,
                                                                modifier = Modifier.weight(1f)
                                                            ) {
                                                                Text(label)
                                                            }
                                                        } else {
                                                            OutlinedButton(
                                                                onClick = onClick,
                                                                enabled = !track.isLoading,
                                                                modifier = Modifier.weight(1f)
                                                            ) {
                                                                Text(label)
                                                            }
                                                        }
                                                    }
                                                }
                                                if (track.delayDivision > 0) {
                                                    Text("Echo-Max ${(track.delayMix * 100).toInt()}%", color = Color.LightGray)
                                                    Slider(
                                                        value = track.delayMix,
                                                        onValueChange = { mix ->
                                                            updateTrackControls(track.index) { it.copy(delayMix = mix) }
                                                        },
                                                        valueRange = 0f..0.8f,
                                                        enabled = !track.isLoading,
                                                        onValueChangeFinished = { persistTrackControls(track.index) }
                                                    )
                                                    Text("Echo-Feedback max. ${(track.delayFeedback * 100).toInt()}%", color = Color.LightGray)
                                                    Slider(
                                                        value = track.delayFeedback,
                                                        onValueChange = { feedback ->
                                                            updateTrackControls(track.index) {
                                                                it.copy(delayFeedback = feedback)
                                                            }
                                                        },
                                                        valueRange = 0f..0.8f,
                                                        enabled = !track.isLoading,
                                                        onValueChangeFinished = { persistTrackControls(track.index) }
                                                    )
                                                }
                                                Text("HALL-MAX ${(track.reverbSend * 100).toInt()}%", color = Color.LightGray)
                                                Slider(
                                                    value = track.reverbSend,
                                                    onValueChange = { send ->
                                                        updateTrackControls(track.index) { it.copy(reverbSend = send) }
                                                    },
                                                    valueRange = 0f..0.8f,
                                                    enabled = !track.isLoading,
                                                    onValueChangeFinished = { persistTrackControls(track.index) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = {
                                if (autoSetPreparing) cancelAutoSetPreparation() else prepareAndStartAutoSet()
                            },
                            enabled = autoSetPreparing || (!freesoundBusy && tracks.none { it.isLoading }),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (autoSetPreparing) "AUTO-SET ABBRECHEN" else "AUTO-SET · automatisch befüllen & starten")
                        }

                        Button(
                            onClick = { if (playing) stopPlayback() else requestNotificationPermissionOrStart() },
                            enabled = playing || (!autoSetPreparing && tracks.any { it.isLoaded } && tracks.none { it.isLoading }),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (playing) "Mixer stoppen" else "Autopilot-Mixer starten")
                        }

                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2024))) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("LOKALER MASTER-RECORDER", color = Color.White)
                                Text(
                                    if (recording) "${formatRecordingTime(recordingSeconds)} / 60:00 · AAC 192 kbit/s"
                                    else "AAC 192 kbit/s · maximal 60 Minuten",
                                    color = Color.LightGray,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(recordingStatus, color = Color(0xFFFFB4AB), style = MaterialTheme.typography.bodySmall)
                                Button(
                                    onClick = { if (recording) stopLocalRecording() else requestLocalRecording() },
                                    enabled = recording || playing,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (recording) "AUFNAHME STOPPEN & SPEICHERN" else "MASTER-AUFNAHME STARTEN")
                                }
                                Text(
                                    "Erfasst nur den fertigen Oboe-App-Mastermix und speichert lokal unter Music/LÄRMBATZ. Kein Mikrofon und keine TikTok-Ereignisse.",
                                    color = Color.LightGray,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF18232A))) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("LIVE-AUSGANGSDIAGNOSE", color = Color.White)
                                Text("Status: $liveOutputStatus", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                                val peakText = if (liveOutputPeak < 0.00001f) "unter −100 dBFS / kein messbarer Pegel"
                                else String.format(Locale.GERMANY, "%.1f dBFS", 20.0 * log10(liveOutputPeak.toDouble()))
                                Text("Signal im Oboe-Ausgabepuffer: $peakText", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                                Text("Medienlautstärke: $mediaVolumeText", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                                Text("Erkannte Ausgänge (nicht zwingend aktiv): $availableOutputText", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                                Text("Während der Wiedergabe die Lautstärketasten verwenden und im Android-Medienausgabemenü Lautsprecher/Kopfhörer prüfen.", color = Color(0xFFFFD180), style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1616))) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("TIKTOK LIVE · NICHT VERBUNDEN", color = Color(0xFFFFB4AB))
                                Text(
                                    "Für echte Follow-/Gift-Events ist eine von TikTok genehmigte Schnittstelle erforderlich. " +
                                        "In den öffentlich dokumentierten Developer-APIs wurde keine passende Echtzeit-Event-API bestätigt. " +
                                        TikTokLiveEventSource.REASON,
                                    color = Color.LightGray,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        if (showFreesoundSettings) {
                            FreesoundCredentialsDialog(
                                initial = freesoundCredentials,
                                pendingOAuthState = freesoundPendingState,
                                busy = freesoundBusy,
                                status = freesoundStatus,
                                onDismiss = { showFreesoundSettings = false },
                                onSave = { saveFreesoundCredentials(it) },
                                onStartOAuth = { startFreesoundAuthorization(it) },
                                onExchangeCode = { credentials, redirectUrl ->
                                    exchangeFreesoundAuthorizationCode(credentials, redirectUrl)
                                }
                            )
                        }
                        if (showFreesoundLibrary) {
                            FreesoundLibraryDialog(
                                items = freesoundLibraryItems.toList(),
                                selectedTrackLabel = "${selectedFreesoundTrack + 1}. ${tracks[selectedFreesoundTrack].label}",
                                canLoad = !tracks[selectedFreesoundTrack].isLoading && !freesoundBusy,
                                categoryFilter = freesoundLibraryFilter,
                                onFilterChange = { freesoundLibraryFilter = it },
                                onChangeCategory = { item, category -> changeFreesoundCategory(item, category) },
                                onLoadIntoTrack = { item -> loadFreesoundLibraryItem(item) },
                                onDelete = { item -> deleteFreesoundLibraryItem(item) },
                                onDismiss = { showFreesoundLibrary = false }
                            )
                        }
                        if (showSamplePackImportNotice) {
                            AlertDialog(
                                onDismissRequest = {
                                    showSamplePackImportNotice = false
                                    samplePackNoticeConfirmed = false
                                },
                                title = { Text("Samplepacks nur lokal importieren") },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(SamplePackLicenseCatalog.legalImportNotice())
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = samplePackNoticeConfirmed,
                                                onCheckedChange = { samplePackNoticeConfirmed = it }
                                            )
                                            Text(
                                                "Ich habe die Anbieterbedingungen geprüft und darf diese Dateien lokal nutzen; rohe WAVs gebe ich nicht weiter.",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                },
                                confirmButton = {
                                    TextButton(
                                        enabled = samplePackNoticeConfirmed,
                                        onClick = {
                                            showSamplePackImportNotice = false
                                            samplePackNoticeConfirmed = false
                                            samplePackPicker.launch(arrayOf("application/zip"))
                                        }
                                    ) { Text("ZIPs auswählen") }
                                },
                                dismissButton = {
                                    TextButton(onClick = {
                                        showSamplePackImportNotice = false
                                        samplePackNoticeConfirmed = false
                                    }) { Text("Abbrechen") }
                                }
                            )
                        }
                    }
                }
            }
        }
        if (autoStartOnOpen && savedInstanceState == null) {
            window.decorView.post { waitForTrackRestoreThenAutoStart(0) }
        }
    }

    override fun onStart() {
        super.onStart()
        progressHandler.removeCallbacks(progressPoller)
        progressHandler.post(progressPoller)
        val filter = IntentFilter(AudioPlaybackService.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(stateReceiver, filter)
        }
        val currentState = AudioPlaybackService.currentState
        playing = currentState.isActivePlaybackState()
        statusMessage = AudioPlaybackService.currentMessage
        recording = AudioPlaybackService.currentRecording
        recordingSeconds = AudioPlaybackService.currentRecordingSeconds
        recordingStatus = AudioPlaybackService.currentRecordingMessage
    }

    override fun onStop() {
        progressHandler.removeCallbacks(progressPoller)
        stopTrackPreview()
        unregisterReceiver(stateReceiver)
        super.onStop()
    }

    private fun autopilotProgressLabel(): String {
        val names = arrayOf("INTRO", "GROOVE A", "BUILD", "DROP A", "BREAK", "BUILD B", "DROP B", "RELEASE")
        val cyclePosition = (autopilotBarPosition % 64.0f).coerceIn(0.0f, 63.999f)
        val section = (cyclePosition / 8.0f).toInt().coerceIn(0, names.lastIndex)
        val sectionPercent = (((cyclePosition - section * 8.0f) / 8.0f) * 100.0f).roundToInt()
        val energyPercent = (autopilotPhaseIntensity.coerceIn(0.0f, 1.0f) * 100.0f).roundToInt()
        return "Live: ${names[section]} · Takt ${cyclePosition.toInt() + 1}/64 · Abschnitt $sectionPercent% · Phasenintensität $energyPercent%"
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_AUTOPILOT, autopilotEnabled)
        outState.putFloat(KEY_AUTOPILOT_INTENSITY, autopilotIntensity)
        outState.putFloat(KEY_BPM, bpm)
        outState.putString(KEY_FREESOUND_PENDING_STATE, freesoundPendingState)
        tracks.forEach { track ->
            outState.putBoolean(controlKey(track.index, "enabled"), track.enabled)
            outState.putFloat(controlKey(track.index, "gain"), track.gain)
            outState.putFloat(controlKey(track.index, "low_pass"), track.lowPassHz)
            outState.putInt(controlKey(track.index, "delay_division"), track.delayDivision)
            outState.putFloat(controlKey(track.index, "delay_mix"), track.delayMix)
            outState.putFloat(controlKey(track.index, "delay_feedback"), track.delayFeedback)
            outState.putFloat(controlKey(track.index, "reverb_send"), track.reverbSend)
        }
        super.onSaveInstanceState(outState)
    }

    private fun restoreTrackControls(savedInstanceState: Bundle?) {
        val preferences = getSharedPreferences(PREF_TRACK_CONTROLS, MODE_PRIVATE)
        tracks.indices.forEach { index ->
            fun floatValue(name: String, default: Float): Float {
                val key = controlKey(index, name)
                return if (savedInstanceState?.containsKey(key) == true) {
                    savedInstanceState.getFloat(key)
                } else preferences.getFloat(key, default)
            }
            fun intValue(name: String, default: Int): Int {
                val key = controlKey(index, name)
                return if (savedInstanceState?.containsKey(key) == true) {
                    savedInstanceState.getInt(key)
                } else preferences.getInt(key, default)
            }
            fun booleanValue(name: String, default: Boolean): Boolean {
                val key = controlKey(index, name)
                return if (savedInstanceState?.containsKey(key) == true) {
                    savedInstanceState.getBoolean(key)
                } else preferences.getBoolean(key, default)
            }
            val defaults = tracks[index]
            tracks[index] = defaults.copy(
                enabled = booleanValue("enabled", defaults.enabled),
                gain = floatValue("gain", defaults.gain).coerceIn(0f, 1.25f),
                lowPassHz = floatValue("low_pass", defaults.lowPassHz).coerceIn(200f, 20000f),
                delayDivision = intValue("delay_division", defaults.delayDivision).coerceIn(0, 3),
                delayMix = floatValue("delay_mix", defaults.delayMix).coerceIn(0f, 0.8f),
                delayFeedback = floatValue("delay_feedback", defaults.delayFeedback).coerceIn(0f, 0.8f),
                reverbSend = floatValue("reverb_send", defaults.reverbSend).coerceIn(0f, 0.8f)
            )
            applyTrackControls(index)
        }
    }

    private fun applyTrackControls(index: Int, controls: TrackUiState? = tracks.getOrNull(index)) {
        val track = controls ?: return
        AudioEngine.setTrackGain(index, track.gain)
        AudioEngine.setTrackEffects(
            index, track.lowPassHz, track.delayDivision, track.delayMix,
            track.delayFeedback, track.reverbSend
        )
    }

    private fun updateTrackControls(
        index: Int,
        persist: Boolean = false,
        transform: (TrackUiState) -> TrackUiState
    ) {
        if (index !in tracks.indices) return
        tracks[index] = transform(tracks[index])
        applyTrackControls(index)
        if (persist) persistTrackControls(index)
    }

    private fun persistTrackControls(index: Int) {
        val track = tracks.getOrNull(index) ?: return
        val editor = getSharedPreferences(PREF_TRACK_CONTROLS, MODE_PRIVATE).edit()
        editor.putBoolean(controlKey(index, "enabled"), track.enabled)
        editor.putFloat(controlKey(index, "gain"), track.gain)
        editor.putFloat(controlKey(index, "low_pass"), track.lowPassHz)
        editor.putInt(controlKey(index, "delay_division"), track.delayDivision)
        editor.putFloat(controlKey(index, "delay_mix"), track.delayMix)
        editor.putFloat(controlKey(index, "delay_feedback"), track.delayFeedback)
        editor.putFloat(controlKey(index, "reverb_send"), track.reverbSend)
        editor.apply()
    }

    private fun persistAutopilotIntensity() {
        getSharedPreferences(PREF_TRACK_CONTROLS, MODE_PRIVATE).edit()
            .putFloat(KEY_AUTOPILOT_INTENSITY, autopilotIntensity)
            .apply()
    }

    override fun onDestroy() {
        previewPlayer.close()
        ioExecutor.shutdown()
        super.onDestroy()
    }

    private fun saveFreesoundCredentials(credentials: FreesoundCredentials) {
        freesoundCredentialStore.save(credentials)
        freesoundCredentials = freesoundCredentialStore.load()
        freesoundStatus = if (credentials.apiUseConfirmed) {
            "Freesound-Zugang lokal gespeichert. Suche nutzt CC0; OAuth2 ist für Downloads erforderlich."
        } else {
            "Zugang gespeichert, aber API-Aufrufe bleiben bis zur bestätigten Nutzungsfreigabe gesperrt."
        }
        showFreesoundSettings = false
    }

    private fun startFreesoundAuthorization(credentials: FreesoundCredentials) {
        if (!credentials.apiUseConfirmed) {
            freesoundStatus = "Bitte kläre zuerst API-Nutzungsbedingungen und erforderliche Freigaben."
            return
        }
        if (credentials.clientId.isBlank() || credentials.clientSecretOrApiKey.isBlank() ||
            credentials.registeredRedirectUri.isBlank()
        ) {
            freesoundStatus = "Client-ID, API-Key/Client-Secret und registrierte Redirect-URL werden benötigt."
            return
        }
        try {
            freesoundCredentialStore.save(credentials)
            freesoundCredentials = freesoundCredentialStore.load()
            val state = FreesoundOAuthFlow.createState()
            freesoundPendingState = state
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(FreesoundOAuthFlow.authorizationUrl(credentials.clientId, state))
                )
            )
            freesoundStatus = "Im Browser anmelden und zustimmen; danach die vollständige Redirect-URL hier einfügen."
        } catch (error: Exception) {
            freesoundStatus = "OAuth konnte nicht gestartet werden: ${error.message ?: "Browser nicht verfügbar"}"
        }
    }

    private fun exchangeFreesoundAuthorizationCode(credentials: FreesoundCredentials, redirectUrl: String) {
        val expectedState = freesoundPendingState
        if (expectedState.isNullOrBlank()) {
            freesoundStatus = "Bitte starte zuerst den Freesound-OAuth-Dialog."
            return
        }
        val code = try {
            FreesoundOAuthFlow.authorizationCodeFromRedirect(
                redirectUrl,
                expectedState,
                credentials.registeredRedirectUri
            )
        } catch (error: Exception) {
            freesoundStatus = error.message ?: "OAuth-Weiterleitung konnte nicht geprüft werden."
            return
        }
        if (!credentials.apiUseConfirmed) {
            freesoundStatus = "OAuth blockiert: API-Freigabe wurde nicht bestätigt."
            return
        }
        freesoundCredentialStore.save(credentials)
        freesoundBusy = true
        freesoundStatus = "OAuth-Code wird gegen ein Token ausgetauscht …"
        ioExecutor.execute {
            try {
                val token = freesoundApiClient.exchangeAuthorizationCode(
                    credentials.clientId,
                    credentials.clientSecretOrApiKey,
                    code
                )
                val refresh = token.refreshToken.orEmpty()
                val expiry = token.expiresAtEpochMillis ?: System.currentTimeMillis() + DEFAULT_TOKEN_LIFETIME_MILLIS
                freesoundCredentialStore.saveTokens(token.accessToken, refresh, expiry)
                val updated = freesoundCredentialStore.load()
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    freesoundCredentials = updated
                    freesoundPendingState = null
                    freesoundBusy = false
                    freesoundStatus = "Freesound-OAuth2 verbunden. CC0-WAV-Suche und autorisierte Downloads sind bereit."
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    freesoundBusy = false
                    freesoundStatus = "OAuth-Token konnte nicht gespeichert werden: ${error.message ?: "API-Fehler"}"
                }
            }
        }
    }

    private fun searchFreesound() {
        val credentials = freesoundCredentials
        if (!credentials.apiUseConfirmed || credentials.clientSecretOrApiKey.isBlank()) {
            freesoundStatus = "API-Suche gesperrt: erst eigenes Credential einrichten und Freigabe bestätigen."
            return
        }
        if (freesoundQuery.isBlank()) return
        freesoundBusy = true
        freesoundResults.clear()
        freesoundStatus = "Suche über die offizielle Freesound-API …"
        val query = freesoundQuery.trim()
        ioExecutor.execute {
            try {
                val page = freesoundApiClient.search(
                    query = query,
                    apiKey = credentials.clientSecretOrApiKey,
                    allowAttribution = credentials.allowAttributionLicense
                )
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    freesoundResults.addAll(page.results)
                    freesoundBusy = false
                    val licenseLabel = if (credentials.allowAttributionLicense) "CC0/CC BY" else "CC0"
                    freesoundStatus = when {
                        page.results.isNotEmpty() ->
                            "${page.results.size} passende WAV-Treffer geladen; Suchtreffer gesamt: ${page.count}."
                        page.count <= 0L ->
                            "Freesound meldet 0 Treffer für „$query“ im $licenseLabel-WAV-Filter. Versuche einen breiteren Suchbegriff."
                        else ->
                            "Freesound meldet ${page.count} Kandidaten, aber keiner bestand die lokale Lizenz-/WAV-Prüfung. Es wurde nichts geladen."
                    }
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    freesoundBusy = false
                    freesoundStatus = "Freesound-Suche fehlgeschlagen: ${error.message ?: "Netzwerk-/API-Fehler"}"
                }
            }
        }
    }

    private fun downloadFreesound(sound: FreesoundSound, trackIndex: Int) {
        if (trackIndex !in tracks.indices) return
        val credentials = freesoundCredentials
        if (!credentials.apiUseConfirmed) {
            freesoundStatus = "Download gesperrt: API-Nutzungsfreigabe fehlt."
            return
        }
        if (credentials.accessToken.isBlank()) {
            freesoundStatus = "Für Downloads zuerst OAuth2 im Freesound-Dialog verbinden."
            showFreesoundSettings = true
            return
        }
        updateTrack(trackIndex) { it.copy(isLoading = true) }
        freesoundBusy = true
        freesoundStatus = "Prüfe Lizenz und lade CC-lizenziertes WAV vor …"
        ioExecutor.execute {
            try {
                val libraryDir = File(filesDir, FREESOUND_LIBRARY_DIR)
                val incomingBytes = sound.filesize ?: MAX_FREESOUND_FILE_BYTES
                val currentBytes = libraryDir.listFiles()
                    ?.filter { it.isFile && it.extension.equals("wav", ignoreCase = true) }
                    ?.sumOf { it.length() } ?: 0L
                if (currentBytes + incomingBytes > MAX_FREESOUND_LIBRARY_BYTES) {
                    throw IOException("Die lokale Loop-Bibliothek ist auf 2,5 GiB begrenzt; entferne zuerst nicht benötigte WAVs.")
                }

                val currentCredentials = freesoundCredentialStore.load()
                var accessToken = currentCredentials.accessToken
                val expiry = currentCredentials.accessTokenExpiresAtMillis
                if (expiry > 0L && expiry <= System.currentTimeMillis() + TOKEN_REFRESH_SKEW_MILLIS) {
                    if (currentCredentials.refreshToken.isBlank()) {
                        throw IOException("OAuth2-Token ist abgelaufen. Bitte verbinde Freesound erneut.")
                    }
                    val refreshed = freesoundApiClient.refreshAccessToken(
                        currentCredentials.clientId,
                        currentCredentials.clientSecretOrApiKey,
                        currentCredentials.refreshToken
                    )
                    accessToken = refreshed.accessToken
                    freesoundCredentialStore.saveTokens(
                        refreshed.accessToken,
                        refreshed.refreshToken ?: currentCredentials.refreshToken,
                        refreshed.expiresAtEpochMillis ?: System.currentTimeMillis() + DEFAULT_TOKEN_LIFETIME_MILLIS
                    )
                }
                val file = freesoundApiClient.downloadWav(sound, accessToken, libraryDir) { stagedFile ->
                    writeFreesoundMetadata(sound, stagedFile)
                }
                val sourceUrl = sound.url ?: "https://freesound.org/sounds/${sound.id}/"
                val sourceInfo = if (sound.license == "Attribution") {
                    "CC BY-Namensnennung: ${sound.name} · ${sound.username} · Freesound #${sound.id} · $sourceUrl"
                } else {
                    "CC0 · ${sound.name} · ${sound.username} · Freesound #${sound.id} · $sourceUrl"
                }
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    freesoundBusy = false
                    freesoundCredentials = freesoundCredentialStore.load()
                    importWav(
                        trackIndex,
                        Uri.fromFile(file),
                        bpm,
                        sourceAttribution = sourceInfo,
                        displayNameOverride = sound.name
                    )
                    refreshFreesoundLibrary()
                    freesoundStatus = "${sound.name} lokal gespeichert; Lizenzmetadaten wurden mitgeführt."
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    freesoundBusy = false
                    updateTrack(trackIndex) { it.copy(isLoading = false) }
                    freesoundStatus = "Freesound-Download fehlgeschlagen: ${error.message ?: "Download nicht möglich"}"
                }
            }
        }
    }

    private fun writeFreesoundMetadata(
        sound: FreesoundSound,
        audioFile: File,
        category: FreesoundLoopCategory? = null
    ) {
        val finalFile = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.properties")
        val previousCategory = if (finalFile.isFile) runCatching {
            Properties().apply { FileInputStream(finalFile).use(::load) }.getProperty("category")
        }.getOrNull() else null
        val previousManualCategory = if (finalFile.isFile) runCatching {
            Properties().apply { FileInputStream(finalFile).use(::load) }.getProperty("category_manually_set")
        }.getOrNull() else null
        val metadata = Properties().apply {
            setProperty("provider", "Freesound")
            setProperty("sound_id", sound.id.toString())
            setProperty("name", sound.name)
            setProperty("username", sound.username)
            setProperty("license", sound.license)
            setProperty("original_url", sound.url ?: "https://freesound.org/sounds/${sound.id}/")
            setProperty("downloaded_at_epoch_ms", System.currentTimeMillis().toString())
            setProperty("attribution_required", (sound.license == "Attribution").toString())
            setProperty("category", category?.name ?: previousCategory ?: FreesoundLoopCategory.UNCATEGORIZED.name)
            setProperty("category_manually_set", if (category != null) "false" else previousManualCategory ?: "false")
            setProperty("tags", sound.tags.joinToString(" "))
        }
        val temporaryFile = File(audioFile.parentFile, "${finalFile.name}.tmp")
        try {
            FileOutputStream(temporaryFile).use { output -> metadata.store(output, "Freesound source and license metadata") }
            Files.move(
                temporaryFile.toPath(),
                finalFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (error: IOException) {
            throw IOException("Freesound-Lizenzmetadaten konnten nicht atomar gespeichert werden", error)
        } finally {
            if (temporaryFile.exists()) temporaryFile.delete()
        }
    }

    private fun refreshFreesoundLibrary() {
        if (!::freesoundLibraryStore.isInitialized) return
        ioExecutor.execute {
            val listed = runCatching { freesoundLibraryStore.list() }.getOrElse { emptyList() }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                freesoundLibraryItems.clear()
                freesoundLibraryItems.addAll(listed)
            }
        }
    }

    private fun importSamplePacks(uris: List<Uri>) {
        if (uris.isEmpty() || freesoundBusy || samplePackBusy || autoSetPreparing) return
        if (uris.size > MAX_SAMPLE_PACK_ARCHIVES) {
            samplePackStatus = "Pro Import sind höchstens $MAX_SAMPLE_PACK_ARCHIVES ZIP-Archive erlaubt."
            return
        }
        samplePackBusy = true
        freesoundBusy = true
        samplePackStatus = "${uris.size} ausgewählte ZIP-Datei(en) werden geprüft …"
        val destination = File(filesDir, FREESOUND_LIBRARY_DIR)

        ioExecutor.execute {
            var importedCount = 0
            var duplicateCount = 0
            var unknownCount = 0
            var failedMetadataCount = 0
            var batchDecompressedBytes = 0L
            var batchLimitReported = false
            val warnings = mutableListOf<String>()

            uris.forEachIndexed { position, uri ->
                if (batchDecompressedBytes >= MAX_SAMPLE_PACK_BATCH_DECOMPRESSED_BYTES) {
                    if (!batchLimitReported) {
                        warnings += "Kumulatives 4-GiB-Dekompressionslimit erreicht; weitere ZIPs nicht geöffnet"
                        batchLimitReported = true
                    }
                    return@forEachIndexed
                }
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        samplePackStatus = "ZIP ${position + 1}/${uris.size}: Datei- und Lizenzquelle wird geprüft …"
                    }
                }
                val archiveName = runCatching { queryDisplayName(uri) }.getOrDefault("unbekannt.zip")
                val policy = SamplePackLicenseCatalog.forArchiveName(archiveName)
                if (policy == null) {
                    unknownCount++
                    warnings += "$archiveName: kein geprüfter Pack-Eintrag; übersprungen"
                    return@forEachIndexed
                }

                val result = try {
                    val archiveStream = contentResolver.openInputStream(uri)
                        ?: throw IOException("Android konnte das ausgewählte ZIP nicht öffnen")
                    val remainingBatchBudget = MAX_SAMPLE_PACK_BATCH_DECOMPRESSED_BYTES - batchDecompressedBytes
                    SamplePackZipImporter(
                        inputStream = archiveStream,
                        archiveDisplayName = archiveName,
                        destinationDirectory = destination,
                        maxTotalDecompressedBytes = remainingBatchBudget
                    ).import()
                } catch (error: Exception) {
                    warnings += "$archiveName: ${error.message ?: "Datei konnte nicht gelesen werden"}"
                    return@forEachIndexed
                }

                batchDecompressedBytes =
                    (batchDecompressedBytes + result.decompressedBytesRead).coerceAtMost(MAX_SAMPLE_PACK_BATCH_DECOMPRESSED_BYTES)
                duplicateCount += result.duplicateCount
                if (result.status != ImportStatus.COMPLETE) {
                    warnings += "$archiveName: ${result.status}; ${result.importedFiles.size} WAV(s) eventuell bereits lokal übernommen"
                }
                result.importedFiles.forEach { imported ->
                    val category = FreesoundAutoCategorizer.inferCategory(imported.name, policy.packName)
                        ?: FreesoundLoopCategory.UNCATEGORIZED
                    try {
                        freesoundLibraryStore.registerImportedPackSample(imported.file, policy, category)
                        importedCount++
                    } catch (error: Exception) {
                        failedMetadataCount++
                        warnings += "${imported.name}: Herkunft konnte nicht gespeichert werden; Datei bleibt gesperrt"
                    }
                }
            }

            val finalLibrary = runCatching { freesoundLibraryStore.list() }.getOrElse {
                warnings += "Lokale Bibliothek konnte nicht neu geladen werden"
                emptyList()
            }
            val finalStatus = buildString {
                append("Import beendet: ").append(importedCount).append(" WAV(s) lokal hinzugefügt; ")
                append(duplicateCount).append(" Duplikat(e) übersprungen.")
                if (unknownCount > 0) append(" ").append(unknownCount).append(" unbekannte ZIP(s) nicht importiert.")
                if (failedMetadataCount > 0) append(" ").append(failedMetadataCount).append(" Datei(en) ohne Lizenzmetadaten blockiert.")
                if (warnings.isNotEmpty()) append(" Details: ").append(warnings.take(4).joinToString("; "))
                append(" Pack-WAVs und Archive werden nicht in APK/GitHub/Website kopiert.")
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                freesoundLibraryItems.clear()
                freesoundLibraryItems.addAll(finalLibrary)
                samplePackStatus = finalStatus
                samplePackBusy = false
                freesoundBusy = false
            }
        }
    }

    private fun changeFreesoundCategory(item: FreesoundLibraryItem, category: FreesoundLoopCategory) {
        ioExecutor.execute {
            try {
                freesoundLibraryStore.setCategory(item, category)
                val listed = freesoundLibraryStore.list()
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    freesoundLibraryItems.clear()
                    freesoundLibraryItems.addAll(listed)
                    freesoundStatus = "${item.title} ist jetzt in „${category.label}“ einsortiert."
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        freesoundStatus = "Kategorie konnte nicht gespeichert werden: ${error.message ?: "Dateifehler"}"
                    }
                }
            }
        }
    }

    private fun deleteFreesoundLibraryItem(item: FreesoundLibraryItem) {
        ioExecutor.execute {
            try {
                if (!freesoundLibraryStore.delete(item)) throw IOException("Die lokale WAV-Datei konnte nicht gelöscht werden")
                val listed = freesoundLibraryStore.list()
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    freesoundLibraryItems.clear()
                    freesoundLibraryItems.addAll(listed)
                    freesoundStatus = "${item.title} wurde aus der lokalen Bibliothek gelöscht. Geladene Mixer-Tracks bleiben erhalten."
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        freesoundStatus = "Lokaler Loop konnte nicht gelöscht werden: ${error.message ?: "Dateifehler"}"
                    }
                }
            }
        }
    }

    private fun loadFreesoundLibraryItem(item: FreesoundLibraryItem) {
        val index = selectedFreesoundTrack
        if (index !in tracks.indices || tracks[index].isLoading || freesoundBusy) return
        val sourceInfo = attributionFor(item)
        showFreesoundLibrary = false
        importWav(
            index,
            Uri.fromFile(item.file),
            bpm,
            sourceAttribution = sourceInfo,
            displayNameOverride = item.title
        )
    }

    private fun prepareAndStartAutoSet() {
        if (autoSetPreparing || freesoundBusy) return
        if (tracks.any { it.isLoading }) {
            statusMessage = "Auto-Set wartet, bis der laufende WAV-Import abgeschlossen ist."
            return
        }

        val trackSnapshot = tracks.toList()
        autoSetCancelled = false
        autoSetPreparing = true
        freesoundBusy = true
        autopilotEnabled = true
        AudioEngine.setAutopilot(true)
        statusMessage = "Auto-Set: ordne vorhandene Loops zu und ergänze fehlende Rollen …"

        ioExecutor.execute {
            try {
                val usedPaths = mutableSetOf<String>()
                val usedSoundIds = mutableSetOf<Long>()
                val selectionsByTrack = mutableMapOf<Int, AutoSetSelection>()
                val missing = mutableListOf<Pair<Int, FreesoundLoopCategory>>()

                var library = freesoundLibraryStore.list()
                library.filter {
                    it.category == FreesoundLoopCategory.UNCATEGORIZED && !it.categoryManuallySet
                }.forEach { item ->
                    FreesoundAutoCategorizer.inferCategory(item.title, "${item.tags} ${item.file.name}")
                        ?.let { category -> freesoundLibraryStore.setCategory(item, category, manualOverride = false) }
                }
                library = freesoundLibraryStore.list()
                library.forEach { item ->
                    FREESOUND_FILENAME_ID.find(item.file.name)?.groupValues?.getOrNull(1)?.toLongOrNull()
                        ?.let(usedSoundIds::add)
                }

                fun selectItem(trackIndex: Int, item: FreesoundLibraryItem) {
                    val canonicalPath = item.file.canonicalPath
                    if (!usedPaths.add(canonicalPath)) return
                    FREESOUND_FILENAME_ID.find(item.file.name)?.groupValues?.getOrNull(1)?.toLongOrNull()
                        ?.let(usedSoundIds::add)
                    selectionsByTrack[trackIndex] = AutoSetSelection(
                        trackIndex = trackIndex,
                        file = item.file,
                        displayName = item.title,
                        sourceAttribution = attributionFor(item)
                    )
                }

                AUTO_SET_CATEGORIES.forEachIndexed { trackIndex, category ->
                    val candidates = library.filter { it.category == category && it.file.canonicalPath !in usedPaths }
                    val item = candidates.shuffled().firstOrNull()
                    if (item != null) {
                        selectItem(trackIndex, item)
                        return@forEachIndexed
                    }

                    val existingTrack = trackSnapshot[trackIndex]
                    val existingFile = trackFile(trackIndex)
                    if (existingTrack.isLoaded && existingFile.isFile && usedPaths.add(existingFile.canonicalPath)) {
                        selectionsByTrack[trackIndex] = AutoSetSelection(
                            trackIndex = trackIndex,
                            file = existingFile,
                            displayName = existingTrack.fileName,
                            sourceAttribution = existingTrack.sourceAttribution.ifBlank {
                                "Lokale WAV · ${existingTrack.fileName}"
                            }
                        )
                    } else {
                        missing += trackIndex to category
                    }
                }

                if (missing.isNotEmpty()) {
                    val credentials = freesoundCredentialStore.load()
                    if (!credentials.apiUseConfirmed || credentials.clientId.isBlank() ||
                        credentials.clientSecretOrApiKey.isBlank()
                    ) {
                        throw AutoSetNeedsSetupException(
                            "Für fehlende Rollen braucht das Auto-Set dein Freesound-Credential und die bestätigte API-Freigabe."
                        )
                    }
                    val accessToken = ensureAutoSetAccessToken(credentials)
                    val libraryDirectory = File(filesDir, FREESOUND_LIBRARY_DIR)

                    missing.forEach { (trackIndex, category) ->
                        if (autoSetCancelled) throw AutoSetCancelledException()
                        runOnUiThread {
                            if (!isFinishing && !isDestroyed) {
                                val message = "Auto-Set: suche ${category.label}-Loop (${trackIndex + 1}/6) …"
                                statusMessage = message
                                freesoundStatus = message
                            }
                        }
                        var selectedSound: FreesoundSound? = null
                        val searchQueries = FreesoundAutoCategorizer.searchQueries(category)
                        for (candidateQuery in searchQueries) {
                            if (autoSetCancelled) throw AutoSetCancelledException()
                            runOnUiThread {
                                if (!isFinishing && !isDestroyed) {
                                    val message = "Auto-Set: suche ${category.label}-Loop (${trackIndex + 1}/6) mit „$candidateQuery“ …"
                                    statusMessage = message
                                    freesoundStatus = message
                                }
                            }
                            val page = freesoundApiClient.search(
                                query = candidateQuery,
                                apiKey = credentials.clientSecretOrApiKey,
                                allowAttribution = credentials.allowAttributionLicense
                            )
                            selectedSound = page.results.firstOrNull { candidate ->
                                candidate.id !in usedSoundIds &&
                                    (candidate.filesize == null || candidate.filesize <= MAX_FREESOUND_FILE_BYTES)
                            }
                            if (selectedSound != null) break
                        }
                        val sound = selectedSound ?: throw IOException(
                            "Keine passende, lizenzzulässige WAV für ${category.label} nach " +
                                "${searchQueries.size} Suchvarianten gefunden. Bereits geladene Loops bleiben in der Bibliothek."
                        )

                        val currentBytes = libraryDirectory.listFiles()
                            ?.filter { it.isFile && it.extension.equals("wav", ignoreCase = true) }
                            ?.sumOf { it.length() } ?: 0L
                        val incomingBytes = sound.filesize ?: MAX_FREESOUND_FILE_BYTES
                        if (incomingBytes < 0L || incomingBytes > MAX_FREESOUND_FILE_BYTES ||
                            currentBytes > MAX_FREESOUND_LIBRARY_BYTES - incomingBytes
                        ) {
                            throw IOException("Der lokale Loop-Speicher (256 MiB) ist voll; bitte Loops entfernen.")
                        }

                        val file = freesoundApiClient.downloadWav(sound, accessToken, libraryDirectory)
                        writeFreesoundMetadata(sound, file, category)
                        usedSoundIds += sound.id
                        library = freesoundLibraryStore.list()
                        val item = library.firstOrNull { it.file.canonicalFile == file.canonicalFile }
                            ?: throw IOException("Der automatisch geladene Loop fehlt in der lokalen Bibliothek.")
                        selectItem(trackIndex, item)
                    }
                }

                if (autoSetCancelled) throw AutoSetCancelledException()
                val orderedSelections = AUTO_SET_CATEGORIES.indices.map { trackIndex ->
                    selectionsByTrack[trackIndex]
                        ?: throw IOException("Auto-Set unvollständig: Spur ${trackIndex + 1} hat keinen Loop.")
                }
                val finalLibrary = library
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    freesoundLibraryItems.clear()
                    freesoundLibraryItems.addAll(finalLibrary)
                    statusMessage = "Auto-Set bereit: sechs Rollen werden automatisch geladen …"
                    loadAutoSetSequentially(orderedSelections, 0)
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    autoSetPreparing = false
                    freesoundBusy = false
                    statusMessage = if (error is AutoSetCancelledException) {
                        "Auto-Set abgebrochen; bereits heruntergeladene Loops bleiben in der Bibliothek."
                    } else {
                        "Auto-Set konnte nicht vollständig vorbereitet werden: ${error.message ?: "unbekannter Fehler"}"
                    }
                    freesoundStatus = statusMessage
                    if (error is AutoSetNeedsSetupException) showFreesoundSettings = true
                }
            }
        }
    }

    private fun ensureAutoSetAccessToken(credentials: FreesoundCredentials): String {
        var current = freesoundCredentialStore.load()
        if (current.accessToken.isBlank()) {
            throw AutoSetNeedsSetupException("Einmalige Freesound-OAuth2-Freigabe erforderlich.")
        }
        if (current.accessTokenExpiresAtMillis > 0L &&
            current.accessTokenExpiresAtMillis <= System.currentTimeMillis() + TOKEN_REFRESH_SKEW_MILLIS
        ) {
            if (current.refreshToken.isBlank()) {
                throw AutoSetNeedsSetupException("OAuth2 ist abgelaufen; bitte Freesound erneut verbinden.")
            }
            val refreshed = freesoundApiClient.refreshAccessToken(
                credentials.clientId,
                credentials.clientSecretOrApiKey,
                current.refreshToken
            )
            freesoundCredentialStore.saveTokens(
                refreshed.accessToken,
                refreshed.refreshToken ?: current.refreshToken,
                refreshed.expiresAtEpochMillis ?: System.currentTimeMillis() + DEFAULT_TOKEN_LIFETIME_MILLIS
            )
            current = freesoundCredentialStore.load()
            runOnUiThread {
                if (!isFinishing && !isDestroyed) freesoundCredentials = current
            }
        }
        return current.accessToken
    }

    private fun attributionFor(item: FreesoundLibraryItem): String = when {
        SamplePackLicenseCatalog.isLocalPackLicense(item.license) ->
            "Samplepack ${item.sourcePack} (${item.provider}): ${item.license.removePrefix(LOCAL_SAMPLE_PACK_LICENSE_PREFIX)} Quelle: ${item.originalUrl}"
        item.license == FreesoundLicensePolicy.ATTRIBUTION ->
            "CC BY-Namensnennung: ${item.title} · ${item.username} · ${item.originalUrl}"
        else -> "${item.license.ifBlank { "Freesound" }} · ${item.title} · ${item.username} · ${item.originalUrl}"
    }

    private fun loadAutoSetSequentially(selections: List<AutoSetSelection>, position: Int) {
        if (autoSetCancelled) {
            autoSetPreparing = false
            freesoundBusy = false
            statusMessage = "Auto-Set abgebrochen; eine gerade laufende Spur wurde noch sicher abgeschlossen."
            return
        }
        if (position >= selections.size) {
            autoSetPreparing = false
            freesoundBusy = false
            refreshFreesoundLibrary()
            if (playing) {
                statusMessage = "Auto-Set bereit: sechs Spuren aktualisiert; die Wiedergabe läuft weiter."
            } else {
                statusMessage = "Auto-Set bereit: sechs Rollen geladen; Autopilot startet."
                requestNotificationPermissionOrStart()
            }
            return
        }

        val selection = selections[position]
        importWav(
            index = selection.trackIndex,
            uri = Uri.fromFile(selection.file),
            targetBpm = bpm,
            sourceAttribution = selection.sourceAttribution,
            displayNameOverride = selection.displayName,
            onComplete = { success ->
                if (success) {
                    loadAutoSetSequentially(selections, position + 1)
                } else {
                    autoSetPreparing = false
                    freesoundBusy = false
                    statusMessage = "Auto-Set abgebrochen: ${tracks[selection.trackIndex].label} konnte nicht geladen werden."
                }
            }
        )
    }

    private fun updateAutoStartPreference(enabled: Boolean) {
        autoStartOnOpen = enabled
        getSharedPreferences(PREF_TRACK_CONTROLS, MODE_PRIVATE).edit()
            .putBoolean(KEY_AUTOSET_ON_OPEN, enabled)
            .apply()
    }

    private fun cancelAutoSetPreparation() {
        if (!autoSetPreparing) return
        autoSetCancelled = true
        statusMessage = "Auto-Set-Abbruch angefordert; ein laufender API-Aufruf wird noch sicher beendet."
    }

    private fun waitForTrackRestoreThenAutoStart(attempt: Int) {
        if (!autoStartOnOpen || isFinishing || isDestroyed) return
        if (!tracksRestored && attempt < 80) {
            progressHandler.postDelayed({ waitForTrackRestoreThenAutoStart(attempt + 1) }, 250L)
            return
        }
        prepareAndStartAutoSet()
    }

    private fun requestNotificationPermissionOrStart() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startPlayback()
        }
    }

    private fun startPlayback() {
        stopTrackPreview()
        if (AudioEngine.loadedTrackCount() == 0) {
            statusMessage = "Importiere zuerst mindestens eine gültige WAV-Datei."
            return
        }
        AudioEngine.setAutopilot(autopilotEnabled)
        AudioEngine.setAutopilotIntensity(autopilotIntensity)
        AudioEngine.setBpm(bpm)
        val intent = Intent(this, AudioPlaybackService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(this, intent)
        else startService(intent)
        statusMessage = "Audio-Mixer wird gestartet …"
    }

    private fun toggleTrackPreview(index: Int) {
        if (index !in tracks.indices || !tracks[index].isLoaded || tracks[index].isLoading) return
        if (playing || AudioEngine.isStreamRunning()) {
            statusMessage = "Soundfile-Vorschau ist nur bei gestopptem Mixer verfügbar."
            return
        }
        if (previewingTrack == index) {
            stopTrackPreview()
            return
        }
        stopTrackPreview()
        val request = ++previewRequestSerial
        previewingTrack = index
        statusMessage = "Vorschau: ${tracks[index].fileName} · Originaldatei ohne Mixer-Effekte."
        previewPlayer.play(trackFile(index)) { error ->
            if (request != previewRequestSerial) return@play
            previewingTrack = -1
            statusMessage = error?.let { "Soundfile-Vorschau fehlgeschlagen: $it" }
                ?: "Soundfile-Vorschau beendet."
        }
    }

    private fun stopTrackPreview() {
        previewRequestSerial++
        previewPlayer.stop()
        if (previewingTrack >= 0) {
            previewingTrack = -1
            statusMessage = "Soundfile-Vorschau gestoppt."
        }
    }

    private fun stopPlayback() {
        if (autoSetPreparing) autoSetCancelled = true
        startService(Intent(this, AudioPlaybackService::class.java).setAction(AudioPlaybackService.ACTION_STOP))
        statusMessage = "Mixer wird weich ausgeblendet …"
    }

    private fun requestLocalRecording() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            recordingStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else startLocalRecording()
    }

    private fun startLocalRecording() {
        startService(Intent(this, AudioPlaybackService::class.java).setAction(AudioPlaybackService.ACTION_RECORD_START))
        recordingStatus = "AAC-Aufnahme wird vorbereitet …"
    }

    private fun stopLocalRecording() {
        startService(Intent(this, AudioPlaybackService::class.java).setAction(AudioPlaybackService.ACTION_RECORD_STOP))
        recordingStatus = "Aufnahme wird finalisiert …"
    }

    private fun formatRecordingTime(seconds: Int): String =
        String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60)

    private fun importWav(
        index: Int,
        uri: Uri,
        targetBpm: Float,
        sourceAttribution: String? = null,
        displayNameOverride: String? = null,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        if (previewingTrack == index) stopTrackPreview()
        val controlSnapshot = tracks[index]
        updateTrack(index) { it.copy(isLoading = true) }
        statusMessage = "WAV für ${tracks[index].label} wird geprüft …"
        ioExecutor.execute {
            val tempFile = File(cacheDir, "track_${index}_${System.nanoTime()}.tmp")
            try {
                copyUriToFile(uri, tempFile)
                val decoded = tempFile.inputStream().use { WavDecoder.decode(it) }
                val analysis = LoopTempoAnalyzer.analyze(decoded, targetBpm)
                val target = trackFile(index)
                if (!AudioEngine.loadTrack(index, decoded, analysis.bars)) {
                    throw IllegalArgumentException("Native Audio-Engine hat diese WAV-Spur abgelehnt")
                }
                applyTrackControls(index, controlSnapshot)
                if (!tempFile.renameTo(target)) {
                    val restored = if (target.isFile) runCatching {
                        val oldWav = target.inputStream().use { WavDecoder.decode(it) }
                        val oldAnalysis = LoopTempoAnalyzer.analyze(oldWav, targetBpm)
                        AudioEngine.loadTrack(index, oldWav, oldAnalysis.bars).also { loaded ->
                            if (loaded) applyTrackControls(index, controlSnapshot)
                        }
                    }.getOrDefault(false) else false
                    if (!restored) AudioEngine.clearTrack(index)
                    throw IOException("Neue WAV konnte nicht dauerhaft gespeichert werden; vorherige Spur wurde wiederhergestellt")
                }
                val fileName = displayNameOverride?.trim()?.take(80)?.takeIf { it.isNotBlank() }
                    ?: queryDisplayName(uri)
                val detail = trackDetail(decoded, analysis)
                getSharedPreferences(PREF_TRACK_CONTROLS, MODE_PRIVATE).edit()
                    .putString(sourceAttributionKey(index), sourceAttribution.orEmpty())
                    .apply()
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    updateTrack(index) {
                        it.copy(
                            fileName = fileName,
                            detail = detail,
                            isLoaded = true,
                            isLoading = false,
                            enabled = true,
                            sourceAttribution = sourceAttribution.orEmpty()
                        )
                    }
                    AudioEngine.setTrackEnabled(index, true)
                    persistTrackControls(index)
                    val tempoText = when {
                        analysis.detectedBpm == null -> "BPM nicht sicher erkannt; Takte aus Dateilänge geschätzt"
                        analysis.tempoAmbiguous -> "≈${analysis.detectedBpm.roundToInt()} BPM, Halb-/Doppeltempo möglich"
                        else -> "≈${analysis.detectedBpm.roundToInt()} BPM erkannt"
                    }
                    val stretchText = if (analysis.usesPitchPreservingStretch) {
                        "WSOLA-Pitch-Lock aktiv"
                    } else {
                        "sehr kurzer Clip: Varispeed-Fallback, Tonhöhe ändert sich"
                    }
                    val launchText = if (playing) " Start am nächsten Takt." else ""
                    statusMessage = "$fileName: $tempoText, ${analysis.bars} Takte auf ${targetBpm.toInt()} BPM quantisiert; $stretchText.$launchText"
                    onComplete?.invoke(true)
                }
            } catch (error: Exception) {
                tempFile.delete()
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    updateTrack(index) { it.copy(isLoading = false) }
                    statusMessage = "WAV-Import fehlgeschlagen: ${error.message ?: "Datei konnte nicht gelesen werden"}"
                    onComplete?.invoke(false)
                }
            }
        }
    }

    private fun copyUriToFile(uri: Uri, destination: File) {
        val maximumBytes = 32L * 1024L * 1024L
        val input = if (uri.scheme == "file") {
            val path = uri.path ?: throw IOException("Lokale Datei-URI enthält keinen Pfad")
            File(path).inputStream()
        } else {
            contentResolver.openInputStream(uri) ?: throw IOException("Datei kann nicht geöffnet werden")
        }
        input.use { source ->
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    total += count
                    if (total > maximumBytes) throw IllegalArgumentException("WAV ist größer als 32 MiB")
                    output.write(buffer, 0, count)
                }
            }
        }
    }

    private fun restoreImportedTracks() {
        val targetBpm = bpm
        val controlSnapshots = tracks.toList()
        ioExecutor.execute {
            try {
                for (index in controlSnapshots.indices) {
                    val file = trackFile(index)
                    if (!file.isFile) continue
                    try {
                        val decoded = file.inputStream().use { WavDecoder.decode(it) }
                        val analysis = LoopTempoAnalyzer.analyze(decoded, targetBpm)
                        if (AudioEngine.loadTrack(index, decoded, analysis.bars)) {
                            applyTrackControls(index, controlSnapshots[index])
                            val detail = trackDetail(decoded, analysis)
                            runOnUiThread {
                                if (isFinishing || isDestroyed) return@runOnUiThread
                                updateTrack(index) {
                                    it.copy(
                                        fileName = file.name,
                                        detail = detail,
                                        isLoaded = true,
                                        isLoading = false,
                                        enabled = controlSnapshots[index].enabled,
                                        sourceAttribution = getSharedPreferences(PREF_TRACK_CONTROLS, MODE_PRIVATE)
                                            .getString(sourceAttributionKey(index), "").orEmpty()
                                    )
                                }
                                AudioEngine.setTrackEnabled(index, controlSnapshots[index].enabled)
                            }
                        }
                    } catch (error: Exception) {
                        file.delete()
                        runOnUiThread {
                            if (isFinishing || isDestroyed) return@runOnUiThread
                            updateTrack(index) { it.copy(fileName = "Gespeicherte WAV ungültig", isLoaded = false) }
                        }
                    }
                }
            } finally {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) tracksRestored = true
                }
            }
        }
    }

    private fun trackDetail(wav: DecodedWav, analysis: LoopTempoAnalysis): String {
        val duration = wav.frameCount.toDouble() / wav.sampleRate
        val durationText = String.format(Locale.getDefault(), "%.1f s", duration)
        val tempoText = when {
            analysis.detectedBpm == null -> "BPM unklar"
            analysis.tempoAmbiguous -> "≈${analysis.detectedBpm.roundToInt()} BPM · Halb-/Doppeltempo?"
            else -> "≈${analysis.detectedBpm.roundToInt()} BPM"
        }
        val rateText = if (analysis.usesPitchPreservingStretch) {
            String.format(Locale.getDefault(), "WSOLA ×%.2f (Pitch-Lock)", analysis.playbackRate)
        } else {
            String.format(Locale.getDefault(), "Varispeed ×%.2f (Tonhöhe ändert)", analysis.playbackRate)
        }
        return "$durationText · ${analysis.bars} Takte · $tempoText · $rateText"
    }

    private fun clearTrack(index: Int) {
        if (previewingTrack == index) stopTrackPreview()
        ioExecutor.execute {
            AudioEngine.clearTrack(index)
            trackFile(index).delete()
            getSharedPreferences(PREF_TRACK_CONTROLS, MODE_PRIVATE).edit()
                .remove(sourceAttributionKey(index))
                .apply()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                updateTrack(index) {
                    it.copy(
                        fileName = "Noch kein WAV geladen",
                        isLoaded = false,
                        isLoading = false,
                        enabled = true,
                        detail = "",
                        sourceAttribution = ""
                    )
                }
                statusMessage = "Spur ${tracks[index].label} entfernt."
                if (playing && AudioEngine.loadedTrackCount() == 0) stopPlayback()
            }
        }
    }

    private fun trackFile(index: Int): File = File(File(filesDir, "loops"), "track_$index.wav").also {
        it.parentFile?.mkdirs()
    }

    private fun queryDisplayName(uri: Uri): String {
        val cursor: Cursor? = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        cursor?.use {
            val column = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column >= 0 && it.moveToFirst()) return it.getString(column)?.take(80) ?: "WAV"
        }
        return "WAV"
    }

    private fun updateTrack(index: Int, transform: (TrackUiState) -> TrackUiState) {
        if (index in tracks.indices) tracks[index] = transform(tracks[index])
    }

    private fun String?.isActivePlaybackState(): Boolean =
        this == AudioPlaybackService.STATE_PLAYING ||
            this == AudioPlaybackService.STATE_PAUSED ||
            this == AudioPlaybackService.STATE_STOPPING

    companion object {
        private const val KEY_AUTOPILOT = "autopilot_enabled"
        private const val KEY_AUTOPILOT_INTENSITY = "autopilot_intensity"
        private const val KEY_BPM = "bpm"
        private const val KEY_AUTOSET_ON_OPEN = "auto_set_on_open"
        private const val KEY_FREESOUND_PENDING_STATE = "freesound_pending_oauth_state"
        private const val PREF_TRACK_CONTROLS = "mixer_track_controls"
        private const val FREESOUND_LIBRARY_DIR = "freesound_library"
        private const val MAX_SAMPLE_PACK_ARCHIVES = 8
        private const val MAX_SAMPLE_PACK_BATCH_DECOMPRESSED_BYTES = 4L * 1024L * 1024L * 1024L
        private const val MAX_FREESOUND_FILE_BYTES = 32L * 1024L * 1024L
        private const val MAX_FREESOUND_LIBRARY_BYTES = 2_684_354_560L
        private const val TOKEN_REFRESH_SKEW_MILLIS = 60_000L
        private const val DEFAULT_TOKEN_LIFETIME_MILLIS = 24L * 60L * 60L * 1_000L
        private val AUTO_SET_CATEGORIES = listOf(
            FreesoundLoopCategory.KICK,
            FreesoundLoopCategory.BASSLINE,
            FreesoundLoopCategory.HI_HAT,
            FreesoundLoopCategory.PERCUSSION,
            FreesoundLoopCategory.SYNTH,
            FreesoundLoopCategory.EFFECTS
        )
        private val FREESOUND_FILENAME_ID = Regex("^freesound_(\\d+)_", RegexOption.IGNORE_CASE)

        private fun controlKey(index: Int, name: String) = "track_${index}_$name"
        private fun sourceAttributionKey(index: Int) = "track_${index}_source_attribution"
    }
}
