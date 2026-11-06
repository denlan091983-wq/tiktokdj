# LÄRMBATZ – BUILD THE TECHNO

Projektpaket für die Weiterentwicklung mit Claude.

**Projektart:** Android-App / automatisierte audiovisuelle Live-Show für TikTok LIVE  
**Marke:** LÄRMBATZ  
**Spiel-/Showtitel:** BUILD THE TECHNO  
**Claim:** GEMEINSAM. LAUTER. HÄRTER.

## Inhalt

- `design/`: drei visuelle Entwürfe aus der bisherigen Konzeptarbeit.
- `docs/PRODUKTKONZEPT.md`: Produktidee und festgelegte Entscheidungen.
- `docs/UI_UX_SPEZIFIKATION.md`: Hochformat-Oberfläche und Bedienlogik.
- `docs/AUDIO_ENGINE.md`: Audio- und Musiksystem.
- `docs/TIKTOK_INTEGRATION.md`: Anforderungen und offene technische Prüfung für Live-Events.
- `docs/ROADMAP.md`: sinnvoller MVP- und Entwicklungsplan.
- `claude/MASTER_PROMPT.md`: Startprompt, den du Claude geben kannst.

## Implementierungsstatus (6. November 2026)

Das Paket enthält neben Konzept und Spezifikationen einen Android-Audio-MVP: SAF-WAV- und lokale ZIP-Loop-Imports, sechs native Loop-Spuren, BPM-Regler, Arrangement-Autopilot, Spur-Level und Effekte. ZIP-WAVs werden lokal nach Kategorie/Genre sortiert und automatisch auf BPM/Tonart analysiert. Der interne Mixer quantisiert Loops per WSOLA auf sein eingestelltes BPM; Bassline/Synth können bei sicher passender Dur-/Moll-Tonart begrenzt transponiert werden. Alle manuell ausgewählten ZIPs bleiben unabhängig vom Dateinamen als lizenzungeprüfte Nutzerimporte markiert; die lokale Importbestätigung ist keine Freigabe für öffentliche LIVE-Streams oder Veröffentlichung. Build- und Funktionsgrenzen stehen in `MIXER_MVP_STATUS.md` und `AUDIT_ROADMAP.md`.

Der jüngste Cloud-Build steht im verbindlichen Auditbericht [AUDIT_ROADMAP.md](AUDIT_ROADMAP.md). Die Android-App ist ein baubares **Debug-MVP**, keine live-abgenommene oder release-signierte Streaming-App. Ein physischer Gerätetest und ein bestätigter TikTok-Ausgabeweg stehen noch aus.

**TikTok LIVE ist absichtlich nicht verbunden.** Für öffentliche TikTok-Developer-APIs ist in der geprüften Dokumentation kein bestätigter Echtzeit-Feed für LIVE-Follow-/Gift-Events enthalten. Details und Quellen stehen in `TIKTOK_LIVE_API_STATUS_2026.md`. Nur eine schriftlich belegte TikTok-Freigabe für genau diese Events kann die Integration freischalten.

## Wichtig

Der Android-Code ist ein **MVP, keine produktionsreife App**. Gerätekompatibilität, längerer Audio-Dauerbetrieb, Audiofokus- und Hintergrundverhalten müssen noch auf dem Zieltelefon geprüft werden. Die Zahlen, Nutzernamen, Geschenk-Events und Countdown-Anzeigen in den Bildern sind illustrative Mockup-Inhalte und keine echten TikTok-Livedaten.

TikTok-LIVE-Zugriff auf Geschenke und neue Follower bleibt `UNAVAILABLE`, bis ein zulässiger offizieller oder schriftlich bestätigter Partnerzugang vorliegt. Die App nutzt Internet für Freesound, nicht für TikTok-LIVE-Events. Keine nicht autorisierten Scraper, Zugangsdaten-Abfragen oder Umgehungen einbauen.

## Empfohlener Start

1. [AUDIT_ROADMAP.md](AUDIT_ROADMAP.md) und [CLOUD_BUILD.md](CLOUD_BUILD.md) als aktuellen Stand lesen.
2. Nur die dort benannte APK installieren; danach zuerst den Zielgeräte-Audiotest und den externen TikTok-LIVE-Ausgabeweg abnehmen.
3. Vor einer Play-Veröffentlichung Android-Ziel-API, Release-Signierung und Datenschutz-/Lizenztexte aktualisieren.
