# Cloud-Build für LÄRMBATZ

## Reproduzierbarer Buildaufruf

Im Projektordner ausführen:

```bash
./cloud-build.sh
```

Das Skript führt den hostseitigen C++17-DSP-/Autopilot-Smoke-Test, danach `:app:testDebugUnitTest` und anschließend `:app:assembleDebug` aus. Es erwartet Android SDK Platform 34, Build Tools 34.0.0, NDK 26.1.10909125, CMake 3.22.1 und JDK 17. Die Android-/NDK-/CMake-Versionen sind im App-Modul gepinnt. Der ARM64-Cloudhost benötigt für einige x86_64-Buildwerkzeuge QEMU-user/binfmt.

## Letzter verifizierter Build

Am 6. November 2026 lief `./gradlew testDebugUnitTest assembleDebug` nach der Erhöhung des ZIP-Importlimits von 8 auf 20 erfolgreich durch. Das kumulative Dekompressionslimit bleibt bei 4 GiB pro Auswahl. Es bestanden **76 JVM-Tests** ohne Fehlschläge, Fehler oder übersprungene Tests. Die nativen C++17-Smokes für SPSC-Ringbuffer, WSOLA, DSP-Effekte und Autopilot bestanden ebenfalls. Dies sind hostseitige Softwareprüfungen, keine Android-Instrumentierungs- oder Gerätetests.

Die APK ist `de.laermbatz.app`, Version `0.1.1` / Code `2`, `minSdk 27`, `targetSdk 34` und enthält ausschließlich `arm64-v8a`. `apksigner verify` war erfolgreich; die Datei ist debug-signiert mit APK Signature Scheme v2. Die Dateigröße beträgt **10.785.645 Byte**. SHA-256:

`45b07fb165176a52719d4f11d77df98bab0253b08dce8201e8eccd97da14578d`

Das aktuelle Artefakt ist [LARMBATZ_ZipLoopLibrary_20ZIP_debug.apk](../artifacts/LARMBATZ_ZipLoopLibrary_20ZIP_debug.apk); der lokale Gradle-Buildpfad lautet `app/build/outputs/apk/debug/app-debug.apk`. Prüfsummen stehen in `../artifacts/SHA256SUMS.txt`.

Das [Gesamt-Quell- und APK-Archiv](../Laermbatz_Quellcode.zip) enthält Android-, Webradio- und Python-Quellen, Tests/Dokumentation sowie den aktuellen Debug-Build. Drive-ZIP-Archive und rohe Loop-WAVs sind wegen der Rohdaten-Weitergaberechte nicht enthalten; Details stehen in [SAMPLEPACK_LICENSE_NOTES.md](SAMPLEPACK_LICENSE_NOTES.md). Secrets, persönliche Aufnahmen, Datenbankexporte, `node_modules`, Gradle-/CMake-Caches und weitere generierte Buildausgaben sind ausgeschlossen.

## Nicht abgenommen

Das Buildumfeld enthielt kein `adb`; deshalb konnte kein Telefon verbunden oder die APK installiert werden. Oboe-/USB-Audioausgabe, Audiofokus, App-Wechsel, Hintergrundbetrieb, Temperatur, Latenz/Xruns, mindestens 30-minütiger Betrieb, Freesound-Live-API-Flows und TikTok-LIVE-Ausgabe sind nicht auf einem physischen Gerät getestet. Es wurden in diesem Audit keine echten Freesound-API-Aufrufe ausgeführt.

Dies ist eine **Debug-APK**, kein release-signiertes oder Play-Store-fähiges Paket. `targetSdk 34` muss vor einer Play-Einreichung auf die dann geforderte API-Stufe aktualisiert werden. Google Play verlangt seit 31. August 2026 grundsätzlich API 36 für neue Apps und Updates [1]. Der Audit-Build meldete außerdem eine harmlose SDK-XML-Versionierungswarnung (ein Tool versteht XML bis Version 3, ein SDK-Paket nutzt Version 4); der Build war trotzdem erfolgreich.

## Quellen

[1]: https://developer.android.com/google/play/requirements/target-sdk "Google Play target API level requirements"
[2]: https://developer.android.com/tools/sdkmanager "Android SDK Package Manager"
[3]: https://developer.android.com/studio/projects/install-ndk "Install and configure the NDK and CMake"
