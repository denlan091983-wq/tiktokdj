# LÄRMBATZ – Schritt 1: Projektgerüst + Oboe-Testton

> Historische Ausgangsnotiz aus dem ursprünglichen Paket. Das Android-Projekt wurde inzwischen ergänzt und mit Gradle gebaut. Aktuelle Anleitung und Grenzen: `ANDROID_BUILD_README.md` und `TIKTOK_API_STATUS.md`. Ein Gerätetest steht weiterhin aus.

Ziel: Auf dem Galaxy A17 5G (SM-A176B) kommt ein Testton stabil und ohne Knacken.

## Bauen
1. Android Studio (aktuell) öffnen, Ordner `laermbatz` wählen, Gradle-Sync abwarten (NDK/CMake werden bei Bedarf nachgeladen).
2. Handy: Entwickleroptionen und USB-Debugging aktivieren, per USB verbinden.
3. Run ▶ auf dem Gerät. „Testton starten" drücken.

## Abnahme Schritt 1
- Ton startet und stoppt ohne Klick (20-ms-Rampe).
- 10 Minuten Dauerton ohne Aussetzer.
- App im Hintergrund / Bildschirm aus: Verhalten notieren (Foreground Service folgt in Schritt 6).

## Dateien
- app/src/main/cpp/audio_engine.cpp – Oboe-Stream, Sinus, Gain-Rampe
- app/src/main/cpp/CMakeLists.txt – Native Build, Oboe über Prefab
- app/.../AudioEngine.kt – JNI-Brücke
- app/.../MainActivity.kt – ein Button
- build.gradle.kts, app/build.gradle.kts, settings.gradle.kts, gradle.properties

## Einschränkungen
- NICHT gebaut oder getestet: Meine Umgebung hat kein Netz und kein Android SDK. Versionen (AGP 8.5.2, Kotlin 2.0.20, Oboe 1.9.0, Compose BOM 2024.09.00) sind aus meinem Wissen; Android Studio kann Updates vorschlagen.
- Nur arm64-v8a, minSdk 27.
- Kein Gradle-Wrapper enthalten (Android Studio legt ihn an, Gradle 8.7).
- Kein Design, keine Loops, kein Service.
