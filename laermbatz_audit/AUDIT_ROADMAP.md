# LÄRMBATZ – Codeaudit und Roadmap bis zum ersten LIVE-Test

**Stand: 5. November 2026**<br>
**Ergebnis:** Der Android-Audio-MVP einschließlich lokaler 60-Minuten-Masteraufnahme (AAC, 192 kbit/s) und lokalem Import geprüfter Drive-Samplepacks ist baubar und besteht die automatisierten Prüfungen. Er ist aber noch keine abgenommene TikTok-LIVE-App: Es fehlen ein physischer Gerätetest, ein bestätigter Audio-/Video-Ausgabeweg und ein autorisierter Live-Eventfeed.

## Was geprüft und behoben wurde

Der Audit umfasste die Android-Produktions- und Testdateien, nativen Mixer/DSP, den Freesound-Python-Importer samt SQLite/DSP, Buildkonfiguration und die maßgeblichen Statusdokumente. `adb` war im Buildumfeld nicht verfügbar; ein Android-Gerät konnte daher nicht angeschlossen werden. Die automatisierten Prüfungen belegen Software-/Buildverhalten, nicht die reale Audioqualität eines bestimmten Telefons.

Im nativen Mixer wurden endliche Float-Werte außerhalb des normalen Audiobereichs als Ursache möglicher DSP-Überläufe behoben: die JNI-Ladegrenze verwirft weiterhin NaN/Infinity und begrenzt sonstige Samples auf `[-1, 1]`; Effektkette und Masterausgabe haben zusätzliche Failsafes. Ein Regressionstest speist Maximalwerte, NaN und Infinity ein und verlangt endliche Ausgabe.

Für Android und das Python-Tool werden Quellen-/Lizenzmetadaten nun vor dem finalen WAV-Commit atomar gespeichert. Damit kann ein Absturz nicht mehr einen finalisierten Freesound-Loop ohne Sidecar-Provenienz hinterlassen. Vorhandene gleichnamige Freesound-Dateien werden nicht still überschrieben; unbekannte Lizenznachweise sperren das Laden aus der App-Bibliothek. Die Compose-Suche zeigt nun alle 25 Ergebnisse der geladenen Freesound-Seite statt nur die ersten acht. Bei leeren Treffern versucht Auto-Set geordnet breitere, weiterhin lizenzgefilterte Suchbegriffe. CC-BY-Sounds bleiben opt-in und verlangen sichtbare Credits. Der eigene JSON-Parser begrenzt die Verschachtelungstiefe.

Im Python-Tool stoppen beschädigte UTF-8-Sidecars den Bibliothekslauf nicht mehr. Nicht-endliche `--delay`-Werte wie `nan` oder `inf` werden abgewiesen. Die lokalen Import- und Freesound-Commitpfade wurden mit synthetischen Dateien bzw. gemockter API offline getestet; es gab dabei keine echten Freesound-Anfragen. Die Freesound-API-Dokumentation beschreibt `license` als Werte wie `Creative Commons 0` und `Attribution`; der bestehende Lizenz-Allowlist-Filter passt zu diesem dokumentierten Feldformat und wurde nicht unnötig erweitert [5]. Die neue Suche fordert CC0 standardmäßig; CC BY wird erst nach ausdrücklicher Einstellung freigegeben.

Widersprüchliche Einstiegsdokumente wurden aktualisiert. Der TikTok-Status erklärt jetzt korrekt, dass Internet für Freesound benötigt wird, aber kein TikTok-LIVE-Eventverkehr existiert.

## Lokale Ein-Stunden-Aufnahme

Der Oboe-Callback kopiert den finalen Stereo-Master nach Mixer-/Effektkette in einen vorallokierten, atomaren SPSC-Ring ohne Sperre, JNI-Aufruf oder Speicherallokation im Audiothread. Ein separater Service-Worker wandelt die Samples mit Android `MediaCodec` in AAC-LC mit 192 kbit/s um und finalisiert einen M4A-Container per `MediaMuxer`. Die Aufnahme wird nach spätestens 3600 Sekunden gestoppt, lokal in `Music/LÄRMBATZ` abgelegt und erst danach im MediaStore sichtbar gemacht. Fehler und Pufferüberläufe brechen ab, statt eine unvollständige Datei als fertig zu markieren. Implementierung: `app/src/main/cpp/master_recording_ring.h`, `app/src/main/java/de/laermbatz/app/audio/LocalMasterMixRecorder.kt` und `AudioPlaybackService.kt`.

## Import der Drive-Samplepacks

Der lokale Android-Dateidialog akzeptiert bis zu acht ausgewählte ZIPs pro Vorgang. Nur Archive mit einem der acht geprüften Namen im Pack-Katalog werden dekomprimiert. Der Import läuft im Hintergrund, prüft WAV-Header, Dateigröße, Duplikat-SHA-256 und freien Speicher; Grenzen sind höchstens 512 MiB komprimiert je Archiv, höchstens 4 GiB kumulativ dekomprimierte Daten pro Auswahl, 32 MiB je WAV und 2,5 GiB gesamte lokale Loop-Bibliothek. WAVs erhalten Anbieter-, Pack-, Lizenz-, Credit- und Quellmetadaten, werden automatisch kategorisiert und sind danach im Auto-Set auswählbar. Unbekannte Archive werden übersprungen. Die Archive/Roh-WAVs werden nicht in APK, GitHub, Quellarchiv oder Radio-Website gebündelt. Öffentliche Live-Stream-Rechte sind für die geprüften Gratis-Packs nicht ausdrücklich bestätigt; LucidSamples verlangt Credit. Details und offizielle Links stehen in [SAMPLEPACK_LICENSE_NOTES.md](SAMPLEPACK_LICENSE_NOTES.md). Die Google-Drive-Dateiauswahl und Audioqualität sind noch nicht auf einem Android-Gerät getestet.

## Verifizierter Build

Der abschließende Aufruf `./cloud-build.sh` war erfolgreich. Es bestanden **58 JVM-Tests** ohne Fehlschläge, Fehler oder übersprungene Tests sowie der native SPSC-Ringbuffer-Smoke und **11 DSP-/Arrangement-Smokes**, darunter der Float-/NaN-Schutztest. Der APK-Hash und die v2-Signatur wurden geprüft. Die APK ist `de.laermbatz.app` Version `0.1.0`, `minSdk 27`, `targetSdk 34`, nur `arm64-v8a`, debug-signiert und 10.879.563 Byte groß. SHA-256: `bb49812c1f029cb3f6250aa2f7d46e2468aaf584f25bf5d732cec4d4c1d7e6ad`.

Die [geprüfte Debug-APK](../artifacts/Laermbatz-debug.apk) ist zum privaten Sideloading auf kompatiblen ARM64-Geräten geeignet. Sie ist nicht release-signiert. Die genaue Buildprüfung steht in [CLOUD_BUILD.md](CLOUD_BUILD.md); der Implementierungsstand des Mixers steht in [MIXER_MVP_STATUS.md](MIXER_MVP_STATUS.md).

## LÄRMBATZ Radio – Website

Das Webradio-Projekt enthält ein Industrial-Techno-Dashboard, eine OAuth-geschützte Studio-Oberfläche, explizite Einzelveröffentlichungen sowie ein öffentliches Archiv mit statusgeprüften Stream- und Download-Proxy-Endpunkten. Der Upload wird serverseitig als AAC in M4A validiert; die tatsächliche Dateigröße und Präsentationsdauer werden aus Container-Metadaten gelesen, nicht vom Client übernommen. Die Owner-E-Mail ist als geschütztes Server-Secret für Development und Production hinterlegt. Die Website ist noch nicht veröffentlicht; das öffentliche Radio zeigt ehrlich „offline“, bis eine genehmigte Live-Audio-Ingest-Strecke eingerichtet ist. Der Versuch, Reserved Hosting für den permanenten Relay zu speichern, wurde mit `requires_membership` abgelehnt; es wurde kein Hostingmodus gespeichert oder deployed. TikTok-Ton wird nicht ausgelesen. Siehe [Website-Roadmap](../larmradio/TODO.md).

## Was noch vor einem echten LIVE fehlt

**1. Audio auf dem Zielgerät abnehmen.** Es war kein Telefon angeschlossen. Auf dem vorgesehenen Samsung-/Android-Gerät müssen WAV-Import und Restore, sechs Spuren, Effekte, BPM-Wechsel, Audiofokus, Routenwechsel, App-Wechsel, Bildschirm aus, Benachrichtigungsaktionen, Stopp/Not-Aus und mindestens 30 Minuten Dauerbetrieb geprüft werden. Dabei XRuns/Aussetzer, Lautheit/Clipping, Akkubelastung und Temperatur protokollieren. Der hostseitige DSP-Smoke kann diese Tests nicht ersetzen.

**2. Einen realen Stream-Ausgabeweg nachweisen.** TikTok dokumentiert, dass LIVE Studio den Android-Bildschirm per Cast empfangen kann [2]. Das bestätigt nicht, dass der interne Oboe-Audiomix dieser App im konkreten Telefon-/Studio-Setup zuverlässig beim Stream ankommt. Zuerst die LIVE-Studio-Vorschau und Audioaufnahme testen; falls die interne Ausgabe nicht sauber übertragen wird, eine class-compliant USB-C-Audioschnittstelle oder einen externen Mixer und ein separates Streaminggerät prüfen. Die App selbst ist derzeit Mixer/Controller, kein eigenständiger TikTok-Broadcaster.

**3. Die Zuschaueransicht fertigstellen.** Die heutige Compose-Oberfläche ist vorrangig Bedien- und Mixeroberfläche. Für einen professionellen Stream fehlen noch eine eigene 9:16-Bühnenansicht, eine ruhige beat-synchrone Visualisierung, klare Arrangement-/Trackanzeigen und ein Check, dass im Bildschirm-Cast keine privaten Benachrichtigungen oder Zugangsdaten sichtbar werden.

**4. TikTok-Ereignisse nicht versprechen.** TikToks öffentlich dokumentierte Webhook-Ereignisse umfassen unter anderem `authorization.removed`, Video-Upload-/Publish-Status und Data-Portability-Benachrichtigungen. Gift- und Follow-Ereignisse eines laufenden LIVE sind dort nicht aufgeführt [1]. Das schließt einen individuellen Vertrag oder Partnerweg nicht aus, belegt aber keinen allgemein nutzbaren öffentlichen Eventfeed. Bis TikTok oder ein autorisierter Partner genau den Zugang für Konto, Region und App-Zweck bestätigt, bleibt `TikTokLiveEventSource` `UNAVAILABLE`. Der Autopilot kann ohne diese Events laufen; Scraping, Cookies und undokumentierte WebSocket-Wege bleiben ausgeschlossen.

**5. Rechte und Konto prüfen.** „Gratis“ bedeutet nicht automatisch für öffentliche oder monetarisierte Streams freigegeben. Vor dem ersten Stream alle Loops und erforderliche CC-BY-Credits prüfen und gegenüber dem Publikum zugänglich machen. Für die Drive-Samplepacks sind Rohdateien ausdrücklich vom Repository-/APK-Export ausgeschlossen; öffentliche Live-Rechte sind ungeklärt. TikTok weist außerdem darauf hin, dass LIVE-Funktionen nicht überall verfügbar sind und lokale Mindestanforderungen gelten; Konto-/Regionsberechtigung vor dem Test im TikTok-Konto prüfen [4].

**6. Für Store-Verteilung releasefähig werden.** Die aktuelle APK ist debug-signiert, hat keine Release-Signaturkonfiguration und targetet API 34. Seit 31. August 2026 müssen neue Google-Play-Apps und Updates grundsätzlich Android 16 / API 36 oder höher targeten [3]. Vor einer Play-Einreichung sind SDK-/Target-Migration, Foreground-Service-/Audiofokus-Regressionstests, Release-Signierung, Versionierung, Privacy/Data Safety, Lizenzhinweise und ein reproduzierbarer CI-Build nötig. Für private Installation ist API 36 keine Voraussetzung; sie ist hier eine Play-Store-Anforderung.

## Priorisierte Roadmap

**P0 – Sicherer Audio-/Hardwaretest:** APK auf dem Zielgerät installieren und die obige Gerätematrix ausführen. Dabei USB-C-Kopfhörer oder Audiointerface als tatsächlichen Output mitprüfen. Erst fortfahren, wenn Dauerbetrieb, Stop, Fokuswechsel und Pegel sicher sind.

**P0 – TikTok-Ausgabeweg:** LIVE-Studio-Cast-/Preview testen. Audio separat verifizieren, nicht aus dem erfolgreichen Bildschirm-Cast ableiten. Bei Bedarf USB-Audiointerface/Mixer und zweites Gerät einsetzen. Erst dann einen echten Stream im zulässigen Konto-/LIVE-Modus planen.

**P1 – Stream-Szene:** Zuschaueransicht getrennt vom Controller bauen und auf TikTok-Hochformat previewen. Keine Fake-Zuschauerdaten, Geschenke oder Follower im Livebetrieb.

**P1 – Auto-Set und Rechte:** Drive-Pack-Import und Auto-Set mit den echten Archiven auf dem Gerät prüfen; danach Freesound-Credentials/OAuth testen, einschließlich Suchlauf/Fallbacks, Download, Deduplizierung, Abbruch, Speicherlimit und Attribution. Bis dahin ist der Pack-Importer nur mit Tests/Synthese-ZIPs belegt und es gab keine echten Freesound-API-Anfragen; der Autopilot soll bei fehlender Netzwerkverbindung weiterlaufen.

**P1 – Projekt-/Release-Härtung:** Der Android-Quellstand liegt nun in einem privaten GitHub-Monorepo; vor weiteren Änderungen aus einer alten Sandbox-Arbeitskopie ist ein sauberer Clone-/Syncablauf festzulegen. Android auf die erforderliche Ziel-API migrieren, Signierschlüssel getrennt und sicher verwalten, Dependencies/Gradle-Wrapper verifizieren und CI so einrichten, dass JVM- und native Smoke-Tests jede Änderung blockieren können. Der native Test läuft derzeit über `cloud-build.sh`, nicht über `./gradlew test` allein.

**P2 – Event-Automation nur nach Freigabe:** Bei bestätigter offizieller Schnittstelle Event-Queue mit IDs/Deduplizierung, Cooldowns, Taktquantisierung, Fake-/Replay-Tests, Bursts, Disconnect-Fallback und Datenschutz implementieren. Ohne bestätigte Schnittstelle bleibt der Produktumfang ein autonomer Musik-Autopilot ohne echte TikTok-Reaktionen.

## Quellen

[1]: https://developers.tiktok.com/doc/webhooks-events/ "TikTok for Developers: dokumentierte Webhook-Ereignisse"
[2]: https://www.tiktok.com/live/studio/help/article/Get-started-with-your-first-LIVE/Add-a-cast-source-to-share-your-phone-screen "TikTok LIVE Studio: Android-Bildschirm als Cast-Quelle"
[3]: https://developer.android.com/google/play/requirements/target-sdk "Google Play: Target API level requirements"
[4]: https://support.tiktok.com/en/live-gifts-wallet/tiktok-live/what-is-tiktok-live "TikTok Support: TikTok LIVE und Teilnahmevoraussetzungen"
[5]: https://freesound.org/docs/api/resources_apiv2.html "Freesound API v2: Sound license field"
