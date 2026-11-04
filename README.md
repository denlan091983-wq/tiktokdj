# LÄRMBATZ — Techno-Show-Mixer und Radio

Dieses private Repository bündelt die aktuellen Projektquellen für den **LÄRMBATZ Techno-Show-Mixer**, **LÄRMBATZ Radio** und das Freesound-Loopverwaltungswerkzeug. „TikTokDJ“ ist nur der Repositoryname; es handelt sich nicht um ein TikTok-Produkt und die App liest keinen TikTok-LIVE-Ton aus.

## Inhalt

- [`laermbatz_audit/`](laermbatz_audit/) — Android-App (Kotlin/Jetpack Compose und nativer Oboe/C++-Audiomixer), Tests, Buildskripte und Audit-Roadmap.
- [`larmradio/`](larmradio/) — React-/Express-/tRPC-Website mit Managed-MySQL-Schema, Studio-Upload, einzeln bestätigter Veröffentlichung, Audioarchiv und statusgeprüften Stream-/Download-Proxys.
- [`LARMBATZ_freesound_auto_download.py`](LARMBATZ_freesound_auto_download.py) — CLI zur Freesound-API-/lokalen Loopverwaltung, Analyse und SQLite-Indizierung.
- [`artifacts/Laermbatz-debug.apk`](artifacts/Laermbatz-debug.apk) — geprüfter ARM64-Debug-Build für privaten Sideload-Test; SHA-256 steht in [`artifacts/SHA256SUMS.txt`](artifacts/SHA256SUMS.txt).

## Android-Build

Für einen lokalen Build Android SDK 34 und NDK 26.1 installieren, `local.properties` lokal anlegen und anschließend im Android-Verzeichnis ausführen:

```bash
cd laermbatz_audit
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Der mitgelieferte Debug-Build ist Version `0.1.0`, `minSdk 27`, `targetSdk 34`, `arm64-v8a` und debug-signiert. Er ist **nicht Play-Store-releasefähig**; vor einem Store-Release fehlen unter anderem Target-API-36-Migration und Release-Signierung. Die `cloud-build.sh` enthält den Buildablauf der bisherigen Cloud-Umgebung; lokale Pfade gegebenenfalls anpassen.

## Radio-Website

Die Website ist im zugehörigen Manus-WebDev-Projekt entwickelt worden. `larmradio/` enthält Quellcode und Lockfile, aber keine Plattform-Secrets oder produktiven Datenbankinhalte. Ein lokaler Start braucht die passende Node-/pnpm-Version und eine eigene, sichere Serverkonfiguration; Zugangsdaten gehören in lokale, ignorierte `.env`-Dateien oder einen Secret-Manager — niemals in Git.

Die Website ist derzeit **nicht öffentlich veröffentlicht**. Das Live-Radio meldet offline, bis ein unterstützter App-Master-Ingest verfügbar ist. Die Plattform lehnte `always_on` in diesem Projekt mit `requires_membership` ab; es wurde keine Hostingänderung gespeichert und nichts deployed. TikTok-Scraping, Cookies und undokumentierte Schnittstellen sind ausgeschlossen.

## Freesound und Audiodaten

Das Python-Werkzeug und die App verlangen eigene Freesound-Zugangsdaten, soweit die jeweilige API-Nutzung sie voraussetzt. Es wurden keine API-Schlüssel, OAuth-Tokens, `.env`-Dateien oder GitHub-Credentials in dieses Repository aufgenommen. Vor einer Weitergabe/Monetarisierung für jeden Loop die Lizenz und vorgeschriebenen Credits prüfen.

**Nicht enthalten:** persönliche Mix-Aufnahmen, private Android-Bibliotheken, heruntergeladene Samples, lokale/verwaltete Datenbankexporte, Server-Secrets sowie automatisch erzeugte Manus-Netzwerk-/Diagnoselogs. Im verwendeten Projektarbeitsverzeichnis lagen keine Audioaufnahmen oder SQLite-Datenbankdateien zum Übertragen vor. Das Weglassen schützt persönliche Daten und verhindert, dass Sample-Rechte ungeprüft weiterverteilt werden.

## Prüfnachweis

Der zuletzt geprüfte Android-Build bestand 42 JVM-Unit-Tests sowie den nativen SPSC-Ringbuffer-Smoke-Test und 11 DSP-/Arrangement-Smokes. TypeScript-Check, 12 Web-Tests, Produktionsbuild, Health-Route und Seiten-Routenmanifest der Website waren erfolgreich. Automatisierte Prüfungen ersetzen weder Android-Gerätetests noch die noch ausstehende öffentliche Website-Veröffentlichung.
