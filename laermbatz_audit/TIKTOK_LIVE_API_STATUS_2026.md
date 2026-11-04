# TikTok-LIVE Follow-/Gift-Events: API-Status

**Geprüft am 4. November 2026.** In den öffentlich zugänglichen offiziellen TikTok-Developer-Produkten und -Dokumenten ist keine autorisierte Schnittstelle dokumentiert, die einer Android-App oder einem Backend Follow- und Gift-Events aus TikTok LIVE in Echtzeit zustellt. Das ist eine Aussage über die öffentlich belegbare Schnittstelle; sie schließt ein nichtöffentliches, einzeln genehmigtes Partnerprogramm nicht aus. [1] [2]

Die offizielle Webhook-Dokumentation führt `authorization.removed`, `video.upload.failed`, `video.publish.completed` und `portability.download.ready` auf. LIVE-Follow- und Gift-Events gehören nicht zu den dokumentierten Eventtypen. Webhooks setzen eine konfigurierte HTTPS-Callback-URL voraus; ihre Existenz allein bedeutet keinen Zugriff auf beliebige TikTok-Ereignisse. [2]

TikToks Android OpenSDK beschreibt Login sowie das Teilen von Bildern und Videos. Ein Empfang von LIVE-Ereignissen wird dort nicht angeboten. Ein Backend kann die dokumentierten Webhooks empfangen, aber die öffentlich beschriebenen Webhooks enthalten ebenfalls keinen LIVE-Follow-/Gift-Feed. [2] [3]

Nicht damit zu verwechseln sind TikTok-Shop-LIVE-APIs: Die offiziellen Endpunkte liefern LIVE-Sitzungen und zugehörige Shop-Performance-Metriken. Der Performance-List-Endpunkt verlangt den Scope `data.shop_analytics.public.read` und ein Seller-Access-Token; seine beschriebenen Daten umfassen Verkaufs-/Sessionmetriken, nicht einzelne Follow- oder Gift-Ereignisse. [4] [5] Die Data Portability API kann nach Nutzerautorisierung Datenexporte anfordern. Zwar nennt der Datentypenkatalog LIVE-Historie und Gesamtwerte wie „Total Gifters“, doch ein Exportauftrag ist kein Live-Eventstream; die Bereitstellung kann Sekunden, Minuten oder Stunden dauern und ist nicht garantiert. [6] [7]

## Konsequenz für LÄRMBATZ

`TikTokLiveEventSource.UNAVAILABLE` sollte unverändert bleiben, bis TikTok einen **konkreten, für LÄRMBATZ genehmigten** Schnittstellenzugang belegt. Für eine Neubewertung benötigt das Projekt eine schriftliche Bestätigung von TikTok mit Produkt-/Programmnamen, API- oder Webhook-Dokumentation samt Event-Schema, Berechtigungen und Freigabestatus, zulässigem Nutzungszweck sowie Markt-/Kontobeschränkungen. Öffentlich dokumentierte Developer-Produkte verlangen App-/Scope-Prüfung und gegebenenfalls nutzerseitige Autorisierung; TikTok garantiert keine Genehmigung. Bei einem möglichen LIVE-/Shop-Partnerweg muss TikTok ausdrücklich bestätigen, dass genau Follow- und Gift-Events – nicht lediglich Shop-Analytics – freigeschaltet sind. [6] [8]

Bis dahin weder Scraping noch Reverse-Engineering, undokumentierte WebSocket-Endpunkte, Session-Cookies oder Community-SDKs als bestätigte oder zulässige Lösung einsetzen. TikToks Bedingungen untersagen unter anderem nicht autorisierte automatisierte Datenerhebung sowie das Umgehen von Zugriffsbeschränkungen; die Developer-Bedingungen verlangen Nutzung gemäß veröffentlichter Dokumentation und räumen TikTok App-Prüfung und Freigabe ein. [8] [9]

## References

[1]: https://developers.tiktok.com/docs/en/welcome "TikTok Developer Documentation – Welcome"
[2]: https://developers.tiktok.com/docs/en/webhooks-events "TikTok for Developers – Webhook Events"
[3]: https://developers.tiktok.com/doc/mobile-sdk-android-quickstart "TikTok OpenSDK for Android – Quickstart"
[4]: https://partner.tiktokshop.com/docv2/page/get-shop-live-performance-list-202508 "TikTok Shop – Get Shop LIVE Performance List"
[5]: https://partner.tiktokshop.com/docv2/page/sv0xa2r6 "TikTok Shop – New APIs for TikTok LIVE"
[6]: https://developers.tiktok.com/doc/data-portability-api-get-started "TikTok Data Portability API – Get Started"
[7]: https://developers.tiktok.com/doc/data-portability-data-types "TikTok Data Portability API – Data Types"
[8]: https://developers.tiktok.com/doc/our-guidelines-developer-guidelines "TikTok Developer Guidelines"
[9]: https://www.tiktok.com/legal/page/global/tik-tok-developer-terms-of-service/en "TikTok Developer Terms of Service"
