# Loop-Import und Quellen

Konzeptstand: 2026-11-02. Implementierungsstatus: 2026-11-06. Zielgerät: Samsung Galaxy A17 5G (SM-A176B). Alles läuft auf einem Gerät.

## Grundregel

Loops werden NICHT im App-Paket oder Git-Repository ausgeliefert. Der Nutzer wählt lokale ZIPs über den Android-Dateidialog; die App extrahiert nur gültige WAVs in ihre lokale Bibliothek. Fremde Rohdateien werden weder von der App noch vom Projekt weitergegeben.

Ein ZIP-Import bestätigt keine Rechte für öffentliche, kommerzielle oder monetarisierte Streams. Jedes manuell im Android-Dateidialog ausgewählte ZIP wird unabhängig von seinem Dateinamen als `Lizenz nicht unabhängig geprüft` gespeichert; ein ZIP-Name beweist weder Quelle noch Packinhalt. Der geprüfte Samplepack-Katalog ist deshalb keine automatische Freigabe. Die Importbestätigung bezieht sich nur auf lokale Nutzung. Vor einem externen LIVE oder einer Veröffentlichung muss der Nutzer die Lizenz-/Anbieterbedingungen und nötige Credits selbst prüfen; bis dahin nicht live verwenden.

## Quellen (kostenlos)

Geprüft = Lizenztext oder FAQ gelesen. Übrige: nur Angaben der Seiten, Lizenzdatei im Download selbst lesen.

| Quelle | Inhalt | Lizenzstand |
|---|---|---|
| Looperman | Community-Loops, Industrial Drums | FAQ gelesen: kommerziell und nichtkommerziell, vorbehaltlich AGB. Apps laut FAQ erlaubt, solange innerhalb der Lizenz. AGB selbst nicht geprüft. Credit laut FAQ nicht nötig, eine Drittquelle widerspricht: AGB prüfen. |
| Sample Focus | Community-Samples, Gratis-Credits pro Woche | Geprüft (samplefocus.com/license): erlaubt u. a. Online-Inhalte, Spiele, Smartphone- und Desktop-Apps. Weitergabe allein verboten, auch gratis. |
| Ghosthack | Gratis-Techno-Packs (ca. 490 Sounds), Industrial FX | Nicht geprüft |
| Teknovault | Gratis Hard-Techno-Pack (über 680 Samples) | Nicht geprüft |
| Myloops | 400 MB+ Techno, nahtlose Loops, BPM/Key im Dateinamen | Nicht geprüft |
| Samplesound | Rotierende Gratis-Techno-Packs, 44,1 kHz / 24 Bit | Nicht geprüft |
| SampleRadar (MusicRadar) | Gratis-Packs | Laut Übersicht lizenzfrei, keine Weitergabe der Originale. Nicht geprüft |
| Loopmasters (Free) | Gratis-Packs aus Label-Netzwerk | Je Pack unterschiedlich |
| Freesound | Effekte, Impacts, Riser | Nur CC0 filtern. Andere Lizenzen brauchen Namensnennung oder sind nicht kommerziell |
| Loopazon | Loops mit BPM-/Key-Filter | 10 Downloads ohne Konto. Lizenz nicht geprüft |

Nicht aufgenommen: Splice (kostenpflichtig), BandLab und Cymatics (nicht ausreichend geprüft).

## pack.json

```json
{
  "pack_id": "myloops_techno_01",
  "name": "Myloops Free Techno",
  "source": "myloops",
  "source_url": "https://...",
  "downloaded_on": "2026-11-02",
  "license_file": "LICENSE.txt",
  "allowed_for_streaming": "unchecked",
  "bpm": 140,
  "key": "Am",
  "assets": [
    {
      "id": "kick_loop_01",
      "file": "kick/kick_01.wav",
      "track": "KICK",
      "type": "loop",
      "bars": 4,
      "bpm": 140,
      "key": null,
      "group": "A",
      "tags": ["hard", "distorted"]
    }
  ]
}
```

Felder:
- `source`: looperman, samplefocus, ghosthack, teknovault, myloops, samplesound, sampleradar, loopmasters, freesound_cc0, own, other
- `allowed_for_streaming`: yes | no | unchecked; dieses Feld ist eine Dokumentationsangabe und wird nicht aus einem ZIP-Dateinamen abgeleitet oder automatisch verifiziert.
- `track`: KICK | BASS | HIHAT | SNARE | PERCUSSION | SYNTH | EFFECTS
- `type`: loop | oneshot | fill | riser | impact
- `bars`: 1–64; der aktuelle MVP schätzt die Taktzahl beim WAV-Import automatisch.
- `group`: harmonisch zusammengehörige Loops (gleiche Tonart)
- Eigene Loops: `source: "own"`, `allowed_for_streaming: "yes"`
- Looperman: statt Lizenzdatei Link zur Loop-Seite und Downloaddatum speichern

## Importprüfung und aktuelle MVP-Implementierung

- Importiert werden RIFF/WAVE PCM Integer 16/24/32 Bit oder IEEE Float 32 Bit, Mono/Stereo und 8–192 kHz; 32 MiB Dateigröße und 4 Millionen interleaved Samples sind die Obergrenzen.
- Der Android-Dateidialog akzeptiert bis zu 20 ZIPs pro Vorgang. ZIP-Signatur, Eintragszahl, komprimierte Gesamtgröße, Dekompressionsbudget, Einzel-/Gesamtvolumen, WAV-Header und SHA-256-Duplikate werden begrenzt/geprüft; das kumulative Dekompressionslimit pro Auswahl bleibt 4 GiB. Vor jedem Staging-Schreibblock wird die 128-MiB-Speicherreserve kontrolliert; nach einem Abbruch werden alte importer-eigene Stagingordner ab zwei Stunden bereinigt. Pfad-Traversal wird verhindert; ein bereinigter ZIP-Unterpfad wird nur als Metadatum genutzt. Nur WAV-Einträge werden importiert.
- Jedes manuell ausgewählte ZIP bleibt als `Lizenz nicht unabhängig geprüft` markiert; der Dateiname wird nicht zur Rechtefreigabe verwendet. Die Lizenzbestätigung beim Import gilt nur für lokale Nutzung, nicht für einen öffentlichen LIVE oder eine kommerzielle Veröffentlichung.
- SHA-256-Duplikate werden nicht erneut als WAV gespeichert. Der Importer liefert bestehende doppelte Dateien aber zur Reparatur fehlender Lizenz-/Analyse-Sidecars zurück. Vorhandene gültige Metadaten eines anderen Imports werden dabei nicht überschrieben. Alte Sidecars, die nur über einen ZIP-Dateinamen als Katalogpack eingestuft worden waren, werden auf den ungeprüften Status zurückgestuft.
- Beim Import schätzt ein Onset-Autokorrelator das Quelltempo von 60–180 BPM. Daraus wird die ganzzahlige Taktzahl (1–64) abgeleitet. Bei niedriger Konfidenz wird die Taktzahl aus Dateidauer und aktuell gewähltem Set-BPM geschätzt.
- Loops werden beim Abspielen automatisch auf die ermittelte Taktzahl und das Set-BPM quantisiert. Der native Oboe-Callback nutzt WSOLA mit korrelationsbasierten 10-ms-Hops, um die Tonhöhe bei der Zeitdehnung zu erhalten. WAVs unter `max(512 Frames, 20 ms)` nutzen wegen zu kurzer Overlap-Fenster einen sichtbaren Varispeed-Fallback; extreme Tempoverschiebungen können WSOLA-Artefakte erzeugen.
- Die lokale ZIP-Bibliothek sortiert nach Kick/Bassdrum, Bassline, Hi-Hat, Snare, Percussion, Synth und Effekten. Genre wird best-effort aus ZIP-Name, Verzeichnispfad und Dateiname abgeleitet. BPM und Tonart werden pro WAV analysiert und lokal in Sidecars gespeichert; die Bibliothek kann nach Kategorie, Genre und Tonart filtern und BPM-/Tonart-nahe Loops bevorzugt anzeigen.
- Der Live-BPM-Anker ist der BPM-Regler des LÄRMBATZ-Mixers, nicht der Ton eines TikTok-Streams. Änderungen wirken auf den internen Mixer. Der erste sicher erkannte Bassline-/Synth-Loop setzt den harmonischen Live-Key-Anker. Weitere Bassline-/Synth-Loops werden nur bei gleicher Dur-/Moll-Stimmung und sicherer Analyse um höchstens ±6 Halbtöne angeglichen. Die Tonart-Erkennung ist best effort; Drums/FX und unklare Loops werden nicht transponiert. Der einfache Resampling-Pitch-Shift kann Artefakte erzeugen.
- Eine während der Wiedergabe neu importierte/ersetzte Spur startet auf dem nächsten Takt. Sample 0 wird als Downbeat angenommen; führende Stille wird nicht analysiert oder entfernt.
- BPM-Abweichung, Taktzahl und WSOLA-Stretchfaktor werden in der Trackzeile angezeigt; sehr kurze Dateien kennzeichnet die UI als Varispeed-Fallback. Halb-/Doppeltempo-Ambiguität wird markiert, aber automatisch mit dem schnelleren Kandidaten aufgelöst; Fehl-Erkennung bleibt möglich und manuelle Korrektur ist noch offen.
- Während der Wiedergabe laufen Loop-Positionen auch dann weiter, wenn eine Spur gerade vom Autopilot oder manuell stummgeschaltet ist. Beim Re-Entry bleibt der Loop dadurch auf der globalen Beat-/Taktphase.
- Lizenzstatus: alle manuell ausgewählten ZIPs bleiben `Lizenz nicht unabhängig geprüft`. Der Dateiimport prüft technische WAV-Gültigkeit, nicht Nutzungsrechte.

## Suchliste (Richtwert 140 BPM)

| Spur | Suchbegriffe | Gut bei |
|---|---|---|
| KICK | hard techno kick loop, distorted kick, rumble kick | Teknovault, Ghosthack, Myloops |
| BASS | rumble bass loop, industrial bass, acid bass | Looperman, Myloops, Sample Focus |
| HI-HAT | closed hat loop, offbeat hat, 16th hats | Samplesound, SampleRadar |
| SNARE | techno snare loop, hard snare, snare roll | SampleRadar, Ghosthack |
| PERCUSSION | industrial percussion, metal hits, tom loop, clap | Looperman, Ghosthack |
| SYNTH | dark synth loop, stab, drone, pad | Sample Focus, Loopmasters, Myloops |
| EFFECTS | riser, impact, noise sweep, industrial fx, machinery | Freesound (CC0), Ghosthack |

Mindestmenge für den ersten Test: 2 Kicks, 2 Bässe, 2 Hi-Hats, 2 Percussion, 2 Synths, 3 Effekte, gleiche Tonart und BPM.
