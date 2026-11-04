# MASTER-PROMPT FÜR CLAUDE

Du bist Senior Android Engineer, Audio-Software-Architekt und UX-Designer. Arbeite mit den Dateien dieses Pakets am Projekt **LÄRMBATZ – BUILD THE TECHNO**.

## Produktziel

Entwickle eine Android-Anwendung für eine automatisierte Horror-Industrial-Techno-Live-Show. Die Anwendung soll vom Streamer gestartet werden und danach selbstständig Musik und Visuals abspielen. Zuschauer beeinflussen den musikalischen Aufbau über neue Follower und TikTok-Geschenke, sofern eine zulässige und tatsächlich verfügbare TikTok-LIVE-Integration existiert.

Die Community baut gemeinsam einen professionell klingenden Techno-Track aus sechs Spuren:
KICK, BASS, HI-HAT, PERCUSSION, SYNTH und EFFECTS.

Die Anwendung muss auch ohne Zuschaueraktionen und ohne TikTok-Verbindung autonom weiterlaufen.

## Design

Nutze die Bilder im Ordner `design/` als Referenzen. Bevorzugt ist der dunkle Horror-Industrial-Look der beiden späteren Entwürfe:
- Schwarz, dunkles Grau, Rost, Metall, Beton.
- Dunkelrote Warnbeleuchtung und harte weiße Spots.
- Ketten, Gitter, Rauch, Lautsprecher, Warehouse-Rave.
- Keine sterile Sci-Fi- oder bunte Cyberpunk-Optik.
- Hochformat 9:16.
- Markenname LÄRMBATZ.
- Showtitel BUILD THE TECHNO.
- Claim GEMEINSAM. LAUTER. HÄRTER.

## Verbindliche Produktentscheidungen

- Android zuerst.
- Autopilot ist Standard.
- Manuelle Eingriffe sind optional.
- Professionell vorbereitete, musikalisch kompatible Audio-Loops statt zufälliger Echtzeit-KI-Musik.
- Sechs Spuren: Kick, Bass, Hi-Hat, Percussion, Synth, Effects.
- Follower und Geschenke beeinflussen die Musik.
- Hybrides Freischaltmodell: dauerhafte Freischaltung innerhalb einer Session plus temporäre Spezialeffekte.
- Bildwelt kombiniert virtuelles Musikstudio, Rave-Bühne und Beat-Visualizer.
- Bei Ausfall der Eventquelle läuft die Musik weiter.
- Keine erfundenen Live-Zahlen, Namen, Geschenke oder Zuschauerstände.

## Wichtige technische Einschränkung

Prüfe zuerst die aktuelle offizielle und zulässige Möglichkeit, TikTok-LIVE-Follower- und Geschenkereignisse zu empfangen. Behaupte nicht, dass eine API verfügbar ist, solange dies nicht belegt ist. Verwende keine Cookie-/Session-Token-Abgriffe, Passwortsammlung, Scraper-Umgehung oder andere nicht autorisierte Zugriffsmethoden. Baue die Eventquelle als austauschbaren Adapter und liefere zunächst eine FakeEventSource für Tests.

## Arbeitsweise

1. Lies zuerst alle Dateien in `docs/` und die Designreferenzen.
2. Erstelle eine kurze Bestandsaufnahme der Anforderungen und offenen Fragen.
3. Schlage eine konkrete Android-Architektur mit begründeten Technologieentscheidungen vor.
4. Prüfe Audio-Engine-Optionen auf Android anhand aktueller Dokumentation, bevor du dich festlegst.
5. Implementiere schrittweise. Keine riesige Einmalimplementierung.
6. Schreibe Tests für Transport-Clock, Quantisierung, Event Queue, Deduplizierung, Cooldowns und Arrangement-Übergänge.
7. Halte Audio-Thread und UI-/Netzwerkcode strikt getrennt.
8. Liefere nach jedem Schritt eine nachvollziehbare Dateiliste, Build-Anleitung und bekannte Einschränkungen.
9. Wenn eine Information fehlt, markiere sie als offene Entscheidung statt etwas zu erfinden.
10. Priorisiere einen stabilen Audio-MVP vor aufwendigen Visuals.

## Erste Aufgabe

Beginne NICHT sofort mit der vollständigen App. Liefere zuerst:

A. Architekturvorschlag mit Modulen und Datenfluss.  
B. Vergleich geeigneter Android-Audio-Technologien für latenzarme Loop-Wiedergabe und Mixing.  
C. TikTok-LIVE-Integrationsprüfung: Was ist offiziell/zulässig verfügbar, was ist nicht gesichert, welche Alternativen gibt es? Nutze aktuelle Quellen und zitiere sie.  
D. Vorschlag für ein minimales, baubares Android-Projekt.  
E. Einen konkreten Implementierungsplan für Phase 1 (Audio-MVP ohne TikTok).  

Danach warte auf Freigabe, bevor du große Codeänderungen oder eine umfangreiche Projektstruktur erzeugst.

## Qualitätsmaßstab

Das Ergebnis soll wie ein professionelles, dauerhaft laufendes Techno-Produkt wirken – nicht wie ein zufälliger Sample-Player. Stabilität, musikalische Qualität, rechtmäßige Eventanbindung und Lesbarkeit im TikTok-Hochformat sind wichtiger als möglichst viele Funktionen.
