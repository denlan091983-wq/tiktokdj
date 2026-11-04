# LÄRMBATZ Radio — Umsetzungsstand

Der WebDev-Blueprint und die Projektwahl React/Express/tRPC mit Managed MySQL sind bestätigt; Server und Datenbank sind aktiv. Die Website ist noch nicht öffentlich veröffentlicht. Im Repository liegen die Webdateien in `larmradio/` und die Android-App in `laermbatz_audit/`.
Die serverseitige Owner-Allowlist (`LARMBATZ_ADMIN_EMAIL`) ist als geschütztes Secret für Development und Production konfiguriert. Die Website ist weiterhin nicht veröffentlicht; es gibt noch keinen öffentlichen Website-Link.

## Ausstehende Ergebnisse

- [x] **Bis zu 1 Stunde Master-Mix lokal aufnehmen.** In der Android-App kann die erstellte Musik bis zu 1 Stunde als AAC mit 192 kbit/s aufgenommen werden. Die Aufnahme erfasst den fertigen Master-Mix nach Mixer-/Effektkette, blockiert den Oboe-Audiocallback nicht und wird bei Ende/Fehler sauber abgeschlossen. Die Datei bleibt auf dem Android-Gerät, bis der Nutzer genau diese Aufnahme ausdrücklich zur Veröffentlichung auswählt; es gibt keine automatische Veröffentlichung. Der Debug-Build ist erfolgreich; der physische Gerätetest ist ein separater Release-Schritt.

- [ ] **LÄRMBATZ-Radio-Website mit expliziten Veröffentlichungen und Downloads.** Die externe Website muss bis zu 60-minütige AAC-Mixaufnahmen verwalten, abspielen und zum Download anbieten. Eine Aufnahme wird erst nach einer einzelnen, ausdrücklichen Nutzeraktion öffentlich gelistet; Dateien, die der Nutzer nicht zur Veröffentlichung freigibt, dürfen nicht in der öffentlichen Liste erscheinen. Für große Uploads ist Projektspeicher statt Git oder flüchtigem Container-Dateisystem zu verwenden. Veröffentlichungs-, Upload- und Verwaltungsaktionen müssen einer angemeldeten und berechtigten Website-Identität vorbehalten sein. Das Dashboard, der OAuth-geschützte Upload, die explizite Freigabe sowie statusgeprüfte Stream-/Download-Proxys sind implementiert und geprüft, die Website ist aber noch nicht öffentlich veröffentlicht.

- [ ] **Live-Radioansicht für den tatsächlich aktiven LÄRMBATZ-Mix.** Die Radioseite soll anzeigen, ob ein LÄRMBATZ-Mix live ist, und bei aktiver, autorisierter Audiozufuhr einen Webplayer dafür bereitstellen. Sie darf nicht behaupten, TikTok-Ton auszulesen oder den aktuellen TikTok-LIVE-Audiostream wiederzugeben. Gibt es keine aktive Audioquelle, muss die Seite dies als offline/nicht verfügbar kenntlich machen, statt einen falschen Live-Player vorzutäuschen.

- [ ] **Sicherer Live-Audio-Zufuhrweg und Kostenfreigabe klären.** Eine Radioansicht kann TikTok-Audio nicht auslesen; für einen tatsächlich hörbaren Web-Livestream muss der gemischte App-Master-Audioausgang ausdrücklich an eine unterstützte Website-/Streaming-Ingest-Strecke gesendet werden. Autoscaling-Web-Hosting ist ein requestgebundener, mehrinstanziger Betrieb; vor Einrichtung eines permanenten Audio-Relays sind Hostingmodus, Kosten und vom Nutzer freizugebende Betriebsart zu klären. Die offizielle Anfrage für Reserved Hosting wurde mit `requires_membership` abgelehnt; es wurde kein Hostingmodus gespeichert und nichts deployed. Das Live-Radio bleibt offline, bis der erforderliche Owner-Zugang vorliegt oder ein anderer geeigneter Dienst ausgewählt ist.

- [ ] **TikTok-LIVE-Verifikation dokumentieren.** Der externe TikTok-Ausgabeweg muss mit einem legalen, vom Nutzer betriebenen Weg (z. B. TikTok LIVE Studio Cast oder USB-Audiointerface) auf einem physischen Android-Gerät geprüft und dokumentiert werden. TikTok-LIVE-Events bleiben ohne bestätigte offizielle API unverfügbar; kein TikTok-Scraping, keine Cookies und keine undokumentierten Schnittstellen.

- [ ] **Release-Vorbereitung.** Vor Play-Store-Reife sind die vorhandenen Debug-Funktionen auf einem physischen Android-Gerät (Audiofokus und Dauerbetrieb eingeschlossen) zu testen und die offenen Release-Aufgaben – darunter Target-SDK 36, Release-Signierung und eine kontrollierte Git-/Cloud-Build-Pipeline – abzuarbeiten.

### Plattformhinweis zur Live-Option (Stand 04.11.2026)

Die Anfrage für `always_on` (permanentes Reserved Hosting) wurde von Manus mit `requires_membership` abgelehnt. Es wurde kein Hostingmodus gespeichert und nichts veröffentlicht oder deployed. Für die Frage, ob dein Guthaben die erforderliche Mitgliedschaft abdeckt, nutze bitte den Manus-Support.

## Bereits erreicht

- [x] Android-Audio-MVP mit Sechs-Spur-Oboe-Mixer, WSOLA-Time-Stretch, BPM-Erkennung, Effekten, 64-Takt-Autopilot und lokaler 60-Minuten-Masteraufnahme in AAC 192 kbit/s; Build-/Ringbuffer- und DSP-Smokes erfolgreich.
- [x] Freesound-API-Integration mit lokaler Loop-Bibliothek/Kategorien und automatisierter BPM-/Tonartanalyse; CLI-Tool für Download, Analyse und SQLite-Indizierung.
- [x] LÄRMBATZ-Radio-WebDev-Projekt initialisiert; Server, Managed MySQL und TypeScript-Diagnostik aktiviert. Industrial-Techno-Dashboard, Studio-Upload und explizite Einzelveröffentlichung mit Manus-OAuth-Allowlist sind umgesetzt. Server verifiziert echte M4A/AAC-Größe und -Dauer; öffentliche Liste gibt keine Storage-Keys aus und Audiozugriff läuft über Status-geprüfte Proxys. TypeScript, 12 Tests, Produktionsbuild, Health und Routenmanifest sind erfolgreich.
