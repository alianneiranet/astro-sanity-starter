// One hourly run: seed Firestore if needed, scan the news, review the consoles that need it.

import Anthropic from "@anthropic-ai/sdk";
import { createHash } from "node:crypto";
import { logger } from "firebase-functions";
import type { Firestore } from "firebase-admin/firestore";
import { runToolTask } from "./claude";
import { catalogSummary, reviewPrompt, reviewSystemPrompt, scanPrompt, scanSystemPrompt } from "./prompts";
import { parseConsoleUpdate, parseScan, REPORT_SCAN_TOOL, SUBMIT_CONSOLE_TOOL } from "./schema";
import type { Catalog, ConsoleInfo } from "./types";

export interface UpdaterConfig {
  apiKey: string;
  model: string;
  scanEffort: "low" | "medium" | "high" | "xhigh" | "max";
  reviewEffort: "low" | "medium" | "high" | "xhigh" | "max";
  scanMaxSearches: number;
  reviewMaxSearches: number;
  maxReviewsPerRun: number;
}

export interface RunReport {
  seeded: number;
  news: number;
  reviewed: string[];
  changed: string[];
  errors: string[];
  inputTokens: number;
  outputTokens: number;
  searches: number;
}

const LOCK_MINUTES = 40;

/** Writes the bundled catalog into Firestore the first time, or when the bundled seedVersion grows. */
export async function ensureSeeded(db: Firestore, bundled: Catalog): Promise<number> {
  const seedRef = db.doc("meta/seed");
  const stored = (await seedRef.get()).data()?.seedVersion ?? 0;
  if (stored >= bundled.seedVersion) return 0;

  const batch = db.batch();
  for (const brand of bundled.brands) batch.set(db.doc(`brands/${brand.id}`), brand);
  let written = 0;
  const existing = await db.collection("consoles").get();
  const byId = new Map(existing.docs.map((d) => [d.id, d.data() as ConsoleInfo]));
  for (const seed of bundled.consoles) {
    const current = byId.get(seed.id);
    // Keep AI updates that are newer than the curated seed; refresh the static fields only.
    if (current && current.updatedAt > seed.updatedAt) {
      batch.set(
        db.doc(`consoles/${seed.id}`),
        {
          brandId: seed.brandId,
          name: seed.name,
          shortName: seed.shortName,
          year: seed.year,
          formFactor: seed.formFactor,
          legacy: seed.legacy,
          versionHint: seed.versionHint,
          checkEveryHours: seed.checkEveryHours,
        },
        { merge: true },
      );
    } else {
      batch.set(db.doc(`consoles/${seed.id}`), { ...seed, lastCheckedAt: current?.lastCheckedAt ?? "" });
    }
    written++;
  }
  batch.set(seedRef, { seedVersion: bundled.seedVersion, seededAt: new Date().toISOString() });
  await batch.commit();
  return written;
}

/** Prevents two overlapping runs (e.g. a slow run plus the next hourly trigger). */
async function acquireLock(db: Firestore): Promise<boolean> {
  const ref = db.doc("meta/lock");
  return db.runTransaction(async (tx) => {
    const until = (await tx.get(ref)).data()?.until ?? 0;
    if (until > Date.now()) return false;
    tx.set(ref, { until: Date.now() + LOCK_MINUTES * 60_000 });
    return true;
  });
}

const releaseLock = (db: Firestore) => db.doc("meta/lock").set({ until: 0 });

/**
 * Consoles whose last AI review is older than their checkEveryHours, most overdue first
 * (relative to their interval, so active consoles beat retro ones checked monthly).
 */
export function dueConsoles(consoles: ConsoleInfo[], now: number): ConsoleInfo[] {
  const overdue = (c: ConsoleInfo) =>
    (now - (Date.parse(c.lastCheckedAt ?? "") || 0)) / ((c.checkEveryHours || 24) * 3_600_000);
  return consoles.filter((c) => overdue(c) >= 1).sort((a, b) => overdue(b) - overdue(a));
}

export async function runUpdate(db: Firestore, bundled: Catalog, config: UpdaterConfig): Promise<RunReport> {
  const report: RunReport = {
    seeded: 0,
    news: 0,
    reviewed: [],
    changed: [],
    errors: [],
    inputTokens: 0,
    outputTokens: 0,
    searches: 0,
  };
  const startedAt = new Date().toISOString();
  const today = startedAt.slice(0, 10);

  report.seeded = await ensureSeeded(db, bundled);
  if (!(await acquireLock(db))) {
    logger.info("Another run is in progress; skipping");
    return report;
  }

  const client = new Anthropic({ apiKey: config.apiKey, maxRetries: 3 });
  const track = (r: { inputTokens: number; outputTokens: number; searches: number }) => {
    report.inputTokens += r.inputTokens;
    report.outputTokens += r.outputTokens;
    report.searches += r.searches;
  };

  try {
    const consoles = (await db.collection("consoles").get()).docs.map((d) => d.data() as ConsoleInfo);
    const byId = new Map(consoles.map((c) => [c.id, c]));

    // 1) Hourly news scan across the whole catalog.
    const recent = await db.collection("news").orderBy("createdAt", "desc").limit(40).get();
    const knownNews = recent.docs.map((d) => String(d.data().title ?? ""));
    const scan = await runToolTask({
      client,
      model: config.model,
      effort: config.scanEffort,
      maxSearches: config.scanMaxSearches,
      system: scanSystemPrompt(today),
      prompt: scanPrompt(catalogSummary(consoles), knownNews),
      tool: REPORT_SCAN_TOOL,
      label: "scan",
    });
    track(scan);

    const reviewQueue: { consoleId: string; reason: string }[] = [];
    if (scan.input) {
      const parsed = parseScan(scan.input, new Set(byId.keys()));
      const batch = db.batch();
      for (const item of parsed.news) {
        const id = createHash("sha1").update(item.url || item.title).digest("hex").slice(0, 20);
        batch.set(db.doc(`news/${id}`), { ...item, createdAt: new Date().toISOString() }, { merge: true });
      }
      await batch.commit();
      report.news = parsed.news.length;
      reviewQueue.push(...parsed.review);
    } else {
      report.errors.push("scan returned no result");
    }

    // 2) Periodic check of consoles nobody has reviewed for a while, even without news.
    for (const c of dueConsoles(consoles, Date.now())) {
      if (!reviewQueue.some((r) => r.consoleId === c.id)) {
        reviewQueue.push({ consoleId: c.id, reason: "Revisión periódica programada (sin noticias concretas)." });
      }
    }

    // 3) Detailed reviews, capped per run to control cost.
    for (const { consoleId, reason } of reviewQueue.slice(0, config.maxReviewsPerRun)) {
      const current = byId.get(consoleId);
      if (!current) continue;
      report.reviewed.push(consoleId);
      try {
        const res = await runToolTask({
          client,
          model: config.model,
          effort: config.reviewEffort,
          maxSearches: config.reviewMaxSearches,
          system: reviewSystemPrompt(today),
          prompt: reviewPrompt(current, reason),
          tool: SUBMIT_CONSOLE_TOOL,
          label: `review:${consoleId}`,
        });
        track(res);
        const checkedAt = new Date().toISOString();
        if (!res.input) {
          report.errors.push(`${consoleId}: no result`);
          continue;
        }
        const update = parseConsoleUpdate(res.input, current);
        const ref = db.doc(`consoles/${consoleId}`);
        if (update.changed) {
          await ref.set({ ...update.console, updatedAt: checkedAt, lastCheckedAt: checkedAt });
          await ref.collection("history").add({
            at: checkedAt,
            change: update.changeSummary,
            reason,
            previous: { summary: current.summary, firmwares: current.firmwares, allVersions: current.allVersions ?? null },
          });
          report.changed.push(consoleId);
          logger.info(`Updated ${consoleId}: ${update.changeSummary}`);
        } else {
          await ref.set({ lastCheckedAt: checkedAt }, { merge: true });
        }
      } catch (err) {
        // Keep the old data; the console stays due and is retried on a later run.
        report.errors.push(`${consoleId}: ${(err as Error).message}`);
        logger.error(`Review of ${consoleId} failed`, err);
      }
    }

    await db.doc("meta/status").set(
      {
        lastRunAt: startedAt,
        ok: report.errors.length === 0,
        message: report.errors.join(" | ").slice(0, 900),
        model: config.model,
        lastReport: report,
        ...(scan.input ? { lastSuccessAt: startedAt } : {}),
      },
      { merge: true },
    );
    return report;
  } catch (err) {
    await db
      .doc("meta/status")
      .set({ lastRunAt: startedAt, ok: false, message: String((err as Error).message).slice(0, 900) }, { merge: true });
    throw err;
  } finally {
    await releaseLock(db);
  }
}
