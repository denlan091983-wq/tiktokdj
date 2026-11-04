import { useEffect, useState } from "react";
import { ArrowLeft, ArrowUpRight, Check, CircleAlert, FileAudio2, LockKeyhole, LogIn, ShieldCheck, Upload, X } from "lucide-react";
import { Link } from "wouter";
import { toast } from "sonner";
import { startLogin } from "@/const";
import { trpc } from "@/lib/trpc";

type UploadState = "idle" | "uploading" | "ready";
const MAX_BYTES = 125 * 1024 * 1024;
const MAX_DURATION_SECONDS = 60 * 60;
const AAC_FRAME_TOLERANCE_SECONDS = 0.05;
const ALLOWED_TYPES = new Set(["audio/mp4"]);

function formatDuration(totalSeconds: number) {
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
}

function uploadToStorage(uploadUrl: string, file: File, contentType: string, onProgress: (percent: number) => void) {
  return new Promise<void>((resolve, reject) => {
    const request = new XMLHttpRequest();
    request.open("PUT", uploadUrl);
    request.setRequestHeader("Content-Type", contentType);
    request.upload.onprogress = event => {
      if (event.lengthComputable) onProgress(Math.round((event.loaded / event.total) * 100));
    };
    request.onerror = () => reject(new Error("Verbindung zum Audiospeicher fehlgeschlagen."));
    request.onabort = () => reject(new Error("Upload abgebrochen."));
    request.onload = () => {
      if (request.status >= 200 && request.status < 300) resolve();
      else reject(new Error(`Speicher-Upload fehlgeschlagen (${request.status}).`));
    };
    request.send(file);
  });
}

function fileContentType(file: File): "audio/mp4" | null {
  if (ALLOWED_TYPES.has(file.type)) return "audio/mp4";
  const name = file.name.toLowerCase();
  if (name.endsWith(".m4a")) return "audio/mp4";
  return null;
}

export default function Studio() {
  const auth = trpc.auth.me.useQuery();
  const user = auth.data;
  const studioAccess = trpc.recordings.canManage.useQuery(undefined, { enabled: !!user });
  const isAdmin = studioAccess.data?.allowed === true;
  const library = trpc.recordings.studioList.useQuery(undefined, { enabled: isAdmin, retry: 1 });
  const prepare = trpc.recordings.prepareUpload.useMutation();
  const confirm = trpc.recordings.confirmUpload.useMutation();
  const publish = trpc.recordings.publish.useMutation();
  const unpublish = trpc.recordings.unpublish.useMutation();

  const [file, setFile] = useState<File | null>(null);
  const [title, setTitle] = useState("");
  const [duration, setDuration] = useState<number | null>(null);
  const [durationError, setDurationError] = useState("");
  const [uploadState, setUploadState] = useState<UploadState>("idle");
  const [progress, setProgress] = useState(0);
  const [pendingId, setPendingId] = useState<number | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);

  useEffect(() => {
    if (!file) {
      setDuration(null);
      setDurationError("");
      return;
    }
    const url = URL.createObjectURL(file);
    const audio = new Audio();
    audio.preload = "metadata";
    audio.onloadedmetadata = () => {
      const exactSeconds = audio.duration;
      const seconds = Math.round(exactSeconds);
      if (!Number.isFinite(exactSeconds) || exactSeconds < 1) {
        setDurationError("Die Laufzeit konnte nicht gelesen werden.");
        setDuration(null);
      } else if (exactSeconds > MAX_DURATION_SECONDS + AAC_FRAME_TOLERANCE_SECONDS) {
        setDurationError("Die Aufnahme ist länger als 60 Minuten.");
        setDuration(seconds);
      } else {
        setDurationError("");
        setDuration(seconds);
      }
      URL.revokeObjectURL(url);
    };
    audio.onerror = () => {
      setDurationError("Die Datei ist beschädigt oder kein abspielbares M4A mit AAC-Audio.");
      setDuration(null);
      URL.revokeObjectURL(url);
    };
    audio.src = url;
    return () => {
      audio.src = "";
      URL.revokeObjectURL(url);
    };
  }, [file]);

  const selectFile = (nextFile: File | null) => {
    setFile(nextFile);
    setPendingId(null);
    setUploadState("idle");
    setProgress(0);
    if (nextFile) {
      setTitle(nextFile.name.replace(/\.m4a$/i, "").slice(0, 160));
    }
  };

  const handleUpload = async () => {
    if (!file || !duration || !title.trim()) return;
    const contentType = fileContentType(file);
    if (!contentType) {
      setDurationError("Bitte eine .m4a-Datei mit AAC-Audio auswählen.");
      return;
    }
    if (file.size > MAX_BYTES) {
      setDurationError("Datei ist zu groß. Erlaubt sind bis zu 125 MiB.");
      return;
    }
    if (duration > 3600) {
      setDurationError("Maximal 60 Minuten pro Aufnahme.");
      return;
    }

    setUploadState("uploading");
    setProgress(0);
    try {
      const prepared = await prepare.mutateAsync({
        title: title.trim(),
        originalFileName: file.name,
        contentType,
        sizeBytes: file.size,
        durationSeconds: duration,
      });
      setPendingId(prepared.id);
      await uploadToStorage(prepared.uploadUrl, file, prepared.contentType, setProgress);
      await confirm.mutateAsync({ id: prepared.id });
      setUploadState("ready");
      void library.refetch();
      toast.success("Upload geprüft. Die Aufnahme ist noch nicht öffentlich.");
    } catch (error) {
      setUploadState("idle");
      toast.error(error instanceof Error ? error.message : "Upload fehlgeschlagen.");
    }
  };

  const handlePublish = async (id: number, recordingTitle: string) => {
    const approved = window.confirm(`„${recordingTitle}“ jetzt öffentlich zum Anhören und Herunterladen freigeben? Diese einzelne Aufnahme wird auf der Radio-Startseite gelistet.`);
    if (!approved) return;
    setBusyId(id);
    try {
      await publish.mutateAsync({ id });
      if (pendingId === id) {
        setPendingId(null);
        setFile(null);
        setTitle("");
        setDuration(null);
        setUploadState("idle");
      }
      await library.refetch();
      toast.success("Aufnahme ausdrücklich veröffentlicht.");
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Veröffentlichung fehlgeschlagen.");
    } finally {
      setBusyId(null);
    }
  };

  const handleUnpublish = async (id: number) => {
    if (!window.confirm("Diese Aufnahme aus dem öffentlichen Archiv nehmen und weitere Website-Wiedergabe/Downloads sperren? Der Speicher löscht das Objekt nicht physisch; bereits gespeicherte Kopien können nicht zurückgerufen werden.")) return;
    setBusyId(id);
    try {
      await unpublish.mutateAsync({ id });
      await library.refetch();
      toast.success("Aufnahme aus dem öffentlichen Archiv entfernt.");
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Änderung fehlgeschlagen.");
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div className="site-shell studio-shell">
      <header className="topbar">
        <Link href="/" className="brand-link"><span className="brand-lockup"><span className="brand-glyph"><FileAudio2 size={20} /></span><span><strong>LÄRMBATZ</strong><small>RADIO / STUDIO</small></span></span></Link>
        <Link href="/" className="studio-link"><ArrowLeft size={15} /> ZURÜCK ZUM RADIO</Link>
      </header>

      <main className="studio-main">
        <div className="studio-kicker"><span>UPLOAD BAY</span><span className="section-line" /><span>OWNER ACCESS</span></div>
        <div className="studio-heading"><div><h1>DEIN<br /><em>STUDIO.</em></h1><p>Aufnahme auswählen, direkt hochladen, einzeln freigeben. Nichts wird automatisch im öffentlichen Archiv gelistet.</p></div><div className="studio-seal"><LockKeyhole size={24} /><span>NOT LISTED<br />BY DEFAULT</span></div></div>

        {auth.isLoading ? <div className="studio-card"><p>Manus-Anmeldung wird geprüft …</p></div> : !user ? (
          <div className="studio-card access-card"><div className="access-icon"><LockKeyhole size={25} /></div><div><span className="section-label-inline">GESCHÜTZTER BEREICH</span><h2>STUDIO-ZUGANG</h2><p>Bitte mit dem Manus-Konto anmelden, das als Website-Administrator freigeschaltet ist. Öffentliche Besucher können keine Dateien hochladen.</p><button className="action-button" onClick={() => startLogin()}><LogIn size={17} /> MIT MANUS ANMELDEN</button></div></div>
        ) : studioAccess.isLoading ? (
          <div className="studio-card"><p>Studio-Berechtigung wird serverseitig geprüft …</p></div>
        ) : !isAdmin ? (
          <div className="studio-card access-card"><div className="access-icon"><ShieldCheck size={25} /></div><div><span className="section-label-inline">{studioAccess.data?.configured ? `ANGEMELDET ALS ${user.name || "MANUS USER"}` : "OWNER-ZUGRIFF NOCH NICHT EINGERICHTET"}</span><h2>KEIN STUDIO-ZUGRIFF</h2><p>{studioAccess.data?.configured ? "Dieses Manus-Konto ist nicht als Eigentümerkonto für Upload und Veröffentlichung freigeschaltet." : "Der Projekteigentümer muss zuerst das serverseitige WebDev-Secret LARMBATZ_ADMIN_EMAIL hinterlegen. Verwende anschließend genau dieses Manus-Konto. Öffentliche Besucher können keine Dateien hochladen oder veröffentlichen."}</p></div></div>
        ) : (
          <>
            <div className="studio-card upload-card">
              <div className="card-topline"><span>01 / AUFNAHME VORBEREITEN</span><span className="subtle-tag">M4A (AAC) · MAX. 60:00 · MAX. 125 MIB</span></div>
              <label className="field-label" htmlFor="mix-title">TITEL DER AUFNAHME</label>
              <input id="mix-title" className="studio-input" maxLength={160} value={title} onChange={event => setTitle(event.target.value)} placeholder="z. B. Nachtfahrt 001" />
              <label className="file-drop" htmlFor="mix-file">
                <Upload size={23} />
                <strong>{file ? file.name : "M4A-DATEI MIT AAC-AUDIO AUSWÄHLEN"}</strong>
                <span>{file ? `${(file.size / (1024 * 1024)).toFixed(1)} MiB${duration ? ` · ${formatDuration(duration)}` : ""}` : "Lokale Android-Aufnahme · bis 125 MiB"}</span>
                <input id="mix-file" type="file" accept=".m4a,audio/mp4" onChange={event => selectFile(event.target.files?.[0] ?? null)} />
              </label>
              {durationError && <div className="form-error"><CircleAlert size={15} /> {durationError}</div>}
              {uploadState === "uploading" && <div className="upload-progress"><div className="progress-heading"><span>UPLOAD LÄUFT</span><span>{progress}%</span></div><div className="progress-track"><i style={{ width: `${progress}%` }} /></div><p>Direkte Übertragung in den dauerhaften Projektspeicher.</p></div>}
              {uploadState === "ready" && pendingId && <div className="ready-notice"><Check size={16} /> Upload geprüft — noch nicht im öffentlichen Archiv gelistet.</div>}
              <div className="upload-actions">
                <button className="action-button" onClick={handleUpload} disabled={!file || !title.trim() || !duration || !!durationError || uploadState === "uploading" || uploadState === "ready" || prepare.isPending || confirm.isPending}>
                  <Upload size={16} /> {uploadState === "uploading" ? "WIRD HOCHGELADEN …" : uploadState === "ready" ? "UPLOAD BEREIT" : "UPLOAD VORBEREITEN"}
                </button>
                {uploadState === "ready" && pendingId && <button className="publish-button" onClick={() => void handlePublish(pendingId, title)} disabled={busyId === pendingId}><ArrowUpRight size={16} /> DIESE AUFNAHME VERÖFFENTLICHEN</button>}
                {(file || uploadState !== "idle") && <button className="clear-button" onClick={() => { selectFile(null); setTitle(""); }} disabled={uploadState === "uploading"}><X size={15} /> ZURÜCKSETZEN</button>}
              </div>
      <p className="privacy-note"><LockKeyhole size={14} /> Nach deiner separaten Bestätigung wird der Mix im öffentlichen Archiv gelistet. Nicht gelistete Storage-Objekte haben keinen zusätzlichen Passwortschutz; lade daher nur Mixe hoch, die du teilen möchtest. „Ausblenden“ löscht die Datei nicht physisch.</p>
            </div>

            <div className="studio-library">
              <div className="card-topline"><span>02 / DEINE AUFNAHMEN</span><span className="subtle-tag">VERÖFFENTLICHUNG VERWALTEN</span></div>
              {library.isLoading ? <p className="muted-copy">Bibliothek wird geladen …</p> : library.isError ? <p className="muted-copy">Aufnahmen konnten nicht geladen werden.</p> : (library.data ?? []).length === 0 ? <p className="muted-copy">Noch keine Aufnahmen vorhanden. Deine lokale Aufnahme wird erst beim Upload in den Projektspeicher übertragen.</p> : (
                <div className="studio-record-list">{library.data?.map(recording => (
                  <div className="studio-record" key={recording.id}>
                    <div><strong>{recording.title}</strong><span>{formatDuration(recording.durationSeconds)} · {(recording.sizeBytes / (1024 * 1024)).toFixed(1)} MiB · {recording.status === "published" ? "ÖFFENTLICH" : recording.status === "ready" ? "BEREIT / NICHT GELISTET" : "UPLOAD OFFEN"}</span></div>
                    {recording.status === "ready" && <button className="publish-button compact" onClick={() => void handlePublish(recording.id, recording.title)} disabled={busyId === recording.id}><ArrowUpRight size={14} /> VERÖFFENTLICHEN</button>}
                    {recording.status === "published" && <button className="clear-button compact" onClick={() => void handleUnpublish(recording.id)} disabled={busyId === recording.id}><X size={14} /> AUSBLENDEN</button>}
                    {recording.status === "uploading" && <span className="pending-tag">UPLOAD NICHT BESTÄTIGT</span>}
                  </div>
                ))}</div>
              )}
            </div>
          </>
        )}
      </main>

      <footer className="site-footer"><span>UPLOADS SIND NICHT AUTOMATISCH ÖFFENTLICH</span><Link href="/" className="footer-link">RADIO ANSEHEN ↗</Link></footer>
    </div>
  );
}
