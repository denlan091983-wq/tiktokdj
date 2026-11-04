import { describe, expect, it } from "vitest";
import { appRouter } from "./routers";
import type { TrpcContext } from "./_core/context";

function contextFor(role: "user" | "admin" | null, email = "radio-test@example.invalid"): TrpcContext {
  return {
    user: role ? {
      id: 7,
      openId: "radio-test-user",
      email,
      name: "Test User",
      loginMethod: "manus",
      role,
      createdAt: new Date(),
      updatedAt: new Date(),
      lastSignedIn: new Date(),
    } : null,
    req: { protocol: "https", headers: {} } as TrpcContext["req"],
    res: { clearCookie: () => undefined } as unknown as TrpcContext["res"],
  };
}

function withAdminEmail(value: string | undefined) {
  const previous = process.env.LARMBATZ_ADMIN_EMAIL;
  if (value === undefined) delete process.env.LARMBATZ_ADMIN_EMAIL;
  else process.env.LARMBATZ_ADMIN_EMAIL = value;
  return () => {
    if (previous === undefined) delete process.env.LARMBATZ_ADMIN_EMAIL;
    else process.env.LARMBATZ_ADMIN_EMAIL = previous;
  };
}

describe("recordings authorization", () => {
  it("allows only the explicitly configured Manus OAuth email to manage recordings", async () => {
    const restore = withAdminEmail("owner@example.invalid");
    try {
      const owner = appRouter.createCaller(contextFor("user", "owner@example.invalid"));
      const other = appRouter.createCaller(contextFor("user", "visitor@example.invalid"));
      await expect(owner.recordings.canManage()).resolves.toMatchObject({ configured: true, allowed: true });
      await expect(other.recordings.canManage()).resolves.toMatchObject({ configured: true, allowed: false });
      await expect(other.recordings.studioList()).rejects.toMatchObject({ code: "FORBIDDEN" });
      await expect(other.recordings.prepareUpload({
        title: "Unauthorized",
        originalFileName: "mix.m4a",
        contentType: "audio/mp4",
        sizeBytes: 1024,
        durationSeconds: 10,
      })).rejects.toMatchObject({ code: "FORBIDDEN" });
    } finally {
      restore();
    }
  });

  it("denies even an admin-role row if the private owner email has not been configured", async () => {
    const restore = withAdminEmail(undefined);
    try {
      const caller = appRouter.createCaller(contextFor("admin", "owner@example.invalid"));
      await expect(caller.recordings.canManage()).resolves.toEqual({ configured: false, allowed: false });
      await expect(caller.recordings.studioList()).rejects.toMatchObject({ code: "FORBIDDEN" });
    } finally {
      restore();
    }
  });

  it("does not expose studio permissions to anonymous users", async () => {
    const restore = withAdminEmail("owner@example.invalid");
    try {
      const caller = appRouter.createCaller(contextFor(null));
      await expect(caller.recordings.canManage()).rejects.toMatchObject({ code: "UNAUTHORIZED" });
    } finally {
      restore();
    }
  });
});
