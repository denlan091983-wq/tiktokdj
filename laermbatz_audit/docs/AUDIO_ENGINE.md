# Audio-Engine – technische Leitplanken

## Ziel

Ein professionell klingender, kontinuierlicher Techno-Track, der durch Events kontrolliert erweitert wird. Keine zufällige Aneinanderreihung beliebiger Samples.

### Aktueller MVP-Stand (4. November 2026)

WAV-Loops werden offline auf Quelltempo (60–180 BPM) und ganzzahlige Taktzahl (1–64) analysiert. Der Oboe-Mixer nutzt im Echtzeit-Callback einen vorallokierten WSOLA-Stretcher mit korrelationsbasierter Frame-Auswahl und 10-ms-Hops, um die Loopdauer pitch-erhaltend an das Set-BPM anzupassen; bei Live-Import startet die Spur am nächsten Takt. Jede geladene Spur hat einen Low-pass (200 Hz bis 20 kHz), BPM-synchrones Feedback-Delay (1/8, 1/4 oder 1/2 Note) und Reverb-Send. Delay- und Reverb-Puffer werden vor Streamstart bereitgestellt; Callback-Verarbeitung arbeitet ohne JNI-Aufrufe, Locks oder Heap-Allokationen. Delayzeit und Wet-/Feedbackwerte werden geglättet, Echo- und Reverb-Ausklang laufen pre-Level je Spur in den Limiter. Loops kürzer als `max(512 Frames, aufgerundete 20 ms)` haben zu wenig Material und nutzen Varispeed als sichtbaren Fallback; extreme Tempoänderungen können auch bei WSOLA Artefakte verursachen. Nicht-perkussive oder mehrdeutige Loops können falsch erkannt werden; dann wird die Taktzahl ersatzweise anhand der Dateidauer und des gewählten BPM geschätzt. Sample 0 gilt als Downbeat.

## 1. Grundarchitektur

Empfohlene Trennung:
- Audio Engine: spielt Loops, One-Shots und Übergänge latenzarm.
- Transport Clock: BPM, Beatposition, Taktposition.
- Arrangement Manager: entscheidet, wann Breaks, Builds, Drops und Variationen stattfinden.
- Event Queue: nimmt Follower-/Geschenkereignisse an und plant Aktionen.
- Mixer: Spurlautstärke, Filter, Sends, Limiter und Master.
- Visual Sync: publiziert Beat-/Takt-/Drop-Zustände an die Oberfläche.

Audio-Thread niemals durch Netzwerkzugriffe, UI-Arbeit oder Dateioperationen blockieren.

## 2. Musikalische Regeln

- Einheitliches BPM-Preset pro Set (z. B. 130–145 BPM für Hard-Techno; konfigurierbar).
- Loops in klaren Taktlängen (4, 8, 16 oder 32 Takte).
- Übergänge nur an musikalisch passenden Quantisierungspunkten.
- Samples nach Tonart/Harmonie gruppieren oder harmonisch neutrale Elemente verwenden.
- Lautheitsnormalisierung und Headroom vorsehen.
- Master-Limiter und Schutz vor Clipping integrieren.
- Ducking/Sidechain-ähnliche Dynamik für Bass/Kick prüfen.
- Bei Trackwechsel kurze, musikalische Crossfades verwenden.

## 3. Asset-Struktur

Beispiel:
```
audio/
  packs/
    industrial_hard_techno_01/
      pack.json
      kick/
      bass/
      hihat/
      percussion/
      synth/
      fx/
      transitions/
      fills/
```

Beispiel-Metadaten pro Audio-Asset:
- id
- category
- bpm
- key (optional)
- bars
- loop / one-shot
- duration
- loudness metadata (optional)
- license
- source
- allowed_for_streaming
- tags
- compatible_asset_groups

## 4. Event-Mapping

Konfiguration statt fest codierter Logik:
- Eventtyp (follow / gift / gift tier / other supported event)
- optional Geschenk-ID oder Ereigniswert
- Aktion (unlock layer / trigger fill / trigger transition / accent / variation)
- Quantisierung (next beat / next bar / next 4 bars / next 8 bars)
- Dauer (falls temporär)
- Cooldown / Rate Limit
- Priorität

Wenn viele Events gleichzeitig eintreffen:
- Events puffern und bündeln.
- Keine unkontrollierte Überlagerung.
- Maximalzahl gleichzeitiger Aktionen begrenzen.
- Größere musikalische Übergänge priorisieren.
- Doppelte/replayed Events erkennen, soweit Eventquelle IDs bereitstellt.

## 5. Autopilot-Arrangement

Ein mögliches Set-Schema:
- Intro / Grundbeat
- Groove A
- Groove B
- Variation
- Break
- Build-up
- Drop
- After-drop groove
- nächster Abschnitt

Das Schema soll datengetrieben und konfigurierbar sein. Der Autopilot muss unabhängig von Zuschaueraktionen weiterlaufen.

## 6. Lizenzierung

Nur selbst produzierte, beauftragte oder ausdrücklich für kommerzielles Streaming und Live-Ausspielung lizenzierte Sounds verwenden. Lizenzinformationen pro Asset speichern. Keine Annahme, dass ein Sample „kostenlos“ automatisch für TikTok-Livestreams erlaubt ist.
