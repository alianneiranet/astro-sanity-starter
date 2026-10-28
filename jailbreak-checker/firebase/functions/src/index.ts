import { readFileSync } from "node:fs";
import { join } from "node:path";
import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions";
import { defineInt, defineSecret, defineString } from "firebase-functions/params";
import { onSchedule } from "firebase-functions/v2/scheduler";
import type { Catalog } from "./types";
import { runUpdate, type UpdaterConfig } from "./updater";

initializeApp();
const db = getFirestore();
db.settings({ ignoreUndefinedProperties: true });

// Set with: firebase functions:secrets:set ANTHROPIC_API_KEY
const anthropicApiKey = defineSecret("ANTHROPIC_API_KEY");

// Tunables (asked on first deploy, stored in functions/.env.<project>).
const claudeModel = defineString("CLAUDE_MODEL", {
  default: "claude-opus-5-5",
  description: "Claude model used for the hourly research",
});
const scanEffort = defineString("SCAN_EFFORT", { default: "medium", description: "low | medium | high" });
const reviewEffort = defineString("REVIEW_EFFORT", { default: "high", description: "low | medium | high" });
const scanMaxSearches = defineInt("SCAN_MAX_SEARCHES", { default: 5, description: "Web searches per hourly scan" });
const reviewMaxSearches = defineInt("REVIEW_MAX_SEARCHES", { default: 5, description: "Web searches per console review" });
const maxReviewsPerRun = defineInt("MAX_REVIEWS_PER_RUN", { default: 2, description: "Detailed console reviews per hour" });

const bundled: Catalog = JSON.parse(readFileSync(join(__dirname, "data", "consoles.json"), "utf8"));

type Effort = UpdaterConfig["scanEffort"];
const asEffort = (v: string, fallback: Effort): Effort =>
  (["low", "medium", "high", "xhigh", "max"] as const).find((e) => e === v.trim()) ?? fallback;

export const hourlyJailbreakCheck = onSchedule(
  {
    schedule: "every 60 minutes",
    timeZone: "Etc/UTC",
    secrets: [anthropicApiKey],
    timeoutSeconds: 1800,
    memory: "512MiB",
    retryCount: 0,
  },
  async () => {
    const report = await runUpdate(db, bundled, {
      apiKey: anthropicApiKey.value(),
      model: claudeModel.value(),
      scanEffort: asEffort(scanEffort.value(), "medium"),
      reviewEffort: asEffort(reviewEffort.value(), "high"),
      scanMaxSearches: scanMaxSearches.value(),
      reviewMaxSearches: reviewMaxSearches.value(),
      maxReviewsPerRun: maxReviewsPerRun.value(),
    });
    logger.info("Hourly jailbreak check finished", report);
  },
);
