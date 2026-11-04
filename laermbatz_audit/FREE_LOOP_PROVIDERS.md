# Gratisloops für die App: Anbieter- und Lizenzprüfung

**Ziel:** legale kostenlose Loops finden, lokal sammeln und später automatisiert suchen, herunterladen und importieren sowie nach einer autorisierten Auslösung abspielen. Dieser Bericht unterscheidet die Nutzung eines Samples in einem fertigen Musikstück von der Verteilung des unveränderten Roh-Audios als App-Asset. Eine „kostenlos“- oder „royalty-free“-Angabe belegt für sich genommen keine Erlaubnis, Rohdateien in einer APK oder zugänglichen App-Bibliothek weiterzugeben.

Die Angaben unten fassen die geprüften offiziellen Anbieter-, Produkt-, Lizenz- und FAQ-Seiten zusammen. Rechte können produkt- und dateiindividuell sein. „Nicht dokumentiert“ bedeutet nicht automatisch verboten, aber es ist keine positive Freigabe belegt. Es wurden keine Audiodateien heruntergeladen.

**Wichtig für dein Ziel:** Die Hinweise gegen Rohdatei-Verteilung betreffen vor allem Samples, die in eine APK gepackt oder über einen gemeinsamen App-Katalog an andere Nutzer ausgeliefert werden. Eine persönliche, nur auf deinem Gerät gespeicherte Sample-Sammlung ist davon zu unterscheiden; auch dafür müssen jedoch die jeweilige Sound-Lizenz und die Download-/API-Bedingungen eingehalten werden. Die vorhandene App speichert WAV-Imports bereits app-intern, hat derzeit aber noch keinen Online-Katalog, keine Netzwerkberechtigung und keinen ZIP-Import.

## Anbieterprüfung

### 1. Looperman

- **Angebot und Relevanz:** Kostenlose, nutzergenerierte WAV-Loops. Die geprüften Kategorien zeigten 2.414 Techno-Loops sowie 465 Hardstyle-Loops; darunter Drums, Kicks, Synths, Bass und FX. Das Genreangebot passt gut, Qualität und Rechteinhaberschaft einzelner Uploads werden jedoch nicht garantiert. [1] [4] [5]
- **Streams und Monetarisierung:** Die Lizenz für registrierte Nutzer erlaubt die Einbindung in neue, originale musikalische Werke und deren kommerzielle Verwertung. Die FAQ nennt Veröffentlichungen auf YouTube, Spotify und ähnlichen Diensten. Monetarisierte Streams sind nicht ausdrücklich separat benannt; die kommerzielle Werkverwertung spricht für eingebettete Loops in monetarisierter Musik, nicht für das isolierte Streamen des Loops. Looperman garantiert die Rechte an einzelnen Uploads nicht. [2] [3]
- **Rohdatei-Einbettung:** Die FAQ untersagt Weitergabe „as is“, eigene Bereitstellung und Sample-Packs. Eine APK oder Bibliothek, aus der Nutzer die unveränderten WAVs extrahieren oder abrufen können, ist nicht klar freigegeben und fällt dem Wortlaut nach voraussichtlich unter Rohdatei-Weitergabe. Dafür wäre eine zusätzliche ausdrückliche Erlaubnis des Rechteinhabers nötig. [3]
- **Offizieller Downloadweg:** Manuell mit kostenlosem Konto anmelden, die jeweilige Loop-Seite öffnen und den Download am Player auslösen; laut FAQ im WAV-Format. Es gelten Minuten- und Tageslimits; Umgehungsversuche können zur IP-Sperre führen. [1] [3]
- **Automatisierbarkeit:** Keine öffentliche API oder Download-Automatisierungserlaubnis belegt. Die Bedingungen untersagen nicht autorisierten programmgesteuerten Zugriff und Downloads, Scraping und automatisierte Extraktion. [2]
- **Empfehlung:** **Nicht geeignet** für eine App, die Roh-Loops an Nutzer ausliefert. Einzelne Samples allenfalls nach zusätzlicher ausdrücklicher App-/Weiterverteilungslizenz und manuellem Bezug nutzen; Looperman allein erteilt die erforderliche Freigabe nicht. [2] [3]

### 2. Freesound

- **Angebot und Relevanz:** Gemeinschaftliche Sammlung von über 738.000 Sounds. Die offizielle Suche nach „techno loop“ zeigte 4.881 Treffer, darunter Techno-, Hard-Techno- und Industrial-Loops. Das belegt Genre-Relevanz, nicht Qualität oder eine bestimmte Lizenz der einzelnen Treffer. Eine verlässliche Hardstyle-Auswahl wurde damit nicht belegt. [6]
- **Streams und Monetarisierung:** Lizenz je Datei prüfen. CC0 erlaubt nach FAQ weitgehende kommerzielle Nutzung; CC BY erlaubt kommerzielle Nutzung mit Attribution; CC BY-NC schließt kommerzielle Nutzung aus. Bei älteren Sampling+-Dateien können besondere Bedingungen gelten. Keine pauschale Stream-Freigabe für alle Sounds. [7] [8]
- **Rohdatei-Einbettung:** Keine globale App-Redistributionsfreigabe. CC0 und CC BY können Weitergabe grundsätzlich erlauben, jeweils unter den einschlägigen Bedingungen; BY erfordert Attribution. BY-NC ist für eine kommerzielle/monetarisierte App nicht gedeckt. Die konkrete Datei-Lizenz, etwaige ältere Lizenz und Verteilungsform müssen einzeln geprüft werden. [7] [8]
- **Offizieller Downloadweg:** Manuell registriertes und angemeldetes Konto, Sound-Detailseite und Download-Button. Die API dokumentiert zusätzlich `GET /apiv2/sounds/{sound_id}/download/` als Download der Originaldatei/-qualität mit OAuth2. [7] [10]
- **Automatisierbarkeit:** Eine offizielle API v2 unterstützt Suche/Metadaten und Dateiabruf; Registrierung/API-Zugangsdaten und für Downloads OAuth2 sind erforderlich. Die Bedingungen untersagen Scraping außerhalb der API und beschränken Zugriff und Weitergabe. Kommerzielle API-Nutzung ist laut offiziellen Bedingungen im Einzelfall mit UPF zu verhandeln; Entwicklerangaben beschreiben die API als für nichtkommerzielle Zwecke kostenlos. [9] [10]
- **Empfehlung:** **Manuelle Lizenzprüfung erforderlich; kein automatischer Massendownload freigegeben.** Vorab je Sound Lizenz und Attribution prüfen sowie eine kommerzielle API-Nutzung mit Freesound klären, falls zutreffend. Erst nach bestätigter API-Freigabe/Key und Prüfung jedes Sounds automatisiert abrufen; die API allein erteilt keine Rohdatei-Verteilungsrechte. [7] [8] [9] [10]

### 3. SINEE – Free Hard Techno & Industrial Samples

- **Angebot und Relevanz:** Produktseite mit 0,00 € und Hard-Techno-/Industrial-Samples: 64 Loops (u. a. Atmo/Pad, Bass, FX, Kick, Percussion, Synth, Tops) und 46 One-Shots. Stilistisch sehr passend. [11]
- **Streams und Monetarisierung:** Die allgemeinen AGB nennen grundsätzlich geschäftliche Nutzung, vorbehaltlich der Produktbeschreibung. Songs, Streams oder monetarisierte Streams werden auf der geprüften Produktseite nicht ausdrücklich geregelt. AGB 6.3 bindet Wirksamkeit an vollständige Zahlung geschuldeter Vergütung; die Anwendung dieser Klausel auf das 0,00-€-Produkt wird nicht eigens erläutert. Keine eindeutige produktspezifische Monetarisierungsfreigabe belegt. [12]
- **Rohdatei-Einbettung:** AGB 6.2 untersagen Weitergabe an Dritte bzw. Kopien für Dritte, soweit SINEE einer Lizenzübertragung nicht zustimmt. APK-/App-Bibliotheksdistribution der Roh-Audios ist damit nicht freigegeben; eine ausdrückliche schriftliche Sonderzustimmung wäre erforderlich. [12]
- **Offizieller Downloadweg:** Produktseite führt über „In den Warenkorb“ und „Jetzt kaufen“ bei 0,00 €. AGB nennen Download oder E-Mail als digitale Lieferwege. Bestellvorgang wurde nicht ausgeführt; kein offener Direktdatei-Link belegt. [11] [12]
- **Automatisierbarkeit:** Keine API oder Berechtigung zu automatisierten Downloads auf den geprüften offiziellen Seiten dokumentiert. Shop-Buttons belegen nur einen regulären Bestellweg. [11] [12] [13]
- **Empfehlung:** **Manuelle Lizenzprüfung erforderlich; nicht automatisieren.** Vor Nutzung eine schriftliche, produktbezogene Lizenz für die App-Verteilung der Rohdateien und eine Klärung der kommerziellen Nutzung einholen. [11] [12]

### 4. On Point Samples

- **Angebot und Relevanz:** Die angegebene Freebies-Kollektion zeigte bei Prüfung 0 Packs. Andere offizielle Gratisangebote umfassen einen Harder-Style-Freebie-Vault mit Hardstyle-Screech-/Drum-Loops, Fills, Kicks und weiteren harten Dance-Inhalten sowie eine separate YouTube-Freebies-Sammlung mit 14 Produkten. Relevanz besonders für Hardstyle/Hard Dance; nicht jedes Angebot ist ein Audio-Loop. [14] [15] [16]
- **Streams und Monetarisierung:** EULA und FAQ erlauben Samples in eigener Musik, auch kommerziell; die FAQ nennt Live-Auftritte und Streams mit solcher Musik. Die FAQ sagt, Gratisdownloads unterlägen denselben Regeln wie bezahlte Packs. Eine ausdrücklich auf Monetarisierung jedes Streamingformats bezogene Zusatzregel ist nicht klar ausgewiesen. [17] [18]
- **Rohdatei-Einbettung:** EULA untersagt Teilen/Weiterverbreiten der Samples einzeln, gebündelt oder isoliert, einschließlich Soundbibliotheken und konkurrierender Produkte. Eine App-Ausnahme ist nicht dokumentiert. [18]
- **Offizieller Downloadweg:** Harder-Style-Vault mit offiziellen „Free Download“-Links; neuere Gratisangebote über Produktseiten, Kundenbibliothek bzw. Bestätigungslink. Für die angegebene Kollektion selbst waren keine Packs gelistet. Kein Audio wurde geladen. [15] [16] [17]
- **Automatisierbarkeit:** Keine offizielle API oder Download-Automatisierungserlaubnis belegt. Terms untersagen automatisierte Scraping-/Extraktionstools; die beschriebenen Zugänge sind manuelle Downloads. [19]
- **Empfehlung:** **Nicht geeignet** als Rohdatei-Bibliothek in einer App. Fertige Musiknutzung ist davon getrennt grundsätzlich erlaubt; eine Integration der Rohsamples erfordert ausdrückliche Sonderlizenz. [18] [19]

### 5. SoundShockAudio – Techno Sample Packs / Touch Loops 2026 Free Sampler

- **Angebot und Relevanz:** Katalogseite bewirbt 55 kostenlose Techno-Einträge, darunter Packs und weitere Tools. Das geprüfte Beispiel Touch Loops 2026 Free Sampler enthält 254 tempo-markierte Loops und 20 One-Shots, zusätzlich Presets, in mehreren Genres einschließlich Techno. Die Touch-Loops-Seite zeigte £0.00, zugleich aber „Sold out“; aktuelle Verfügbarkeit daher unklar. Hardstyle-spezifische Verfügbarkeit des geprüften Beispiels ist nicht belegt. [20] [22] [23]
- **Streams und Monetarisierung:** Die Touch-Loops-Lizenz-FAQ erlaubt kommerzielle Veröffentlichung fertiger eigener Kompositionen auf Streamingplattformen ohne laufende Tantiemen an Touch Loops. Monetarisierte Livestreams werden nicht eigens geregelt; isoliertes Streaming der Rohsamples ist keine daraus ableitbare Erlaubnis. Für andere Verzeichniseinträge gilt jeweils die Creator-Lizenz. [23] [24]
- **Rohdatei-Einbettung:** Touch Loops untersagt Rohdateien weiterzuverteilen, weiterzuverkaufen oder unterzulizenzieren sowie als Sample-/Loop-/DAW-Bibliothek anzubieten. Keine App-Ausnahme belegt. [24]
- **Offizieller Downloadweg:** Verzeichnis führt zum Pack, dieses verweist auf die Touch-Loops-Shopify-Produktseite. Dort regulärer Warenkorbweg, aber aktuell „Sold out“; Gratisbestellung/Dateibereitstellung nicht zugesichert. Kein Download wurde ausgeführt. [20] [22] [23]
- **Automatisierbarkeit:** Keine offizielle Download-API oder Automatisierungsfreigabe gefunden. SoundShockAudio untersagt Scraping/automatisiertes Extrahieren von Website-Daten; die Bedingungen verlangen Beachtung der jeweiligen Creator-Lizenz. [21]
- **Empfehlung:** **Nicht geeignet** für Rohdatei-Einbettung und derzeit auch nicht verlässlich verfügbar. Nur nach separater App-/Redistributionslizenz und bestätigter Verfügbarkeit neu prüfen. [21] [23] [24]

### 6. SoundShockAudio – Hardstyle Sample Packs

- **Angebot und Relevanz:** Kategorieseite listet sechs kostenlose Packs, darunter Hardstyle, Screechhouse, Future Rave und Frenchcore. Beispiele enthalten Construction Kits, Drum-Loops, One-Shots, MIDI und Presets. Das passt zu Hardstyle/Hard Dance; nicht alles sind direkt verwendbare Audio-Loops. [25] [26] [27]
- **Streams und Monetarisierung:** Keine pauschale Freigabe für alle Einträge. Myloops beschreibt royalty-free Nutzung „in your productions“, aber ohne ausdrückliche Klarstellung zu monetarisierten Streams. Für mehrere weitere Packs fehlt auf den geprüften Seiten eine konkrete kommerzielle Lizenz. Creator-Lizenz pro Pack maßgeblich. [26] [27] [28]
- **Rohdatei-Einbettung:** Keine geprüfte Creator-Lizenz erteilt ausdrücklich das Einbetten/Weitergeben von Rohdateien in einer APK oder Bibliothek. Vor App-Bündelung ist die ausdrückliche Erlaubnis des jeweiligen Rechteinhabers erforderlich. [26] [27] [21]
- **Offizieller Downloadweg:** Manuell von der Kategorieseite zum jeweiligen Produkt und dessen Download-/Originalquelle. Die Textansicht belegte keinen direkten Ziel-Link; Kontoanforderungen waren nicht eindeutig. Kein Download wurde ausgeführt. [25] [21] [28]
- **Automatisierbarkeit:** Keine API oder Automatisierungsfreigabe dokumentiert. Websitebedingungen untersagen automatisiertes Scraping/Extraktion und Kopieren zur Weiterverteilung. [21]
- **Empfehlung:** **Manuelle Lizenzprüfung erforderlich.** Kostenloser Download oder allgemeines „royalty-free“ genügt nicht für kommerzielle Streams und App-Rohdateien. Pro Pack Rechte beim Creator klären; keine automatisierte Beschaffung auf Basis der vorhandenen Belege. [26] [27] [21] [28]

### 7. MusicRadar / SampleRadar – 560 Warehouse-Techno Samples

- **Angebot und Relevanz:** 560 WAV-Samples als ZIP (222 MB), darunter vier tempo-markierte Loop-Sets bei 125–128 BPM und Drum-Hits. Direkt passend für Warehouse-Techno; Hardstyle nicht ausdrücklich genannt. [29]
- **Streams und Monetarisierung:** Artikel sagt, Samples könnten in Musik verwendet werden; eine ausdrückliche Regel zu monetarisierten Streams fehlt. Allgemeine UK-Site-Bedingungen schränken kommerzielle Nutzung von Website-Material ohne Lizenz ein; kommerzielle Nutzung daher nicht zweifelsfrei geklärt. [29] [31]
- **Rohdatei-Einbettung:** SampleRadar fordert ausdrücklich, die Samples nicht weiterzuverbreiten. APK oder zugängliche App-Bibliothek mit den Rohdateien ist nicht freigegeben. [29] [30]
- **Offizieller Downloadweg:** Direkter offizieller CDN-ZIP-Link vom Artikel; manueller Browser-Download, kein Konto auf der Artikelseite als Voraussetzung genannt. Der Link wurde nur als Quelle geprüft; keine Datei heruntergeladen. [29] [32]
- **Automatisierbarkeit:** Keine API oder Automatisierungserlaubnis belegt. Future-UK-Bedingungen untersagen automatisierte Bots/Tools zum Zugriff, Abruf oder Kopieren von Inhalten. [31]
- **Empfehlung:** **Nicht geeignet** für automatische Beschaffung und App-Rohdateiverteilung. Nur nach schriftlicher Sonderlizenz, einschließlich geklärter kommerzieller Nutzungsrechte, erneut prüfen. [29] [31]

### 8. MusicRadar / SampleRadar – 270 Rhythmic Noise Samples

- **Angebot und Relevanz:** Die angegebene Artikelseite lieferte aktuell 404; Packinhalt, Dateiliste und derzeitiger Download sind damit nicht verifizierbar. Der Titel legt Rhythmus-/Noise-Texturen nahe, aber konkrete Eignung als Techno-/Hardstyle-Loop ist nicht belegt. [33]
- **Streams und Monetarisierung:** Allgemeine SampleRadar-Aussage erlaubt Nutzung in eigener Musik; monetarisierte Streams werden nicht ausdrücklich genannt. Für das nicht verfügbare konkrete Pack ist keine produktspezifische Lizenzprüfung möglich. [30] [31]
- **Rohdatei-Einbettung:** Allgemeine SampleRadar-Regel untersagt Weiterverbreitung der Samples. Keine App-Ausnahme oder Rohdatei-Lizenz belegt. [30]
- **Offizieller Downloadweg:** Für den nicht abrufbaren Artikel kein aktueller offizieller Downloadweg bestätigbar. Bei anderen aktiven SampleRadar-Artikeln werden direkte offizielle ZIP-Links angeboten; das ist kein Nachweis für diesen Pack. Es wurde nichts heruntergeladen. [33] [30]
- **Automatisierbarkeit:** Keine API/Automatisierungserlaubnis belegt; Future-UK-Bedingungen untersagen automatisierte Text-/Data-Mining- und Scraping-Zugriffe. [31]
- **Empfehlung:** **Manuelle Lizenzprüfung erforderlich, aktuell nicht nutzbar belegt.** Erst wenn der konkrete Pack und eine offizielle Downloadquelle wieder verfügbar sind, Rechte einschließlich App-Rohdateien und kommerzieller Nutzung schriftlich klären. [33] [30] [31]

### 9. MusicRadar / SampleRadar – 505 Rave Synth Samples

- **Angebot und Relevanz:** Die aktuell erreichbare offizielle Packseite beschreibt ein kostenloses ZIP mit über 500 WAV-Sounds (236 MB): Acid-Basslines, Rave-Pads/-Pianos, Stabs, Leads und Beats. Sehr passend für Rave/Acid/Old-School-Techno; Hardstyle nicht als Zielgenre ausgewiesen. [34]
- **Streams und Monetarisierung:** Erlaubt ist laut Packseite die Nutzung der royalty-free Samples in eigener Musik. Monetarisierte Streams oder bestimmte Plattformen werden nicht ausdrücklich genannt. [34] [30]
- **Rohdatei-Einbettung:** SampleRadar untersagt Weiterverbreitung der Samples; eine App-Bibliothek mit Rohdateien wäre nicht freigegeben. [34] [30]
- **Offizieller Downloadweg:** Normaler manueller Link von der Packseite zum MusicRadar/Future-CDN-ZIP; kein Konto als Voraussetzung auf der Packseite genannt. Keine Datei wurde heruntergeladen. [34] [35]
- **Automatisierbarkeit:** Keine Download-API oder Automatisierungsfreigabe dokumentiert. UK-Site-Bedingungen untersagen automatisierte Bots/Tools zum Zugriff oder Kopieren von Inhalten. [31]
- **Empfehlung:** **Nicht geeignet** für eine Rohdatei-App-Bibliothek. Nutzung in eigenen Songs ersetzt keine Weiterverteilungslizenz. [34] [30]

### 10. MusicRadar / SampleRadar – 503 Free Techno Samples (Legacy-Link)

- **Angebot und Relevanz:** Angegebener Artikel ist aktuell 404. Der offizielle Hub führt jedoch einen „503 free techno samples“-Eintrag und beschreibt SampleRadar-Sammlungen allgemein als WAV-Downloads. Konkreter Packinhalt, heutige Verfügbarkeit und Hardstyle-Eignung sind nicht verifizierbar. [36] [30] [37]
- **Streams und Monetarisierung:** Allgemeiner Hub gestattet Nutzung der Samples in Musik, untersagt aber Weiterverbreitung. Monetarisierte Streams werden nicht ausdrücklich benannt; wegen fehlender Packseite keine produktspezifische Bestätigung. [30]
- **Rohdatei-Einbettung:** Allgemeine SampleRadar-Regel untersagt Weiterverteilung; keine App-Ausnahme belegt. [30]
- **Offizieller Downloadweg:** Der Hub verlinkt auf den Legacy-Artikel, dessen konkreter Downloadinhalt nicht zugänglich war. Es wurde nichts heruntergeladen. [36] [30] [37]
- **Automatisierbarkeit:** Keine dokumentierte API oder Automatisierungsfreigabe; Future UK Terms untersagen automatisierte Zugriffe und Scraping. [31]
- **Empfehlung:** **Nicht geeignet** für geplante Einbettung/Automatisierung; nur nach Wiederverfügbarkeit und ausdrücklicher Lizenzklärung erneut bewerten. [36] [30] [31]

### 11. WAVLoft – Hardstyle-Verzeichnis

- **Angebot und Relevanz:** Verzeichnis listet 224 Hardstyle-Packs, davon sieben kostenlos. Inhalte umfassen u. a. Jumpstyle-/Hardstyle-Melodien, Hardcore-Kicks, One-Shots und Presets. Ein als Loop gelistetes Paket besteht tatsächlich aus MIDI-Melodien und Presets, nicht aus Audio-Loops. Die Dateien werden bei den jeweiligen Entwicklern bezogen, nicht von WAVLoft gehostet. [38] [41]
- **Streams und Monetarisierung:** WAVLoft beschreibt royalty-free-Lizenzen für eigene Musik einschließlich kommerzieller Releases. Ghosthack erlaubt für eigene fertige Musik ausdrücklich Streaming und YouTube-Monetarisierung, mit Content-ID-Einschränkungen. Für W.A. Production belegt die Produktseite nur „100% Royalty Free“; für alle gelisteten Packs gibt es keine pauschale Stream-Freigabe. [40] [41] [42]
- **Rohdatei-Einbettung:** WAVLoft Creator Terms untersagen Verkauf, Teilen und Repackaging der Sounds; Ghosthack untersagt Rohsample-Redistribution. Keine App-Ausnahme oder Sublicensing-Lizenz belegt. [40] [42]
- **Offizieller Downloadweg:** WAVLoft verweist auf Anbieter. W.A. Production leitet über „Free Download“ zu einem Fangate; Ghosthack verlangt für ein Gratisangebot Vorname und E-Mail und kündigt Downloadlink per E-Mail an. Kein Download oder Formular wurde ausgeführt. [41] [42]
- **Automatisierbarkeit:** Keine API oder Erlaubnis zu automatisierten Downloads dokumentiert. WAVLoft untersagt bestimmtes Scraping bzw. Umgehen von Limits/Zugriffskontrollen; die Anbieterwege sind manuelle Formulare oder persönliche Downloadlinks. [39]
- **Empfehlung:** **Nicht geeignet** für Rohdatei-Verteilung in einer App; für einzelne fertige Musikproduktionen können einzelne Packs nutzbar sein. Erforderlich wären pro Rechteinhaber eine ausdrückliche App-/Redistributionslizenz und Klärung der konkreten Streamrechte. [39] [40] [42]

### 12. Ghosthack

- **Angebot und Relevanz:** Katalog wirbt mit über 10.000 kostenlosen Sounds in 346 Packs und über 40 GB; darunter WAV, Loops, One-Shots, MIDI, Presets und SFX. Die Techno-Kategorie enthält laut offizieller Seite über 1.000 Sounds, darunter Warehouse-, Melodic- und Dub-Techno-Material. Ein Rave-Melody-Kit richtet sich an Rave, Techno und Hard Dance. Hohe Techno-Relevanz; Inhalte und Verfügbarkeit bleiben packindividuell. [43] [44] [45] [46]
- **Streams und Monetarisierung:** FAQ erlaubt kommerzielle Projekte sowie Verkauf und Streaming fertiger Musik auf Plattformen wie Spotify/YouTube; YouTube-Kanal-Monetarisierung ist ausdrücklich erlaubt. Content ID soll für die betroffenen Inhalte deaktiviert bleiben. Das bezieht sich auf fertige Musik/Medienprojekte, nicht auf isolierte Rohsamples. [42]
- **Rohdatei-Einbettung:** FAQ untersagt Übertragen bzw. Weiterverbreiten der Rohsamples außerhalb der Einbindung in ein fertiges Musik-/Medienprojekt. Eine APK oder zugängliche Sample-Bibliothek ist damit nicht freigegeben. Packindividuelle Lizenzvereinbarungen können bestehen; ohne Prüfung einer konkreten Vereinbarung keine weitergehenden Rechte annehmen. [42]
- **Offizieller Downloadweg:** Der Katalog führt zu einzelnen Pack-Seiten. Beim Warehouse-Techno-Beispiel erfolgt ein Gratis-Download über ein offizielles Formular mit Vorname und E-Mail; Ghosthack sendet den Downloadlink per E-Mail. Kein Formular wurde ausgefüllt und kein Audio heruntergeladen. [46]
- **Automatisierbarkeit:** Keine öffentliche API oder automatisierte Downloadberechtigung auf den geprüften Seiten dokumentiert. Beschrieben sind manuelle Formulare bzw. persönliche Downloadlinks. [42] [46]
- **Empfehlung:** **Nicht geeignet** für automatisierten Bezug und Rohdatei-Bibliothek. Für fertige Musik ist kommerzielle Nutzung grundsätzlich belegt; App-Distribution erfordert eine ausdrückliche Sondergenehmigung bzw. eine eindeutig abweichende Packlizenz. [42]

## Freesound-API: Voraussetzungen für die Integration

Die API unterstützt eine persönliche Token-Authentifizierung für nicht geschützte Endpunkte. Dafür wird je Anwendung ein API-Zugang in einem Freesound-Konto beantragt. Der Suchaufruf kann den Token als `Authorization: Token …`-Header mitsenden. Für Sound-Dateien reicht dieser Token nicht: `GET /apiv2/sounds/{sound_id}/download/` verlangt OAuth2 mit `Authorization: Bearer …`. Der dokumentierte Authorization-Code-Ablauf erfordert Client-ID und Client-Secret; der Einmalcode gilt 10 Minuten, Access Tokens 24 Stunden, anschließend kann das Refresh Token ein neues Access Token anfordern. [10] [47]

Die App speichert Freesound-Uploader, Sound-ID, Originallink und Lizenz zu jedem geladenen Sample. Das ist eine Provenienz-/Credit-Hilfe, aber keine automatische Veröffentlichung der Namensnennung an das Live-Publikum. CC BY darf nur eingesetzt werden, wenn die verlangte Attribution zusätzlich in der konkreten Veröffentlichung bzw. im Stream zugänglich gemacht wird; andernfalls CC0 verwenden. [7] [10]

Für die Suche ist der aktuelle Endpunkt `/apiv2/search/` zu verwenden; `/apiv1/search/text` ist seit November 2025 veraltet. Die API erlaubt Lizenz- und Dateitypfilter sowie die Felder `license`, `type`, `duration`, `filesize`, `username`, `url` und `previews` in einem Ergebnisaufruf. Die ausgewiesenen Lizenzwerte sind `Creative Commons 0`, `Attribution` und `Attribution NonCommercial`; ein sicherer Standard kann CC0 verwenden und CC BY nur mit gespeicherter Namensnennung zulassen. Downloadbare Sounds sind vor dem Speichern nochmals gegen Metadaten und lokale WAV-Gültigkeit zu prüfen. [10]

Die API-Bedingungen erlauben nur den Zugriff mit den von Freesound ausgegebenen Zugangsdaten, können Anfragevolumen begrenzen und sagen, dass kommerzielle API-Nutzung im Einzelfall mit UPF verhandelt wird. Eine lokale Android-App kann Client-Zugangsdaten nicht vollständig vor dem Geräteinhaber verbergen; sie dürfen daher nicht in Quellcode oder APK fest eingebaut werden. Vor produktiver kommerzieller Nutzung sind die API-Freigabe und die jeweilige Inhaltslizenz zu klären. [9] [47]

## Sichere technische Empfehlung

1. **Kein Scraping und keine Umgehung von Schutzmaßnahmen.** Keine Browser-Bots, Scraper, automatisierte Extraktion, Umgehung von Downloadlimits oder nicht dokumentierten Direkt-Endpunkten einsetzen. Automatisierung nur über ausdrücklich dokumentierte und erlaubte Anbieter-APIs bzw. Downloadwege.
2. **Freesound nur nach bestätigter Freigabe und API-Key.** Freesound hat zwar eine offizielle API mit Download-Endpunkt, aber ein API-Key/OAuth ist Voraussetzung und kommerzielle API-Nutzung ist nicht pauschal freigegeben. Vor dem Einsatz in einem kommerziellen/monetarisierten App-Kontext die Freigabe mit Freesound klären. Nur Sounds mit passender, einzeln geprüfter Lizenz automatisiert abrufen; insbesondere CC BY-NC nicht als kommerziell nutzbar behandeln. [7] [9] [10]
3. **Direkte Anbieterdownloads ausschließlich über offiziell erlaubte Links.** Wo Anbieter nur manuelle Downloads, Bestellvorgänge, Formulare oder E-Mail-Links dokumentieren, nicht eigenmächtig automatisieren. Ein öffentlich erreichbarer Datei-Link ist für sich keine Automatisierungserlaubnis.
4. **Lokal speichern, Provenienz und Rechte festhalten.** Für jeden Sound mindestens Anbieter und Original-URL, Sound-/Pack-ID, Abrufdatum, Rechteinhaber soweit bekannt, exakte Lizenzversion/URL, erlaubte Nutzungsarten, Attributionstext, Einschränkungen, API-/Downloadgrundlage und Prüfergebnis mitführen. Eine persönliche lokale Cache-Kopie ist von einer Verteilung an App-Nutzer zu unterscheiden; keine Samples ungeprüft in die APK bündeln oder aus der App exportierbar machen.
5. **Vor dem Livestream vorladen.** Downloads sollten nicht erst beim Live-Ereignis beginnen. Erst lokal suchen, Lizenz prüfen, WAV laden/analysieren und für das Set bereitstellen; eine Auslösung kann dann nur bereits gespeicherte Sounds starten.
6. **TikTok-Auslösung bleibt `UNAVAILABLE`.** Eine offizielle, autorisierte TikTok-Auslösung ist nicht belegt. Daher nur eine lokale Testauslösung vorsehen oder später eine offiziell bestätigte API bzw. autorisierte Integration verwenden. Keine inoffizielle TikTok-Automatisierung als Ersatz.
7. **Abspielen erst nach Autorisierung.** Lokale Wiedergabe darf nur nach einem ausdrücklich gestarteten Auto-Set oder einem vom Nutzer aktivierten Auto-Start beim Öffnen beginnen. Diese Mixer-Automation ist keine TikTok-Auslösung; Tests lokal und ohne externe Plattform durchführen. Kein Anbieterstatus in diesem Bericht begründet eine automatische Drittplattform-Auslösung.

## References

[1]: https://www.looperman.com/ "Looperman – Startseite"
[2]: https://www.looperman.com/help/terms "Looperman – Terms & Conditions"
[3]: https://www.looperman.com/help/faq/loops "Looperman – Help with loops FAQ"
[4]: https://www.looperman.com/loops/genres/free-techno-loops-samples-sounds-wavs-download "Looperman – Free Techno Loops"
[5]: https://www.looperman.com/loops/genres/free-hardstyle-loops-samples-sounds-wavs-download "Looperman – Free Hardstyle Loops"
[6]: https://freesound.org/search/?q=techno+loop "Freesound – Suche: techno loop"
[7]: https://freesound.org/help/faq/ "Freesound – FAQ: Lizenzen und Downloads"
[8]: https://freesound.org/help/tos_web/ "Freesound – Terms of Service"
[9]: https://freesound.org/help/tos_api/ "Freesound – API Terms of Use"
[10]: https://freesound.org/docs/api/resources_apiv2.html "Freesound API v2 – Download Sound"
[11]: https://sinee.de/free-hard-techno-samples "SINEE – Free Hard Techno & Industrial Samples"
[12]: https://sinee.de/informationen/allgemeine-geschaeftsbedingungen/ "SINEE – Allgemeine Geschäftsbedingungen"
[13]: https://sinee.de/informationen/faq/ "SINEE – FAQ"
[14]: https://onpointsamples.com/collections/freebies "On Point Samples – Freebies-Kollektion"
[15]: https://onpointsamples.com/pages/freebies "On Point Samples – Harder-Style Freebie Vault"
[16]: https://onpointsamples.com/collections/youtube-freebies "On Point Samples – YouTube Freebies"
[17]: https://onpointsamples.com/pages/support "On Point Samples – Support / FAQ"
[18]: https://onpointsamples.com/pages/eula "On Point Samples – EULA"
[19]: https://onpointsamples.com/ "On Point Samples – Website/Terms"
[20]: https://soundshockaudio.com/techno-sample-packs/ "SoundShockAudio – Free Techno Sample Packs"
[21]: https://soundshockaudio.com/terms-of-service/ "SoundShockAudio – Terms of Use"
[22]: https://soundshockaudio.com/2026-free-sampler/ "SoundShockAudio – 2026 Free Sampler"
[23]: https://touchloops.com/products/2026-free-sampler "Touch Loops – 2026 Free Sampler"
[24]: https://touchloops.com/pages/licensing-faq "Touch Loops – Royalty-Free Licensing FAQ"
[25]: https://soundshockaudio.com/hardstyle-sample-packs/ "SoundShockAudio – Free Hardstyle Sample Packs"
[26]: https://soundshockaudio.com/hardstyle-sample-pack/ "SoundShockAudio – Hardstyle Sample Pack / Myloops"
[27]: https://soundshockaudio.com/screechhouse-hardstyle-sample-pack/ "SoundShockAudio – Screechhouse Hardstyle Sample Pack"
[28]: https://soundshockaudio.com/ "SoundShockAudio – FAQ"
[29]: https://www.musicradar.com/news/sampleradar-free-warehouse-techno-samples "MusicRadar – 560 free warehouse techno samples"
[30]: https://www.musicradar.com/news/tech/free-music-samples-royalty-free-loops-hits-and-multis-to-download-sampleradar "MusicRadar – SampleRadar: kostenlose Musik-Samples"
[31]: https://futureplc.com/terms-and-conditions-uk/ "Future plc – UK Site Terms and Conditions"
[32]: https://cdn.mos.musicradar.com/audio/samples/musicradar-warehouse-techno-samples.zip "MusicRadar – Warehouse-Techno-ZIP (offizieller Downloadlink)"
[33]: https://www.musicradar.com/news/sampleradar-270-free-rhythmic-noise-samples "MusicRadar – 270 free rhythmic noise samples (derzeit 404)"
[34]: https://www.musicradar.com/music-tech/samples/sampleradar-505-free-rave-synths-samples "MusicRadar – SampleRadar: 505 free rave synths samples"
[35]: https://cdn.mos.musicradar.com/audio/samples/musicradar-rave-synths-samples.zip "MusicRadar – Rave-Synth-ZIP (offizieller Downloadlink)"
[36]: https://www.musicradar.com/news/sampleradar-free-techno-samples "MusicRadar – SampleRadar Techno-Seite (aktuell 404)"
[37]: https://www.musicradar.com/news/tech/sampleradar-503-free-techno-samples-218884 "MusicRadar – SampleRadar: 503 free techno samples (Hub-Eintrag)"
[38]: https://wavloft.app/sounds/genre/hardstyle/ "WAVLoft – Hardstyle-Übersicht"
[39]: https://wavloft.app/terms/ "WAVLoft – Terms"
[40]: https://wavloft.app/creators/terms/ "WAVLoft – Creator Terms"
[41]: https://www.waproduction.com/sounds/view/free-hardstyle-melodies-bundle "W.A. Production – Free Hardstyle Melodies Bundle"
[42]: https://www.ghosthack.de/faq/ "Ghosthack – FAQ / Lizenzhinweise"
[43]: https://www.ghosthack.de/free_sample_packs/ "Ghosthack – Free Sample Packs"
[44]: https://www.ghosthack.de/Service/Free-Sample-Packs/Techno/ "Ghosthack – Techno Free Sample Packs"
[45]: https://www.ghosthack.de/free_sample_packs/free-rave-melody-kits "Ghosthack – Free Rave Melody Kits"
[46]: https://www.ghosthack.de/free_sample_packs/advent-calendar-2025-day-1-free-warehouse-techno-sounds-2026 "Ghosthack – Free Warehouse Techno Sounds 2026"
[47]: https://freesound.org/docs/api/authentication.html "Freesound API v2 – Authentication and OAuth2"
