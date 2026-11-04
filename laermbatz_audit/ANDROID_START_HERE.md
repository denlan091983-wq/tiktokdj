# LÄRMBATZ – Android-Projektstart

Das baubare Android-Modul liegt unter `:app`. Der aktuelle Funktions- und Auditstand steht in [AUDIT_ROADMAP.md](AUDIT_ROADMAP.md), der Build- und Artefaktstatus in [CLOUD_BUILD.md](CLOUD_BUILD.md). Der bestehende Audio-MVP enthält WAV-Import, sechs native Oboe-Spuren, BPM-/Takt-Erkennung, WSOLA-Time-Stretch, Effekte, Autopilot, Freesound-Katalog und lokale Loop-Bibliothek.

**Wichtig:** Dies ist eine debug-signierte Audio-/Mixer-App, keine fertige TikTok-Broadcast-App. TikTok-LIVE-Geschenk-/Follow-Ereignisse sind `UNAVAILABLE`. TikTok LIVE Studio Android-Cast ist ein zu testender Ausgabekandidat, aber die zuverlässige Übertragung der Oboe-Audioausgabe ist nicht nachgewiesen. Es gibt keinen physischen Gerätetest in diesem Buildnachweis.

Zum Neubauen in der eingerichteten Cloud-Umgebung:

```bash
./cloud-build.sh
```

Für die nächsten Aufgaben gilt die Reihenfolge in [docs/ROADMAP.md](docs/ROADMAP.md): Geräte- und Audioabnahme, verlässlicher Stream-Ausgabeweg, danach Visualisierung und Release-Härtung. Eine Play-Veröffentlichung ist mit `targetSdk 34` und der aktuellen Debug-Signierung noch nicht möglich.
