# UI/UX-Spezifikation

## 1. Format und Stil

- Primärformat: 9:16 Hochformat.
- Zielauflösung für erste Layouttests: 1080 × 1920; responsiv skalieren.
- Stil: Horror-Industrial / düstere Warehouse-Rave-Ästhetik.
- Farbwelt: Schwarz, rußiges Grau, rostiges Metall, dunkles Rot, sparsame weiße Highlights.
- Materialien: Beton, Stahlplatten, Kratzer, Rost, Ketten, Gitter, Nebel.
- Licht: rote Warnleuchten, harte weiße Spots, dezente dunkle Schatten.
- Vermeiden: glänzende Sci-Fi-Hologramme, bunte Cyberpunk-Neonflächen, sterile futuristische Bedienfelder.

## 2. Bildschirmzonen

### Kopfbereich
- LÄRMBATZ-Logo.
- Unterzeile BUILD THE TECHNO.
- LIVE-Status nur anzeigen, wenn tatsächlich ein Stream-/Live-Status bekannt ist.
- Zuschauerzahl nur anzeigen, wenn echte Daten vorliegen.

### Bühnenbereich
- Virtuelle Bühne / Warehouse.
- DJ-Silhouette oder maskierte Figur als visuelles Motiv.
- Crowd-Silhouetten, Lautsprecher, Rauch, Ketten und rotes Licht.
- Dezente Animationen synchron zum Beat.

### Event-Feed
- Letzter neuer Follower.
- Letztes Geschenk.
- Top-Supporter nur bei realen Eventdaten.
- Keine erfundenen Namen oder Beispielwerte im Livebetrieb.

### Drop-Anzeige
- Nächster Drop oder nächster musikalischer Höhepunkt.
- Fortschrittsanzeige muss den tatsächlichen Arrangement-Zustand abbilden.
- Countdown nicht künstlich als Spenden-Druckmittel verwenden. Bei dynamischem Verlauf lieber „Build-up läuft“ und Fortschritt zeigen.

### Track Builder
Sechs gut erkennbare Module:
- KICK
- BASS
- HI-HAT
- PERCUSSION
- SYNTH
- EFFECTS

Jedes Modul zeigt:
- Status (aktiv / vorbereitet / temporär).
- Level oder Aktivitätsanzeige.
- Kleine Beat-/Wellenformanimation.
- Optional Kurzname der aktuell verwendeten Variation.

Die Prozentwerte aus den Referenzbildern sind nur Platzhalter und dürfen nicht als echte Lautstärke-/Spendenwerte missverstanden werden.

### Community-Bereich
- Ereignisse und kurze Chat-/Community-Reaktionen nur dann darstellen, wenn echte Daten verfügbar sind.
- Follower- und Geschenkhinweise getrennt kennzeichnen.
- Keine automatischen Chatnachrichten senden, sofern keine ausdrücklich zulässige Funktion und Freigabe vorliegt.

### Fußbereich
- Kurze Erklärung: „Follower bringen neue Elemente“ / „Geschenke verändern den Track“.
- Keine irreführende Aussage, dass ein Geschenk einen garantierten bestimmten Effekt auslöst, falls die Eventzuordnung nicht konfiguriert ist.

## 3. Streamer-Steuerung (separater Kontrollbereich)

Der Zuschauerbildschirm bleibt visuell sauber. Die Steuerung kann als separate Android-Ansicht oder ausblendbares Overlay gestaltet werden.

MVP-Steuerungen:
- Start / Pause / Stop
- Autopilot ein/aus
- Musikrichtung / Preset auswählen
- Master-Lautstärke
- Event-Verarbeitung aktivieren/deaktivieren
- Nächster Abschnitt (optional)
- Audio-Ausgabe testen
- TikTok-Verbindung / Eventquelle: Status und Diagnose
- Not-Aus: Musik sofort stummschalten

## 4. Barrierefreiheit und Lesbarkeit

- Text auch auf kleinen Displays lesbar.
- Rote Anzeigen nicht ausschließlich über Farbe unterscheiden; Statussymbol/Text ergänzen.
- Animationen nicht übermäßig flackern lassen.
- Keine schnellen Vollbild-Blitze.
- UI muss auch bei dunklem Bühnenbild klare Kontraste haben.

## 5. Designreferenzen

Die Bilder in `design/` zeigen die visuelle Richtung. Das erste Bild ist der frühere neon-futuristische Entwurf; die späteren zwei Bilder sind die bevorzugten Horror-Industrial-Referenzen. Für die Weiterentwicklung den roten/schwarzen Industrial-Look bevorzugen.
