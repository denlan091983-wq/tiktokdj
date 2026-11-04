import { Button } from "@/components/ui/button";
import { Loader2 } from "lucide-react";

import { ArrowDownToLine, AudioLines, Disc3, ExternalLink, Headphones, Radio, RadioTower, ShieldCheck, Volume2, Waves } from "lucide-react";
import { Link } from "wouter";
import { trpc } from "@/lib/trpc";

function formatDuration(totalSeconds: number) {
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
}

function formatSize(bytes: number) {
  return `${(bytes / (1024 * 1024)).toFixed(bytes < 10 * 1024 * 1024 ? 1 : 0)} MB`;
}

function BrandMark() {
  return (
    <span className="brand-lockup" aria-label="LÄRMBATZ Radio">
      <span className="brand-glyph" aria-hidden="true"><AudioLines size={21} strokeWidth={2.6} /></span>
      <span><strong>LÄRMBATZ</strong><small>RADIO / 001</small></span>
    </span>
  );
}

export default function Home() {
  const archive = trpc.recordings.list.useQuery(undefined, { retry: 1 });
  const recordings = archive.data ?? [];

  return (
    <div className="site-shell">
      <header className="topbar">
        <Link href="/" className="brand-link"><BrandMark /></Link>
        <nav aria-label="Hauptnavigation" className="top-nav">
          <a href="#radio">Radio</a>
          <a href="#archiv">Archiv <span className="nav-count">{recordings.length.toString().padStart(2, "0")}</span></a>
        </nav>
        <Button variant="outline" className="studio-link" onClick={() => { window.location.href = "/studio"; }}><ShieldCheck size={15} /> STUDIO <span aria-hidden="true">↗</span></Button>
      </header>

      <main>
        <section className="intro-band">
          <div className="intro-kicker"><span className="status-dot" /> INDEPENDENT HARD TECHNO RADIO <span className="intro-index">BERLIN / ONLINE</span></div>
          <h1>LÄRM<br /><em>BLEIBT.</em></h1>
          <div className="intro-bottom">
            <p>Ungefilterte Mixe aus dem LÄRMBATZ-Mixer.<br />Direkt aufgenommen. Nur mit Freigabe veröffentlicht.</p>
            <a className="scroll-link" href="#radio">RUNTER ZUM SIGNAL <span>↓</span></a>
          </div>
        </section>

        <section id="radio" className="radio-section">
          <div className="section-label"><span>01 / LIVE-FREQUENZ</span><span className="section-line" /><span>STATUS</span></div>
          <div className="live-panel">
            <div className="live-copy">
              <div className="live-badge offline"><span className="status-dot" /> OFFLINE</div>
              <h2>NO SIGNAL.<br /><span>NO FAKE.</span></h2>
              <p>Hier läuft ausschließlich der echte LÄRMBATZ-Mastermix, wenn die App ihn aktiv zur Radioquelle sendet. Gerade ist keine Live-Audioquelle verbunden.</p>
              <div className="notice-strip"><RadioTower size={16} /><span>Kein TikTok-Audio: Diese Seite liest keinen TikTok-LIVE-Ton aus.</span></div>
            </div>
            <div className="live-visual" aria-label="Live-Stream derzeit offline">
              <div className="meter-head"><span>MASTER BUS</span><span>—:—</span></div>
              <div className="wave-grid" aria-hidden="true">{Array.from({ length: 29 }, (_, index) => <i key={index} style={{ height: `${9 + ((index * 17) % 25)}%` }} />)}</div>
              <div className="offline-center"><div className="offline-ring"><Radio size={31} /></div><span>WARTET AUF SIGNAL</span></div>
              <div className="meter-footer"><span>STEREO / MIXER-MASTER</span><span>LIVE-INGEST NOCH NICHT AKTIV</span></div>
            </div>
          </div>
          <div className="radio-footnote"><span><Waves size={15} /> AUDIOQUELLE: LÄRMBATZ MASTER</span><span>LIVE-EINSPEISUNG NOCH AUSSTEHEND</span></div>
        </section>

        <section id="archiv" className="archive-section">
          <div className="section-label"><span>02 / FREIGEGEBENE AUFNAHMEN</span><span className="section-line" /><span>DOWNLOAD-ARCHIV</span></div>
          <div className="archive-heading"><div><h2>AUF DEM<br /><em>TAPE.</em></h2><p>Nur Mixe, die ausdrücklich freigegeben wurden.</p></div><div className="archive-count"><strong>{String(recordings.length).padStart(2, "0")}</strong><span>AUFNAHMEN</span></div></div>
          {archive.isLoading ? (
            <div className="archive-state"><Loader2 className="spin-disc" size={23} /> ARCHIV WIRD GELADEN …</div>
          ) : archive.isError ? (
            <div className="archive-state error-state">Das Archiv konnte gerade nicht geladen werden. Bitte später erneut versuchen.</div>
          ) : recordings.length === 0 ? (
            <div className="empty-state"><Headphones size={27} /><div><strong>NO TAPES YET.</strong><p>Hier erscheinen Aufnahmen erst nach einer einzelnen, ausdrücklichen Veröffentlichung im Studio.</p></div><Link href="/studio" className="text-arrow">STUDIO ÖFFNEN <span>↗</span></Link></div>
          ) : (
            <div className="recording-list">
              {recordings.map((recording, index) => (
                <article className="recording-row" key={recording.id}>
                  <div className="record-index">{String(recordings.length - index).padStart(2, "0")}</div>
                  <div className="record-info"><h3>{recording.title}</h3><div className="record-meta"><span>{formatDuration(recording.durationSeconds)}</span><span>{formatSize(recording.sizeBytes)}</span><span>{recording.publishedAt ? new Date(recording.publishedAt).toLocaleDateString("de-DE") : "Freigegeben"}</span></div></div>
                  <div className="record-player"><Volume2 size={16} /><audio controls preload="none" src={`/api/recordings/${recording.id}/stream`} aria-label={`Mix ${recording.title} anhören`} /></div>
                  <a className="download-button" href={`/api/recordings/${recording.id}/download`} aria-label={`${recording.title} herunterladen`}><ArrowDownToLine size={17} /><span>DOWNLOAD</span></a>
                </article>
              ))}
            </div>
          )}
        </section>

        <section className="manifesto-band">
          <div className="manifesto-symbol"><AudioLines size={35} /></div>
          <div><span className="section-label-inline">UNABHÄNGIG. DIREKT. LAUT.</span><p>Keine fremden Feeds, keine heimlichen Mitschnitte. Nur der Mix, den LÄRMBATZ selbst erzeugt — und Aufnahmen, die bewusst geteilt werden.</p></div>
          <a href="#radio" className="back-top">NACH OBEN <span>↑</span></a>
        </section>
        <footer className="site-footer"><BrandMark /><span>© LÄRMBATZ RADIO</span><span>BUILT FOR THE AFTERHOURS <ExternalLink size={13} /></span></footer>
      </main>
    </div>
  );
}
