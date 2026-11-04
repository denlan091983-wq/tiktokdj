import { int, mysqlEnum, mysqlTable, text, timestamp, varchar } from "drizzle-orm/mysql-core";

/**
 * Core user table backing the Manus OAuth flow.
 * Application roles are persisted here and never inferred from an untrusted browser value.
 */
export const users = mysqlTable("users", {
  id: int("id").autoincrement().primaryKey(),
  openId: varchar("openId", { length: 64 }).notNull().unique(),
  name: text("name"),
  email: varchar("email", { length: 320 }),
  loginMethod: varchar("loginMethod", { length: 64 }),
  role: mysqlEnum("role", ["user", "admin"]).default("user").notNull(),
  createdAt: timestamp("createdAt").defaultNow().notNull(),
  updatedAt: timestamp("updatedAt").defaultNow().onUpdateNow().notNull(),
  lastSignedIn: timestamp("lastSignedIn").defaultNow().notNull(),
});

/** Legacy setup guard retained in the shared database; current Studio access uses an email allowlist. */
export const siteAdminAssignments = mysqlTable("site_admin_assignments", {
  id: int("id").primaryKey().notNull(),
  userId: int("userId").notNull().unique().references(() => users.id),
  createdAt: timestamp("createdAt").defaultNow().notNull(),
});

/**
 * Index of uploaded mixes. The object is not exposed in the public catalogue unless status is
 * `published`; stored bytes themselves live in durable Manus object storage, never in MySQL.
 */
export const recordings = mysqlTable("recordings", {
  id: int("id").autoincrement().primaryKey(),
  title: varchar("title", { length: 160 }).notNull(),
  originalFileName: varchar("originalFileName", { length: 255 }).notNull(),
  storageKey: varchar("storageKey", { length: 512 }).notNull().unique(),
  contentType: varchar("contentType", { length: 64 }).notNull(),
  sizeBytes: int("sizeBytes").notNull(),
  durationSeconds: int("durationSeconds").notNull(),
  status: mysqlEnum("status", ["uploading", "ready", "published"]).default("uploading").notNull(),
  createdBy: int("createdBy").notNull().references(() => users.id),
  createdAt: timestamp("createdAt").defaultNow().notNull(),
  publishedAt: timestamp("publishedAt"),
});

export type User = typeof users.$inferSelect;
export type InsertUser = typeof users.$inferInsert;
export type Recording = typeof recordings.$inferSelect;
