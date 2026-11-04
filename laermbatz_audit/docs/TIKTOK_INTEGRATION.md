# TikTok-LIVE-Integration: Prüf- und Sicherheitsplan

## Ziel

Echte neue Follower und Geschenkereignisse sollen BUILD THE TECHNO beeinflussen.

## Kritischer offener Punkt

Vor der Implementierung muss geklärt werden, welche offiziellen TikTok-Schnittstellen, LIVE-Integrationen oder autorisierten Partnerlösungen für das konkrete Konto, die Region und den App-Typ tatsächlich verfügbar sind.

Nicht ungeprüft voraussetzen, dass eine öffentliche offizielle API alle Live-Geschenke und Follow-Events bereitstellt.

## Anforderungen an einen Event-Adapter

Die Audio-/Spiel-Logik darf nicht direkt von einer bestimmten TikTok-Bibliothek abhängen. Definiere ein neutrales Interface, beispielsweise:
- onFollow(event)
- onGift(event)
- onConnectionStateChanged(state)
- onError(error)

Normalisiertes Eventmodell:
- eventId (wenn verfügbar)
- eventType
- userDisplayName (optional)
- giftId / giftName (optional)
- giftCount (optional)
- value/coinValue (nur falls zuverlässig geliefert)
- timestamp
- source
- deduplicationKey (optional)

## Adapter-Strategie

1. Zuerst offizielle TikTok-Dokumentation und zulässige Partnerwege prüfen.
2. Wenn kein erlaubter Zugriff verfügbar ist, klaren Demo-Modus mit simulierten Events bereitstellen.
3. Keine Browser-Cookies, Session-Tokens oder TikTok-Passwörter abgreifen.
4. Keine Schutzmechanismen, Zugriffsbeschränkungen oder Rate Limits umgehen.
5. Keine nicht autorisierten Scraper als Produktionsgrundlage verwenden.
6. Bei Verbindungsabbruch: Audio läuft weiter, Eventverarbeitung pausiert und Status wird angezeigt.

## Datenschutz

- Nur erforderliche Eventdaten verarbeiten.
- Keine unnötige Speicherung von Zuschauernamen.
- Event-Historie begrenzen und konfigurierbar machen.
- Datenschutzhinweise und Plattformregeln berücksichtigen.
- Keine automatischen privaten Nachrichten oder Chat-Spam.

## Teststrategie

- FakeEventSource für lokale Tests.
- Simulierte Follower- und Geschenkereignisse.
- Replay-Test mit doppelten Events.
- Lasttest mit Event-Bursts.
- Disconnect-/Reconnect-Test.
- Test, dass Musik auch ohne Verbindung weiterläuft.
