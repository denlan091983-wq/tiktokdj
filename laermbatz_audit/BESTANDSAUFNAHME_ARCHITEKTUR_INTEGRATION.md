# LÄRMBATZ – Bestandsaufnahme: Architektur und offene Integrationsfragen

**Stand:** 2. November 2026  
**Projekt:** BUILD THE TECHNO / Zielgerät laut Unterlagen: Samsung Galaxy A17 5G (SM-A176B)

## Was im Arbeitsordner tatsächlich vorliegt

Das gelieferte ZIP ist ein Konzept- und Designpaket, kein vollständiges Android-Studio-Projekt. Es enthält Produkt-, UI-, Audio-, TikTok- und Roadmap-Dokumente sowie drei Designentwürfe. Separat wurden `README_SCHRITT1.md`, `LOOP_IMPORT.md` und `audio_engine.cpp` geliefert. Im Arbeitsordner gibt es eine einzelne Root-Gradle-Datei und den nativen C++-Testton, aber kein vollständiges `app`-Modul: Kotlin-Aktivität/JNI-Klasse, App-Gradle-Konfiguration, `settings.gradle(.kts)`, Manifest, CMake-Konfiguration, Wrapper und Tests liegen nicht vor. Der zusätzliche Kopiepfad `app/src/main/cpp/audio_engine.cpp` enthält nur die gelieferte Engine-Datei und macht den App-Build allein nicht möglich.

Damit gilt Schritt 1 als beschrieben und teilweise als C++-Quelltext vorhanden, aber nicht als baubares oder getestetes Android-Projekt. Es gibt hier kein Android SDK/Build-Protokoll und keinen Gerätetest. Die Angaben im README zu Android Studio, A17 und 10-Minuten-Test sind Vorgaben bzw. Abnahmekriterien, keine nachgewiesenen Testergebnisse.

## Empfohlene Zielarchitektur

### 1. Android-App und Bedienoberfläche

Eine einzelne native Android-App mit Kotlin, Jetpack Compose und einem klar getrennten Steuerungs- und Zuschauerbildschirm ist der einfachste passende Start. Compose zeigt den 9:16-Bühnenbildschirm, sechs Spurzustände, Arrangementfortschritt, Eventquellenstatus sowie Start/Pause/Stop und Not-Aus. Die Oberfläche erhält ausschließlich Zustandsmeldungen; sie darf Audio-Callbacks weder aufrufen noch blockieren.

Ein `ShowController` bzw. Use-Case-Layer übersetzt Bedienaktionen und Ereignisse in Audio-Befehle. Ein Repository hält Preset-, Pack- und Mapping-Konfiguration. Die UI zeigt „LIVE“ und Zuschauer-/Eventdaten nur dann an, wenn sie wirklich aus einer bestätigten Quelle stammen. Im Demo-Modus sind synthetische Events sichtbar als Simulation gekennzeichnet.

### 2. Native Audio-Engine mit Oboe

Für eine latenzarme Mehrspur-Wiedergabe ist Oboe eine plausible Wahl: Google beschreibt Oboe als C++-API für Android, die auf API 27+ AAudio verwendet und gerätespezifische Audio-Workarounds bereitstellt [1]. Die sechs Spuren KICK, BASS, HI-HAT, PERCUSSION, SYNTH und EFFECTS werden in einer nativen Engine summiert. Sampledateien werden außerhalb des Echtzeit-Callbacks gelesen, geprüft und in vorbereitete Speicherpuffer dekodiert. Im Callback bleiben nur begrenzte, vorhersehbare Operationen: Sample-Read aus vorbereiteten Buffern, Gain-/Filter-Verarbeitung, Voice-Mix, Limiter und Ausgabe. Keine Datei-I/O, Heap-Allokation, Locks, Kotlin/JNI-Aufrufe, Netzwerkzugriffe oder Logs im Callback.

Das jetzige C++ ist noch kein Loop-Player. Es erzeugt einen Sinus und hält Stream-/Callback-Zustand in globalen Variablen. Vor Ausbau sind Thread-Sicherheit der Streamsteuerung, Fehler- und Wiederanlaufpfad, Geräte-/Audio-Routenwechsel sowie Audiofokus zu entwerfen. Die aktuelle `SharingMode::Exclusive`-Anforderung sollte als Wunsch, nicht als Garantie gelten; der Start muss auch mit Shared-Modus bzw. anderer verfügbarer Streamkonfiguration robust sein. „LowLatency“ ist eine Optimierungsanforderung, kein Versprechen einer festen Latenz auf jedem Gerät [1][2].

### 3. Sample-genauer Transport, Mixer und Arrangement

Die Engine sollte einen monotonen Sample-Frame-Zähler als Transportgrundlage führen. Bei Set-BPM wird ein Beat-/Taktzeitpunkt in Frames abgebildet; eingehende Befehle werden auf den nächsten erlaubten Beat, Takt oder Mehrtaktpunkt quantisiert. Dadurch hängen Loops und Übergänge nicht an UI-Timern. Eine control-threadseitige Arrangement-Logik entscheidet über Intro, Groove, Variation, Break, Build-up, Drop und After-drop. Sie sendet zeitlich geplante, kleine Befehle an eine vorab angelegte Echtzeit-Queue. Übergänge, Layer-Freischaltung und Fills dürfen nicht durch spontanes Öffnen/Decodieren von Dateien ausgelöst werden.

Ein Masterbus sollte zunächst konservatives Headroom, Spur-Gains und einen begrenzenden Schutz gegen digitales Clipping bieten. „Limiter“ ist eine Qualitätsanforderung, die durch Audio-Tests und Messungen belegt werden muss; ein einfacher Soft-Clip darf nicht ungeprüft als transparenter Limiter beworben werden. Für ein erstes MVP sind komplexe Filter, Sends und Sidechain-Modelle nachrangig.

### 4. WAV-Pack-Import und Lizenzmetadaten

Die Importkomponente läuft vollständig außerhalb des Audiothreads. Sie nimmt ein vom Nutzer ausgewähltes Verzeichnis/Pack entgegen, validiert Manifest, Pfade, WAV-Header, Kanäle, Sampleformat, Samplerate und Loop-Länge und erstellt eine Vorschau/Importmeldung. Ein Pack mit `allowed_for_streaming: "unchecked"` ist im Testmodus erlaubt, aber im Live-Modus gesperrt; `no` ist gesperrt. Die App darf fehlende Rechte nicht aus einem Gratis-Download ableiten.

`LOOP_IMPORT.md` verlangt WAV mit 44,1 kHz. Gleichzeitig kann die Audioausgabe eine andere Sample-Rate nutzen. Deshalb ist vor Schritt 2 eine explizite Entscheidung nötig: Entweder 44,1-kHz-Quellen werden beim Import in die tatsächlich verwendete Engine-Rate konvertiert oder die Engine resampelt sie vorab außerhalb des Callbacks. Eine stille Laufzeitumwandlung im Audio-Callback ist ungeeignet. Zusätzlich müssen zulässige WAV-PCM-Varianten (z. B. 16/24/32-bit PCM und ggf. Float), Mono/Stereo, Maximallänge/Packgröße sowie unvollständige/nahtlose Loopgrenzen festgelegt werden.

### 5. Hintergrundbetrieb, Audiofokus und Lebenszyklus

Für dauerhaftes Abspielen bei ausgeschaltetem Bildschirm oder beim Wechsel in andere Apps braucht die Wiedergabe später einen `mediaPlayback`-Foreground-Service samt sichtbarer Benachrichtigung und korrekten Berechtigungs-/Manifestangaben. Android 14+ verlangt deklarierte Foreground-Service-Typen und zugehörige Berechtigungen; `mediaPlayback` ist ausdrücklich für Audio-/Videowiedergabe im Hintergrund vorgesehen [3]. Audiofokus ist ebenfalls Pflicht für gutes Systemverhalten: Starten nur bei erteiltem Fokus, auf Fokusverlust reagieren, bei Ende Fokus freigeben. Ab Android 15 braucht eine targetSdk-35+-App für den Audiofokus den Vordergrund oder einen laufenden Foreground Service [4].

Dies ist im Konzept als späterer Roadmap-Schritt markiert, aber für einen echten Livestream sollte die Service-/Fokus-Grundlage vor einem Langzeittest der Autopilot-Show eingeplant werden. Der Service muss Engine-Lifecycle, Notification-Aktionen, Audiofokus, Telefonunterbrechungen und Abbruch/Neustart koordinieren.

### 6. Ereignis- und Adapterarchitektur

Die Musiklogik erhält keine TikTok-spezifischen SDK-Objekte. Ein `EventSource`-Interface liefert ein neutrales Modell wie `Follow`, `Gift`, `ConnectionState` und Fehler. Ein lokaler `FakeEventSource` ist die erste und verbindlich testbare Quelle. Eine Queue normalisiert, dedupliziert (soweit eine Quelle IDs liefert), begrenzt Bursts, wendet Cooldowns an und übergibt Musikaktionen an die Quantisierung. Bei Disconnect läuft der Autopilot unverändert weiter.

Für echte TikTok-Daten gilt aktuell: TikToks offizielle Webhook-Referenz listet `authorization.removed`, `video.upload.failed`, `video.publish.completed` und `portability.download.ready`, aber keine Live-Geschenk- oder Live-Follow-Ereignisse [5]. TikToks offizielle Entwicklerproduktseite beschreibt u. a. Login, Teilen, Content Posting, Display und Data Portability; ein öffentlich dokumentierter LIVE-Eventstream für die hier benötigten Ereignisse ist dort nicht ausgewiesen [6]. Daher ist eine offizielle, allgemein verfügbare Event-API für dieses Produkt und Konto **nicht nachgewiesen**. Das ist eine Aussage über den geprüften öffentlichen Entwicklerkatalog und keine Behauptung, dass es keine individuellen, regionalen oder Partnerprogramme geben könne.

TikTok LIVE Studio kann laut TikTok den Android-Bildschirm als Cast-Quelle anzeigen. Es bietet außerdem LIVE-Metriken und zeigt im LIVE-Chat Aktivitäten wie Geschenke, Beitritte, Likes, neue Follower und Abos an [7][8]. Das sind Creator-Oberflächen und Streamingfunktionen, keine dokumentierte Schnittstelle, die diese Events an eine Drittanbieter-App weiterreicht. Automatisches Auslesen von Bildschirm, Chat, Cookies, Tokens oder nicht dokumentierten Protokollen wird nicht als Produktionsweg empfohlen. Ein genehmigter Partnerweg müsste vor Verwendung schriftlich/technisch bestätigt werden.

### 7. TikTok-Ausgabe und Capture

Die Zielausgabe ist noch offen: direkt aus der TikTok-App auf demselben Telefon, Telefon-Casting zu TikTok LIVE Studio am PC, oder eine andere zulässige Capture-Lösung. TikTok dokumentiert Android-Casting in LIVE Studio über die TikTok-App und empfiehlt stabile WLAN- oder Kabelverbindung [7]. Dies macht PC/LIVE Studio zu einem belegten Kandidaten für die Bildausgabe, beweist aber nicht, dass die von Oboe erzeugte interne Audioausgabe im Cast zuverlässig und in gewünschter Qualität mit übertragen wird. Das muss mit dem tatsächlichen Telefon, LIVE Studio, Kabel/WLAN und einem Teststream praktisch geprüft werden.

Android `MediaProjection` ist eine System-API für Display-/App-Fenster-Capture und verlangt Nutzerzustimmung; Android 14+ verlangt dafür außerdem den passenden Foreground-Service-Typ [9]. Sie ist nicht automatisch eine TikTok-Sende-API und sollte nicht mit dem offiziellen LIVE Studio Cast verwechselt werden. Vor Umsetzung muss feststehen, ob die App nur die Szene für TikTok bereitstellt, selbst Bildschirm-Capture startet oder auf PC-Casting setzt.

## Architektur-Datenfluss

```text
Compose UI / Bedienung ─────┐
                             ├─> ShowController / Zustandsmodell
FakeEventSource ─────────────┤          │
Später: bestätigter Adapter ─┘          ├─> Arrangement + Quantisierung
                                        ├─> EventQueue / Cooldowns / Dedupe
                                        └─> vorbereitete Audio-Command-Queue
                                                   │
Import/Pack-Validator -> Decoder/Resampler -> Native Oboe-Engine
                                                   │
                                      Oboe-Callback -> Mixer -> Audio-Ausgabe
                                                   │
                                      Clock/Beat-Metadaten -> UI-Visualisierung
```

Der native Audiopfad bleibt unabhängig von Netzwerk und UI. UI-Zustände und Beatvisualisierung sind abgeleitete, rate-begrenzte Meldungen aus der Engine, keine Echtzeit-Taktgeber.

## Offene Entscheidungen vor einem baubaren MVP

1. **Gerät und Betriebssystem:** Ist SM-A176B weiterhin das Ziel? Welche Android-Version/One UI und welche Zielgeräte außer diesem Modell sind vorgesehen?
2. **Ausgabeweg:** Läuft die Show direkt aus der TikTok-App auf demselben Gerät oder wird Android-Bildschirm zu LIVE Studio auf einem PC gecastet? Ist ein zweites Gerät/PC verfügbar? Muss Oboe-Audio extern zuverlässig ankommen?
3. **TikTok-Ereignisse:** Gibt es bereits eine offizielle TikTok-Zusage, einen zugelassenen Partner oder eine Kontofreischaltung für LIVE-Eventdaten? Ohne Bestätigung bleibt Phase 3 Fake/Demo und Phase 4 gesperrt.
4. **Netzwerk und Datenschutz:** Soll eine spätere Eventquelle lokal auf dem Telefon oder über einen vertrauenswürdigen Server verbunden werden? Welche Eventfelder sind erforderlich und wie lange dürfen Namen/Historien gespeichert werden? Geheimnisse eines Servers dürfen nicht im APK liegen.
5. **Audioformat:** Bleibt 44,1 kHz ein Import-Quellformat oder die Engine-Ausgabe-Rate? Welche PCM-Typen, Kanäle, Dateigrößen und Loop-Längen werden unterstützt? Dürfen abweichende BPM-Werte abgelehnt, markiert oder optional offline umgerechnet werden?
6. **Lizenzworkflow:** Wer prüft Lizenzdateien/AGB und setzt `allowed_for_streaming=yes`? Bedeutet „Streaming“ im Projekt ausschließlich TikTok LIVE oder auch andere Plattformen/kommerzielle Nutzung? Die App sollte diese Freigabe protokollieren und nicht selbst rechtlich ableiten.
7. **MVP-Umfang:** Ist das nächste Ziel zunächst ein baubares, offline-fähiges Einspur-WAV-Loop-Player-Projekt oder direkt eine vollständige Sechs-Spur-Grundlage mit Clock, Mixer und Autopilot? Die Roadmap empfiehlt sechs Spuren, der aktuell verfügbare Schritt-1-Quellcode kann dies noch nicht leisten.
8. **Build-Setup:** Welche Android-Studio-/Gradle-/AGP-/Kotlin-Version ist gewünscht und welche NDK/CMake-Version kann lokal installiert werden? Die zuvor genannten Versionen sind unbestätigte Ausgangswerte. Ein erreichbares SDK ist für Build/Tests erforderlich.

## Empfohlene Reihenfolge

Zuerst die Fragen 1–3 und 5–7 entscheiden. Danach ein minimales echtes Android-Projekt mit reproduzierbarem Gradle-Wrapper, Manifest, CMake/Prefab, JNI-Klasse und Oboe-Test aufbauen und auf dem Zielgerät testen. Anschließend den WAV-Import samt Validierungsfällen ergänzen, dann sample-genaue Loop-Wiedergabe und Transport-Clock. Darauf folgen Sechs-Spur-Mixer/Headroom, Arrangement-Autopilot und FakeEventSource. Erst nach Stabilitäts- und Lizenztests werden Visuals und eine bestätigte externe Eventintegration priorisiert. Ein getesteter Hintergrundservice gehört vor den geplanten 30–60-Minuten- und Bildschirm-aus-Abnahmetest.

## Quellen

[1]: https://developer.android.com/games/sdk/oboe "Oboe audio library"
[2]: https://developer.android.com/games/sdk/oboe/low-latency-audio "Low latency audio"
[3]: https://developer.android.com/develop/background-work/services/fgs/service-types#media "Foreground service types"
[4]: https://developer.android.com/media/optimize/audio-focus "Manage audio focus"
[5]: https://developers.tiktok.com/doc/webhooks-events/ "TikTok Webhook Events"
[6]: https://developers.tiktok.com/ "TikTok for Developers"
[7]: https://www.tiktok.com/live/studio/help/article/Get-started-with-your-first-LIVE/Add-a-cast-source-to-share-your-phone-screen "Add a cast source to share your phone screen"
[8]: https://www.tiktok.com/live/studio/help/article/Get-started-with-your-first-LIVE/Learn-the-basics-of-LIVE "Learn the basics of LIVE"
[9]: https://developer.android.com/media/grow/media-projection "Media projection"
