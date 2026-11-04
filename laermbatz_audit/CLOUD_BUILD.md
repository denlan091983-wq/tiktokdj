# Cloud-Build für LÄRMBATZ

## Reproduzierbarer Buildaufruf

Im Projektordner ausführen:

```bash
./cloud-build.sh
```

Das Skript führt den hostseitigen C++17-DSP-/Autopilot-Smoke-Test, danach `:app:testDebugUnitTest` und anschließend `:app:assembleDebug` aus. Es erwartet Android SDK Platform 34, Build Tools 34.0.0, NDK 26.1.10909125, CMake 3.22.1 und JDK 17. Die Android-/NDK-/CMake-Versionen sind im App-Modul gepinnt. Der ARM64-Cloudhost benötigt für einige x86_64-Buildwerkzeuge QEMU-user/binfmt.

## Letzter verifizierter Audit-Build

Am 4. November 2026 lief `./cloud-build.sh` auf dem Quellstand mit lokaler Masteraufnahme mit **BUILD SUCCESSFUL** durch. Es bestanden **42 JVM-Tests** ohne Fehlschläge, Fehler oder übersprungene Tests. Die nativen C++17-Prüfungen für den aufnahmefähigen SPSC-Ringbuffer und die DSP-/Autopilot-Kette bestanden; der native DSP-Smoke umfasst **11 DSP-/Arrangementprüfungen**, darunter eine Regression für extreme Float-, NaN- und Infinity-Eingaben. Diese hostseitigen Prüfungen sind keine Android-Instrumentierungs- oder Gerätetests.

Die APK ist `de.laermbatz.app`, Version `0.1.0` / Code `1`, `minSdk 27`, `targetSdk 34` und enthält ausschließlich `arm64-v8a`. `apksigner verify` war erfolgreich; die Datei ist debug-signiert mit APK Signature Scheme v2. Die Dateigröße beträgt **10.775.509 Byte**. SHA-256:

`3df983559d59eb8cad56bd6ef91211206d6a6d32d568e8b0a508950bba81f4f0`

Der verbindliche Buildpfad ist [`app/build/outputs/apk/debug/app-debug.apk`](app/build/outputs/apk/debug/app-debug.apk); die geprüfte, separat bereitgestellte Repositorykopie liegt unter [`../artifacts/Laermbatz-debug.apk`](../artifacts/Laermbatz-debug.apk). Die SHA-256-Prüfsumme steht in `../artifacts/SHA256SUMS.txt`.

Das bereinigte Gesamt-Quellarchiv wurde separat erzeugt. Dieses Repository enthält die Android-, Webradio- und Python-Quellen samt Tests/Dokumentation direkt; Zugangsdaten, persönliche Aufnahmen, Datenbankexporte, Caches und Buildausgaben sind ausgeschlossen.

## Nicht abgenommen

Das Buildumfeld enthielt kein `adb`; deshalb konnte kein Telefon verbunden oder die APK installiert werden. Oboe-/USB-Audioausgabe, Audiofokus, App-Wechsel, Hintergrundbetrieb, Temperatur, Latenz/Xruns, mindestens 30-minütiger Betrieb, Freesound-Live-API-Flows und TikTok-LIVE-Ausgabe sind nicht auf einem physischen Gerät getestet. Es wurden in diesem Audit keine echten Freesound-API-Aufrufe ausgeführt.

Dies ist eine **Debug-APK**, kein release-signiertes oder Play-Store-fähiges Paket. `targetSdk 34` muss vor einer Play-Einreichung auf die dann geforderte API-Stufe aktualisiert werden. Google Play verlangt seit 31. August 2026 grundsätzlich API 36 für neue Apps und Updates [1]. Der Audit-Build meldete außerdem eine harmlose SDK-XML-Versionierungswarnung (ein Tool versteht XML bis Version 3, ein SDK-Paket nutzt Version 4); der Build war trotzdem erfolgreich.

## Quellen

[1]: https://developer.android.com/google/play/requirements/target-sdk "Google Play target API level requirements"
[2]: https://developer.android.com/tools/sdkmanager "Android SDK Package Manager"
[3]: https://developer.android.com/studio/projects/install-ndk "Install and configure the NDK and CMake"
