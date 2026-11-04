# LÄRMBATZ – Korrekturen am Audio-Prototyp

## Umgesetzte Korrekturen

- `app/src/main/java/de/laermbatz/app/audio/AudioPlaybackService.kt`
  - Audio-Fokusgewinn setzt die Wiedergabe kontrolliert fort.
  - Temporärer Fokusverlust pausiert und meldet den Status; permanenter Fokusverlust fährt Stream und Foreground-Service kontrolliert herunter.
  - Der Stop-Rampen-Callback wird gespeichert und bei einem Neustart entfernt.
  - Ein bereits laufender Stream löst keine unnötigen neuen Fokusrequests aus.
  - Audiozustand und Statusmeldung sind abfragbar und werden bei Zustandsänderungen aktualisiert.
- `app/src/main/java/de/laermbatz/app/MainActivity.kt`
  - Die Activity liest beim erneuten Anzeigen den tatsächlichen Servicezustand.
  - Auf Android 13+ wird vor dem Start `POST_NOTIFICATIONS` zur Laufzeit angefragt; bei Ablehnung erscheint ein Hinweis.
  - WAV-Ersatz wird erst nach Annahme durch die native Audio-Engine in die persistierte Datei übernommen; eine Ablehnung löscht den vorherigen gespeicherten Loop nicht.
- `app/build.gradle.kts`
  - NDK `26.1.10909125` und CMake `3.22.1` sind für reproduzierbare Builds festgelegt.
- `cloud-build.sh` und `CLOUD_BUILD.md`
  - Reproduzierbarer Aufruf für Unit-Tests und Debug-APK-Build ergänzt.
- Der lokale Audio-MVP umfasst zusätzlich WAV-Import, sechs Oboe-Mixer-Spuren, persistente Lautstärke je Spur, Low-pass, BPM-synchrones Echo und Reverb-Send, Beat-Clock, Arrangement-Autopilot sowie automatische BPM-/Taktquantisierung mit WSOLA-Pitch-Lock. FX-Zustand wird mit der Track-Instanz ersetzt, damit neue WAVs keine alten Echo-/Hall-Tails erben; Level und FX-Regler sind während eines WAV-Ersatzes deaktiviert.

## Verifikation

Der finale Aufruf `./cloud-build.sh` lief am 4. November 2026 mit dem quantisierten WSOLA-/Mixer-FX-MVP erfolgreich durch (`BUILD SUCCESSFUL`). Der JVM-Testbericht weist **16 Tests, 0 Fehlschläge, 0 Fehler und 0 übersprungene Tests** aus. Zusätzlich bestehen neun native DSP-Assertions: 440-Hz-Pitch bei Mono/Stereo und 0,8×/1,25×, Impuls-/Beat-Abstand am Loop-Wrap, Low-pass-Absenkung, Beat-Delay-Echos und Reverb-Tail. `:app:assembleDebug` erzeugte die APK `de.laermbatz.app`, Version `0.1.0`, `minSdk 27`, `targetSdk 34`, mit nativer Bibliothek für `arm64-v8a`. Die APK-Signaturprüfung mit Android `apksigner` war erfolgreich (Debug-Schlüssel, APK Signature Scheme v2); elf JNI-Exports einschließlich `nativeSetTrackEffects` sind vorhanden.

SHA-256 der gebauten Debug-APK: `9effc5e8d7625fda6323fc515c993a6f318497167cacca295d9341f2bfa43748`. Dateigröße: 10.619.635 Byte.

Der Cloud-Host läuft auf Linux ARM64; die Android-x86_64-Buildwerkzeuge wurden für diesen Build über QEMU-user/binfmt ausgeführt. Das APK ist **debug-signiert**, nicht als Release-/Play-Store-Artefakt freigegeben. Ein Test auf einem physischen Android-Gerät – insbesondere Audio-Fokuswechsel, Hintergrundbetrieb und Dauerbetrieb – steht weiterhin aus. Die APK wird separat ausgeliefert; sie ist nicht in das Quell-ZIP eingebettet.
