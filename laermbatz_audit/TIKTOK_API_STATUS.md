# TikTok LIVE API – Integrationsstatus

**Status: kein produktiver LIVE-Eventzugriff konfiguriert.**

Der öffentliche TikTok Developer-Webhooks-Katalog, den wir geprüft haben, dokumentiert Account-Deautorisierung, Video-Upload-/Publish-Status und Data-Portability-Benachrichtigungen. Er dokumentiert keine LIVE-Geschenk- oder Live-Follow-Events. Damit lässt sich in diesem Projekt aktuell weder ein belastbarer OAuth-Scope noch ein Webhook-Eventname für Gifts/Follows konfigurieren.

In der App existiert darum lediglich:

- `LiveEventSource`: plattformneutraler Vertrag für spätere Events.
- `FakeEventSource`: lokale, ausdrücklich markierte Testquelle.
- `TikTokLiveEventSource`: `UNAVAILABLE`, keine Netzwerkaufrufe.

Für echte Anbindung bitte erst ergänzen, wenn TikTok oder ein autorisierter Partner schriftlich bestätigt hat:

1. dass der Zugang im Land, für das Konto und für den App-Zweck erlaubt ist;
2. dass LIVE Gift-/Follow-Events angeboten werden;
3. welche Authentifizierung, Eventfelder, Verzögerungen, IDs und Rate Limits gelten;
4. wie Tokens sicher erneuert/gespeichert werden und welche Datenschutzpflichten gelten.

Dann wird nur die `TikTokLiveEventSource`-Implementierung ergänzt; die Audiologik bleibt unverändert. Geheimnisse mit App-Secret dürfen nicht in das APK. Nicht dokumentierte Scraper, Webview-Automation, Cookies, Session-Tokens oder Chat-/Bildschirmerkennung sind kein freigegebener Produktionsweg.

Die App benötigt `INTERNET` für die ausdrücklich eingerichtete Freesound-API-Suche und OAuth-/Downloadfunktionen. Diese Berechtigung ist **keine** TikTok-LIVE-Anbindung: `TikTokLiveEventSource` führt weiterhin keine Netzwerkaufrufe aus und bleibt `UNAVAILABLE`. Ein späterer TikTok-Adapter muss separat freigegeben und dokumentiert werden.
