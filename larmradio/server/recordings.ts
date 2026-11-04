import { Readable } from "node:stream";
import { pipeline } from "node:stream/promises";
import type { Express, Request, Response } from "express";
import { and, desc, eq } from "drizzle-orm";
import { z } from "zod";
import { getDb } from "./db";
import { recordings } from "../drizzle/schema";
import { protectedProcedure, publicProcedure, router } from "./_core/trpc";
import { storageCreatePresignedUpload, storageGetSignedUrl } from "./storage";
import { TRPCError } from "@trpc/server";
import { ENV } from "./_core/env";
import { readM4aInfoFromUrl } from "./m4a";

const MAX_RECORDING_BYTES = 125 * 1024 * 1024;
const MAX_RECORDING_SECONDS = 60 * 60;
const allowedTypes = ["audio/mp4"] as const;

const getDatabase = async () => {
  const db = await getDb();
  if (!db) throw new TRPCError({ code: "INTERNAL_SERVER_ERROR", message: "Datenbank ist derzeit nicht verfügbar." });
  return db;
};

function studioAccess(email: string | null | undefined) {
  const ownerEmail = ENV.larmbatzAdminEmail.trim().toLowerCase();
  const configured = ownerEmail.length > 0;
  return {
    configured,
    allowed: configured && !!email && email.trim().toLowerCase() === ownerEmail,
  };
}

const studioProcedure = protectedProcedure.use(({ ctx, next }) => {
  if (!studioAccess(ctx.user.email).allowed) {
    throw new TRPCError({ code: "FORBIDDEN", message: "Dieses Manus-Konto ist nicht für das LÄRMBATZ-Studio freigeschaltet." });
  }
  return next({ ctx: { ...ctx, user: ctx.user } });
});

function safeOriginalName(value: string): string {
  const basename = value.replaceAll("\\", "/").split("/").pop() ?? "mix.m4a";
  const safe = basename.replace(/[\r\n\u0000-\u001f]/g, "").trim();
  return safe.slice(0, 255) || "mix.m4a";
}

export const recordingsRouter = router({
  canManage: protectedProcedure.query(({ ctx }) => studioAccess(ctx.user.email)),

  list: publicProcedure.query(async () => {
    const db = await getDatabase();
    const rows = await db.select({
      id: recordings.id,
      title: recordings.title,
      contentType: recordings.contentType,
      sizeBytes: recordings.sizeBytes,
      durationSeconds: recordings.durationSeconds,
      publishedAt: recordings.publishedAt,
    }).from(recordings)
      .where(eq(recordings.status, "published"))
      .orderBy(desc(recordings.publishedAt));
    return rows;
  }),

  studioList: studioProcedure.query(async () => {
    const db = await getDatabase();
    return db.select().from(recordings).orderBy(desc(recordings.createdAt));
  }),

  prepareUpload: studioProcedure.input(z.object({
    title: z.string().trim().min(1).max(160),
    originalFileName: z.string().min(1).max(255).refine(value => value.toLowerCase().endsWith(".m4a"), "Nur M4A-Dateien mit AAC-Audio werden unterstützt."),
    contentType: z.enum(allowedTypes),
    sizeBytes: z.number().int().min(1).max(MAX_RECORDING_BYTES),
    durationSeconds: z.number().int().min(1).max(MAX_RECORDING_SECONDS),
  })).mutation(async ({ ctx, input }) => {
    if (input.durationSeconds > MAX_RECORDING_SECONDS) {
      throw new TRPCError({ code: "BAD_REQUEST", message: "Aufnahmen dürfen höchstens 60 Minuten lang sein." });
    }
    const db = await getDatabase();
    const keySeed = `larmradio/recordings/${crypto.randomUUID()}.m4a`;
    let key: string;
    let uploadUrl: string;
    try {
      ({ key, uploadUrl } = await storageCreatePresignedUpload(keySeed));
    } catch (error) {
      console.error("[Storage] Could not create recording upload URL:", error instanceof Error ? error.message : "unknown error");
      throw new TRPCError({ code: "INTERNAL_SERVER_ERROR", message: "Der Audiospeicher konnte keinen Upload vorbereiten." });
    }

    const result = await db.insert(recordings).values({
      title: input.title,
      originalFileName: safeOriginalName(input.originalFileName),
      storageKey: key,
      contentType: input.contentType,
      sizeBytes: input.sizeBytes,
      durationSeconds: input.durationSeconds,
      status: "uploading",
      createdBy: ctx.user.id,
    });
    const id = Number((result as unknown as { insertId?: number }).insertId);
    if (!Number.isSafeInteger(id) || id < 1) {
      throw new TRPCError({ code: "INTERNAL_SERVER_ERROR", message: "Upload wurde vorbereitet, aber nicht indiziert." });
    }
    return { id, uploadUrl, contentType: input.contentType };
  }),

  confirmUpload: studioProcedure.input(z.object({ id: z.number().int().positive() })).mutation(async ({ input }) => {
    const db = await getDatabase();
    const rows = await db.select().from(recordings).where(eq(recordings.id, input.id)).limit(1);
    const recording = rows[0];
    if (!recording) throw new TRPCError({ code: "NOT_FOUND", message: "Aufnahme nicht gefunden." });
    if (recording.status !== "uploading") {
      throw new TRPCError({ code: "PRECONDITION_FAILED", message: "Dieser Upload ist nicht mehr offen." });
    }

    try {
      const signedUrl = await storageGetSignedUrl(recording.storageKey);
      const media = await readM4aInfoFromUrl(signedUrl);
      if (!Number.isSafeInteger(media.sizeBytes) || media.sizeBytes < 1 || media.sizeBytes > MAX_RECORDING_BYTES || media.sizeBytes !== recording.sizeBytes) {
        throw new TRPCError({ code: "BAD_REQUEST", message: "Die Audiodatei fehlt, ist zu groß oder ihre Uploadgröße stimmt nicht überein." });
      }
      if (media.durationSeconds > MAX_RECORDING_SECONDS + 0.05) {
        throw new TRPCError({ code: "BAD_REQUEST", message: "Die tatsächliche Audiodauer überschreitet 60 Minuten." });
      }
      await db.update(recordings).set({
        status: "ready",
        sizeBytes: media.sizeBytes,
        durationSeconds: Math.round(media.durationSeconds),
      }).where(and(eq(recordings.id, input.id), eq(recordings.status, "uploading")));
    } catch (error) {
      if (error instanceof TRPCError) throw error;
      console.warn("[Storage] Recording upload verification failed:", error instanceof Error ? error.message : "unknown error");
      throw new TRPCError({ code: "BAD_REQUEST", message: "Der Upload ist noch nicht vollständig oder konnte nicht bestätigt werden." });
    }
    return { id: input.id, status: "ready" as const };
  }),

  publish: studioProcedure.input(z.object({ id: z.number().int().positive() })).mutation(async ({ input }) => {
    const db = await getDatabase();
    const rows = await db.select().from(recordings).where(eq(recordings.id, input.id)).limit(1);
    const recording = rows[0];
    if (!recording) throw new TRPCError({ code: "NOT_FOUND", message: "Aufnahme nicht gefunden." });
    if (recording.status !== "ready") {
      throw new TRPCError({ code: "PRECONDITION_FAILED", message: "Nur vollständig hochgeladene Aufnahmen können veröffentlicht werden." });
    }
    await db.update(recordings).set({ status: "published", publishedAt: new Date() })
      .where(and(eq(recordings.id, input.id), eq(recordings.status, "ready")));
    return { id: input.id, status: "published" as const };
  }),

  unpublish: studioProcedure.input(z.object({ id: z.number().int().positive() })).mutation(async ({ input }) => {
    const db = await getDatabase();
    await db.update(recordings).set({ status: "ready", publishedAt: null })
      .where(and(eq(recordings.id, input.id), eq(recordings.status, "published")));
    return { id: input.id, status: "ready" as const };
  }),
});

function downloadFileName(title: string): string {
  const normalized = title.normalize("NFKD").replace(/[\u0300-\u036f]/g, "");
  const safe = normalized.replace(/[^A-Za-z0-9._-]+/g, "-").replace(/^-+|-+$/g, "").slice(0, 100);
  return `${safe || "laermbatz-mix"}.m4a`;
}

/** A public download exists only while the recording's DB status is explicitly `published`. */
export function registerRecordingDownloadRoute(app: Express) {
  app.get("/api/recordings/:id/download", async (req: Request, res: Response) => {
    const id = Number(req.params.id);
    if (!Number.isSafeInteger(id) || id < 1) {
      res.status(404).json({ error: "Aufnahme nicht gefunden." });
      return;
    }
    try {
      const db = await getDatabase();
      const rows = await db.select().from(recordings)
        .where(and(eq(recordings.id, id), eq(recordings.status, "published")))
        .limit(1);
      const recording = rows[0];
      if (!recording) {
        res.status(404).json({ error: "Aufnahme nicht gefunden." });
        return;
      }
      const signedUrl = await storageGetSignedUrl(recording.storageKey);
      const upstream = await fetch(signedUrl, { signal: AbortSignal.timeout(30_000) });
      if (!upstream.ok || !upstream.body) {
        res.status(502).json({ error: "Die Audiodatei ist momentan nicht erreichbar." });
        return;
      }
      const filename = downloadFileName(recording.title);
      res.status(200);
      res.setHeader("Content-Type", recording.contentType);
      res.setHeader("Content-Disposition", `attachment; filename="${filename}"; filename*=UTF-8''${encodeURIComponent(filename)}`);
      res.setHeader("X-Content-Type-Options", "nosniff");
      res.setHeader("Cache-Control", "private, no-store");
      const upstreamLength = upstream.headers.get("content-length");
      if (upstreamLength) res.setHeader("Content-Length", upstreamLength);
      await pipeline(Readable.fromWeb(upstream.body as never), res);
    } catch (error) {
      if (res.headersSent) {
        res.destroy(error instanceof Error ? error : undefined);
        return;
      }
      console.error("[Download] Recording stream failed:", error instanceof Error ? error.message : "unknown error");
      res.status(502).json({ error: "Download konnte nicht gestartet werden." });
    }
  });
}
