import { Readable } from "node:stream";
import { pipeline } from "node:stream/promises";
import type { Express, NextFunction, Request, Response } from "express";
import { and, eq } from "drizzle-orm";
import { getDb } from "./db";
import { recordings } from "../drizzle/schema";
import { storageGetSignedUrl } from "./storage";

function fileName(title: string): string {
  const normalized = title.normalize("NFKD").replace(/[\u0300-\u036f]/g, "");
  const safe = normalized.replace(/[^A-Za-z0-9._-]+/g, "-").replace(/^-+|-+$/g, "").slice(0, 100);
  return `${safe || "laermbatz-mix"}.m4a`;
}

function validSingleRange(value: string): boolean {
  const match = /^bytes=(\d+)-(\d*)$|^bytes=-(\d+)$/i.exec(value);
  if (!match) return false;
  const start = match[1] ? Number(match[1]) : undefined;
  const end = match[2] ? Number(match[2]) : undefined;
  const suffix = match[3] ? Number(match[3]) : undefined;
  if (start !== undefined && !Number.isSafeInteger(start)) return false;
  if (end !== undefined && !Number.isSafeInteger(end)) return false;
  if (suffix !== undefined && (!Number.isSafeInteger(suffix) || suffix < 1)) return false;
  return start === undefined || end === undefined || start <= end;
}

async function getPublishedRecording(id: number) {
  const db = await getDb();
  if (!db) return null;
  const rows = await db.select({
    id: recordings.id,
    title: recordings.title,
    storageKey: recordings.storageKey,
    contentType: recordings.contentType,
    sizeBytes: recordings.sizeBytes,
  }).from(recordings)
    .where(and(eq(recordings.id, id), eq(recordings.status, "published")))
    .limit(1);
  return rows[0] ?? null;
}

function setBaseHeaders(res: Response, title: string) {
  const name = fileName(title);
  res.setHeader("Content-Type", "audio/mp4");
  res.setHeader("Content-Disposition", `inline; filename="${name}"; filename*=UTF-8''${encodeURIComponent(name)}`);
  res.setHeader("Accept-Ranges", "bytes");
  res.setHeader("Cache-Control", "private, no-store");
  res.setHeader("X-Content-Type-Options", "nosniff");
  res.setHeader("Cross-Origin-Resource-Policy", "same-origin");
}

async function streamPublishedRecording(req: Request, res: Response, id: number) {
  const recording = await getPublishedRecording(id);
  if (!recording) {
    res.status(404).json({ error: "Aufnahme nicht gefunden." });
    return;
  }

  const range = req.headers.range;
  if (range && !validSingleRange(range)) {
    res.setHeader("Content-Range", `bytes */${recording.sizeBytes}`);
    res.setHeader("Accept-Ranges", "bytes");
    res.status(416).end();
    return;
  }

  const signedUrl = await storageGetSignedUrl(recording.storageKey);
  const upstream = await fetch(signedUrl, {
    headers: range ? { Range: range } : undefined,
    signal: AbortSignal.timeout(15 * 60_000),
  });

  if (upstream.status === 416) {
    const contentRange = upstream.headers.get("content-range");
    if (contentRange) res.setHeader("Content-Range", contentRange);
    res.setHeader("Accept-Ranges", "bytes");
    res.status(416).end();
    return;
  }
  if (!upstream.ok || !upstream.body || (range && upstream.status !== 206)) {
    await upstream.body?.cancel().catch(() => undefined);
    res.status(502).json({ error: "Die Audiodatei ist momentan nicht erreichbar." });
    return;
  }

  setBaseHeaders(res, recording.title);
  res.status(upstream.status);
  const contentLength = upstream.headers.get("content-length");
  const contentRange = upstream.headers.get("content-range");
  if (contentLength) res.setHeader("Content-Length", contentLength);
  if (contentRange) res.setHeader("Content-Range", contentRange);

  try {
    await pipeline(Readable.fromWeb(upstream.body as never), res);
  } catch (error) {
    if (res.headersSent) {
      res.destroy(error instanceof Error ? error : undefined);
      return;
    }
    throw error;
  }
}

export function registerRecordingStreamRoute(app: Express) {
  const route = async (req: Request, res: Response, next: NextFunction) => {
    if (req.method === "HEAD") {
      next();
      return;
    }
    const id = Number(req.params.id);
    if (!Number.isSafeInteger(id) || id < 1) {
      res.status(404).json({ error: "Aufnahme nicht gefunden." });
      return;
    }
    try {
      await streamPublishedRecording(req, res, id);
    } catch (error) {
      if (res.headersSent) {
        res.destroy(error instanceof Error ? error : undefined);
        return;
      }
      console.error("[Stream] Recording proxy failed:", error instanceof Error ? error.message : "unknown error");
      res.status(502).json({ error: "Wiedergabe konnte nicht gestartet werden." });
    }
  };

  app.get("/api/recordings/:id/stream", route);
  app.head("/api/recordings/:id/stream", async (req: Request, res: Response) => {
    const id = Number(req.params.id);
    if (!Number.isSafeInteger(id) || id < 1) {
      res.status(404).end();
      return;
    }
    try {
      const recording = await getPublishedRecording(id);
      if (!recording) {
        res.status(404).end();
        return;
      }
      setBaseHeaders(res, recording.title);
      res.setHeader("Content-Length", String(recording.sizeBytes));
      res.status(200).end();
    } catch (error) {
      console.error("[Stream] Recording metadata lookup failed:", error instanceof Error ? error.message : "unknown error");
      res.status(502).end();
    }
  });
}
