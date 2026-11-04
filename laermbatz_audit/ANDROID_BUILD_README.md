# LÄRMBATZ Android – aktueller Audio-MVP

## Implementierter Umfang

- Android-App `de.laermbatz.app` mit Kotlin/Compose und Foreground-Audiowiedergabeservice.
- WAV-Import über den Android-Systempicker; RIFF/WAVE PCM 16/24/32 Bit oder IEEE Float 32 Bit, Mono/Stereo, 8–192 kHz, mit Datei-, Sample- und Formatprüfungen.
- Sechs native Oboe-Mixer-Spuren: KICK, BASS, HI-HAT, PERCUSSION, SYNTH und EFFECTS. Die Lautstärke kann je Spur zwischen 0 und 125 % geregelt werden; Reglerwerte werden auf dem Gerät gespeichert.
- Einklappbare Effektsektion je geladener Spur: Low-pass 200 Hz–20 kHz, BPM-synchrones Feedback-Delay (1/8-, 1/4- oder 1/2-Note, Wet und Feedback je 0–80 %) und Reverb-Send 0–80 %. FX sind standardmäßig aus.
- Die Track-Instanz besitzt ihren DSP-Zustand; Austausch oder Entfernen einer WAV übernimmt keine alten Delay-/Reverb-Tails. Während des WAV-Ersatzes sind Spurregler und Entfernen gesperrt.
- Automatische Offline-Schätzung des Quell-BPM (60–180), Taktzahl (1–64) und BPM-/Takt-Synchronisierung. Neue Spuren starten beim Live-Import am nächsten Takt.
- Pitch-preserving WSOLA im Oboe-Echtzeitcallback: korrelationsbasierte 10-ms-Hops, keine JNI-Aufrufe, Locks oder Heap-Allokationen im Callback. Die Delay-Puffer werden vor Start beziehungsweise vor Veröffentlichung einer neuen Spur vorbereitet.
- Kürzere Clips fallen sichtbar auf Varispeed zurück. Sehr große Tempoverschiebungen und bestimmte Transienten können WSOLA-Artefakte erzeugen. Sample 0 gilt als Downbeat; Halb-/Doppeltempo-Mehrdeutigkeit wird markiert.
- TikTok-LIVE-Follow-/Gift-Events sind nicht angebunden; der Adapter bleibt `UNAVAILABLE`.

## Voraussetzungen und Build

Das Modul pinnt Android SDK Platform 34, Build Tools 34.0.0, NDK 26.1.10909125 und CMake 3.22.1. JDK 17 oder neuer und Gradle Wrapper 8.7 werden benötigt. In der Cloud-Sandbox:

```bash
./cloud-build.sh
```

Das Skript führt zuerst den nativen WSOLA-/Effekt-Smoke-Test aus, danach `:app:testDebugUnitTest` und `:app:assembleDebug`. Die APK entsteht unter `app/build/outputs/apk/debug/app-debug.apk`.

## Verifizierter Cloud-Build (4. November 2026)

- Der aktuell freigegebene Build-Status, die exakte Testzahl sowie Artefaktgröße und SHA-256 stehen ausschließlich in [`CLOUD_BUILD.md`](CLOUD_BUILD.md).
- Der native Host-Smoke prüft WSOLA-Pitch/Transienten, Low-pass, BPM-Delay, Reverb, finite DSP-Ausgabe und Arrangementgrenzen. Er ist ein synthetischer DSP-Test, kein Android-Gerätetest.
- Paket `de.laermbatz.app`, Version `0.1.0`, `minSdk 27`, `targetSdk 34`, native ABI `arm64-v8a`.
- `apksigner verify` erfolgreich (Debug-Signatur, APK Signature Scheme v2); elf `AudioEngine`-JNI-Exports einschließlich `nativeSetTrackEffects` vorhanden.
- Verwende für Installation und Prüfung nur die im Abschnitt „Finales Artefakt“ von [`CLOUD_BUILD.md`](CLOUD_BUILD.md) benannte APK. Alte, gleichnamige oder kopierte APKs können einen älteren Stand enthalten.

Die APK ist **debug-signiert und kein Release-/Play-Store-Artefakt**. Auf einem physischen Android-Gerät wurde sie noch nicht getestet. Audioqualität und Latenz bei sechs Spuren, Filter-/Delay-/Reverb-Klang, Audiofokuswechsel, Hintergrundbetrieb, thermische Last und längere Wiedergabe müssen am Zieltelefon abgenommen werden. Google Play verlangt seit 31. August 2026 grundsätzlich Android 16/API 36 als Ziel für neue Apps und Updates; dieses Modul targetet API 34 und muss vor einer Play-Einreichung migriert und erneut getestet werden: https://developer.android.com/google/play/requirements/target-sdk.

## Grenzen und nächste Schritte

Die BPM-Erkennung ist heuristisch; besonders syncopierte oder nicht-perkussive Loops können falsch erkannt werden. Bei Mehrdeutigkeit wählt der MVP den schnelleren Kandidaten. WSOLA erhält die Grundtonhöhe im nativen Sinus-Smoke-Test, ersetzt aber keinen Phase-Vocoder; Transienten, Rauschen und extreme Zeitdehnung können hörbar verändert werden. Pitch-preserving Formant-Korrektur, manuelle Quell-BPM-/Taktkorrektur und Downbeat-/Stilleerkennung bleiben offen.

Die integrierten Effekte sind eine leichte Echtzeit-MVP-Kette und benötigen Hör-/Lasttests auf Zielgeräten. Ein getesteter Cloud-Build beweist keine xrunsfreie Wiedergabe auf allen Android-Geräten.

Es gibt keinen bestätigten TikTok-LIVE-Eventfeed für Follow-/Gift-Ereignisse in den geprüften öffentlichen TikTok-Unterlagen. Deshalb werden keine Scraper, Session-Cookies oder undokumentierten WebSocket-Wege verwendet. Eine echte Integration erfordert eine schriftliche, für App/Konto/Region zulässige Freigabe und dokumentierte Schnittstelle; siehe `TIKTOK_LIVE_API_STATUS_2026.md`.
