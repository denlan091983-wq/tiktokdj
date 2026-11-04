# LÄRMBATZ – Entwicklungsroadmap zur Live-Abnahme

**Stand:** 4. November 2026. Der auditierte Ist-Stand und die konkreten Befunde stehen in [`../AUDIT_ROADMAP.md`](../AUDIT_ROADMAP.md).

## Bereits umgesetzt, noch nicht live-abgenommen

- Android/Kotlin/Compose-Projekt mit nativer Oboe-Engine und WAV-Import.
- Sechs Spuren, BPM-/Takt-Schätzung, WSOLA-Time-Stretch, Spur-Effekte, 64-Takt-Autopilot und Fortschrittsanzeige.
- Freesound-API v2 mit Nutzer-Credentials, Lizenzfiltern, OAuth-Downloads, lokaler Kategorienbibliothek sowie Auto-Set und SQLite-/Metadatenverwaltung im begleitenden Python-Tool.
- Debug-Build, JVM-Tests und hostseitige native DSP-Smokes.
- TikTok-LIVE-Eventquelle bleibt bewusst `UNAVAILABLE`; kein Scraping, Reverse-Engineering oder Cookie-/Sessionzugriff.

## P0 – Erstes Ziel: sicherer Testbetrieb am Telefon

1. APK nur aus dem aktuellen Buildbericht installieren. Das Projekt wurde nicht auf einem physischen Telefon abgenommen; `adb devices` fand in der Buildumgebung kein angeschlossenes Android-Gerät.
2. WAV-Import/Restore, sechs Spuren, BPM-Wechsel, Effekte, Stop, Audiofokuswechsel, App-Wechsel, Bildschirm aus, Rotation und mindestens 30 Minuten Dauerbetrieb auf dem vorgesehenen Android-Gerät prüfen. Logs/Xruns, hörbare Aussetzer, Lautheit, Clipping, Akku und Temperatur festhalten.
3. Vor dem ersten Stream den Audioausgabeweg praktisch bestätigen. TikTok dokumentiert Android-Bildschirm-Cast in LIVE Studio. Das belegt nicht, dass der interne Oboe-Audiomix in der konkreten Geräte-/LIVE-Studio-Kombination zuverlässig übertragen wird [1]. Daher zuerst Aufnahme-/Previewtest und nötigenfalls eine class-compliant USB-C-Audioausgabe bzw. einen externen Audiomischer testen.
4. Eine klare „Not-Aus“-Bedienung, verlässliche Stummschaltung und sicheren Wiederanlauf nach Fokus-/Routenwechsel auf dem Zielgerät abnehmen.

**Abnahme:** Die Show läuft ohne TikTok-Ereignisse mindestens 30 Minuten synchron und ohne hörbare Aussetzer; der gewählte Video-/Audio-Ausgabeweg ist mit dem tatsächlichen TikTok-Setup nachgewiesen. Bis dahin ist ein öffentlicher Live-Einsatz nicht empfohlen.

## P1 – Stream-Bild und Show-Erlebnis

- Eine echte 9:16-Zuschaueransicht mit Warehouse-Bühne, beat-synchroner aber flackerfreier Visualisierung, Trackzuständen, Arrangementphase und lesbarer Statusanzeige bauen. Die heutige Compose-Oberfläche ist vorrangig ein Mixer-/Kontrollbildschirm.
- Stream-Status, Zuschauerzahl, Gifts und Follows nur anzeigen, wenn eine echte, bestätigte Quelle sie liefert. Bis dahin keine erfundenen Events/Namen; Demo-Daten deutlich als Simulation markieren.
- Control-/Mixeransicht von der Zuschaueransicht trennen. Controller muss Start/Pause/Stop, Master-Mute, BPM, Autopilotstatus und technische Diagnose schnell erreichbar halten.
- Bild-/Audio-Synchronität und Lesbarkeit im TikTok-LIVE-Studio-Preview prüfen; Android-Cast zeigt den ganzen Telefonbildschirm, daher Benachrichtigungen vermeiden und Anzeige vor dem Livegang kontrollieren [1].

## P1 – Musikrechte und automatisierte Library

- Nur eigene oder für öffentliche/monetarisierte Streams ausdrücklich geeignete Loops einsetzen. „Kostenlos“ ist keine pauschale Live- oder kommerzielle Nutzungserlaubnis.
- CC BY nur bei erhaltener Quelle und tatsächlich für Publikum erreichbarer Namensnennung verwenden; für einen einfachen Livebetrieb CC0 oder klar freigegebene Packs bevorzugen.
- Freesound-Zugang und API-Nutzungsbedingungen je eigenem Credential prüfen. App-Auto-Set ist nutzerinitiierter App-Flow, kein unbeaufsichtigter Cloud-Dienst.
- Automatische Suche/Downloads begrenzen, Duplikate verhindern und Lizenz-/Herkunftsnachweise mit jeder Datei erhalten.

## P1 – TikTok-Events: nur bei bestätigter Schnittstelle

TikToks öffentlich dokumentierte Webhook-Events umfassen aktuell unter anderem Account-Deautorisierung, Video-Upload-/Publish-Status und Data-Portability-Status; Gifts/Follows eines laufenden LIVE-Streams sind in der geprüften Referenz nicht als Eventtypen ausgewiesen [2]. Das ist keine Aussage über vertrauliche Partnerprogramme. Vor einer Integration braucht es eine für Konto, Region und App-Zweck schriftlich oder im Developer Portal bestätigte Schnittstelle und Eventfelder.

Bis dahin darf LÄRMBATZ als autonomer Autopilot-Mixer ohne Zuschauerevents getestet werden. Es dürfen keine inoffiziellen Scraper oder undokumentierten WebSocket-/Cookie-Methoden verwendet werden. Wenn eine offizielle Quelle später verfügbar ist, folgen Event-Queue, Deduplizierung, Cooldowns, Taktquantisierung, Fake-/Replay-Tests, Disconnect-Fallback und Datenschutzprüfung.

## P1 – Release/Verteilung

- Für private Installation kann die Debug-APK sideloaded werden; sie ist aber nicht release-signiert. Eine sichere Release-Signatur und eindeutige Artefakt-/Quellzuordnung fehlen noch.
- Für eine Google-Play-Einreichung muss die Ziel-API angehoben werden: Seit 31. August 2026 müssen neue Apps und Updates Android 16 / API 36 oder höher targeten (Ausnahmen sind produkt-/gerätebezogen) [3]. Das aktuelle Projekt targetet API 34. Beim Upgrade sind Foreground-Service-, Audiofokus-, Berechtigungs- und Oboe-Verhalten erneut zu testen.
- Release-Variante, private Signaturschlüsselverwaltung, Versionierung, R8-/ProGuard-Prüfung, Privacy/Data Safety und Lizenzhinweise, reproduzierbarer Build, Gradle-Wrapper-Prüfsumme, Dependency Verification/Locking und CI ergänzen.
- Projekt in ein Git-Repository mit nachvollziehbarem Release-Tag und Buildprovenienz überführen; in dieser Arbeitskopie existiert aktuell kein `.git`-Repository.

## Abnahme-Checkliste vor dem ersten Livegang

1. Zielgerät, TikTok-Konto und LIVE-Berechtigung in TikTok prüfen; LIVE-Verfügbarkeit ist konten-/regionsabhängig [4].
2. Alle verwendeten Loops und Credits vorab prüfen und eine schriftliche Liste der Live-Lizenzen aufbewahren.
3. Audioausgabeweg, Preview, Pegel, Sprach-/Systemaudio-Abwesenheit und Stummschaltung technisch testen.
4. Dauer- und Fokus-/Routenwechseltest am Gerät bestehen lassen.
5. Visuals so einrichten, dass keine privaten Benachrichtigungen, App-Dialoge oder Zugangsdaten im Cast erscheinen.
6. Autopilot-only-Show probieren. Gifts/Follows nicht als funktionsfähig bewerben, solange keine bestätigte Eventquelle implementiert ist.

## Quellen

[1]: https://www.tiktok.com/live/studio/help/article/Get-started-with-your-first-LIVE/Add-a-cast-source-to-share-your-phone-screen "TikTok LIVE Studio: Android-Bildschirm als Cast-Quelle"
[2]: https://developers.tiktok.com/doc/webhooks-events/ "TikTok for Developers: dokumentierte Webhook-Ereignisse"
[3]: https://developer.android.com/google/play/requirements/target-sdk "Google Play: Target API level requirements"
[4]: https://support.tiktok.com/en/live-gifts-wallet/tiktok-live/what-is-tiktok-live "TikTok Support: TikTok LIVE und Teilnahmevoraussetzungen"
