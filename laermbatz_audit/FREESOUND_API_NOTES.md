# Freesound API v2 — Lizenz- und Suchverhalten

Stand der Dokumentationsprüfung: 4. November 2026.

Die Freesound-API v2 liefert beim Soundfeld `license` die dokumentierten Namen `Creative Commons 0`, `Attribution` oder `Attribution NonCommercial`; das Feld `type` enthält das ursprüngliche Dateiformat, darunter `wav`. Die lokale strenge Prüfung auf exakt CC0 und optional CC BY ist daher passend und soll unbekannte sowie nichtkommerzielle Lizenzen weiter ablehnen. [1]

Der Suchfilter nutzt `filtername:value`; mehrteilige Werte werden in Anführungszeichen gesetzt. Die API unterstützt logische Operatoren in Klammern, z. B. `type:(wav OR aiff)`. Die Suchdokumentation erlaubt höchstens 150 Ergebnisse pro Seite und gewichtet Treffer anhand von Tags und Metadaten. Normale Suchwörter gelten als verpflichtend, sofern sie nicht anders markiert sind; ein Auto-Set-Query mit drei Wörtern kann deshalb deutlich enger sein als jedes Wort allein. Das aktuelle Auto-Set verwendete pro Kategorie nur eine enge Dreiwort-Suche und der Client verlangte nur 25 Treffer. [1]

Daraus folgt: Lizenzwerte nicht lockern, sondern die zulässigen Lizenzen serverseitig filtern, bei CC-BY-Opt-in CC0 oder Attribution anfordern und beim Auto-Set auf weniger spezifische Suchbegriffe ausweichen. Die lokale Lizenzprüfung bleibt zusätzlich als Sicherheitsnetz bestehen. Ein leerer Suchlauf allein beweist nicht, dass Freesound keine zulässigen WAVs enthält; er kann auch an einem zu engen Query oder einer begrenzten Ergebnisseite liegen.

## Quellen

[1]: https://freesound.org/docs/api/resources_apiv2.html "Freesound API v2 resources and search documentation"
[2]: https://freesound.org/docs/api/overview.html "Freesound API v2 overview"
