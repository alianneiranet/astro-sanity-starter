// Tool schemas Claude fills in, plus validation that turns the tool input into app data.
// Tools use strict mode, so inputs already match these schemas; the validators below
// enforce the cross-field rules a JSON schema can't express (known ids, version order...).

import type { Anthropic } from "@anthropic-ai/sdk";
import { compareVersions } from "./versions";
import {
  STATUSES,
  type ConsoleInfo,
  type FirmwareRange,
  type NewsItem,
  type StatusInfo,
  type StatusValue,
} from "./types";

type Tool = Anthropic.Beta.BetaTool;

const str = (description: string) => ({ type: "string", description });
const statusEnum = (description: string) => ({ type: "string", enum: [...STATUSES], description });

const statusInfoSchema = {
  type: "object",
  additionalProperties: false,
  required: ["status", "title", "explanation", "method"],
  properties: {
    status: statusEnum("Traffic light for every version"),
    title: str("Short title in Spanish, e.g. 'Jailbreak en todas las versiones'"),
    explanation: str("2-4 plain Spanish sentences for a non-technical user"),
    method: str("Exploit / tool names, e.g. 'GoldHEN + PPPwn'"),
  },
};

export const REPORT_SCAN_TOOL: Tool = {
  name: "report_scan",
  description:
    "Report the result of the news scan. Call exactly once, after searching, even if nothing new was found (use empty arrays).",
  strict: true,
  input_schema: {
    type: "object",
    additionalProperties: false,
    required: ["news", "consoles_to_review"],
    properties: {
      news: {
        type: "array",
        description: "New, relevant, verified news items not already in the known-news list. Max 8.",
        items: {
          type: "object",
          additionalProperties: false,
          required: ["title", "summary", "console_ids", "status", "url", "source", "published_date"],
          properties: {
            title: str("Headline in Spanish, max ~90 characters"),
            summary: str("1-2 sentences in Spanish explaining what changed and for which firmware"),
            console_ids: { type: "array", items: { type: "string" }, description: "Catalog console ids affected" },
            status: statusEnum("Jailbreak status this news implies for the affected firmware"),
            url: str("URL of the best source (the original announcement if possible)"),
            source: str("Site name, e.g. 'GBAtemp' or 'GitHub'"),
            published_date: str("YYYY-MM-DD"),
          },
        },
      },
      consoles_to_review: {
        type: "array",
        description: "Consoles whose stored status is now outdated or wrong and need a detailed review.",
        items: {
          type: "object",
          additionalProperties: false,
          required: ["console_id", "reason"],
          properties: {
            console_id: str("Catalog console id"),
            reason: str("Why the stored data looks outdated"),
          },
        },
      },
    },
  },
};

export const SUBMIT_CONSOLE_TOOL: Tool = {
  name: "submit_console_update",
  description:
    "Submit the complete, corrected jailbreak status for the console under review. Call exactly once. " +
    "If nothing needs to change, set changed=false and resend the current data unchanged.",
  strict: true,
  input_schema: {
    type: "object",
    additionalProperties: false,
    required: [
      "changed",
      "change_summary",
      "summary_status",
      "summary_text",
      "latest_firmware",
      "mode",
      "all_versions",
      "firmwares",
      "sources",
    ],
    properties: {
      changed: { type: "boolean", description: "true only if the jailbreak situation materially changed" },
      change_summary: str("Spanish, one sentence describing what changed (empty if nothing)"),
      summary_status: statusEnum("Best realistic status a user can get today on this console"),
      summary_text: str("Spanish, max ~60 characters, e.g. 'Jailbreak hasta la 13.60'"),
      latest_firmware: str("Latest official firmware version, or empty if not applicable"),
      mode: {
        type: "string",
        enum: ["all", "firmware"],
        description: "'all' when every version shares one status (no firmware question), else 'firmware'",
      },
      all_versions: { ...statusInfoSchema, description: "Used when mode='all'; otherwise repeat the summary" },
      firmwares: {
        type: "array",
        description:
          "Ordered firmware ranges (oldest first) when mode='firmware'; empty array when mode='all'. " +
          "Inclusive bounds; empty min/max means open-ended; both empty = 'any other version'.",
        items: {
          type: "object",
          additionalProperties: false,
          required: ["id", "label", "variant", "min", "max", "status", "title", "explanation", "method"],
          properties: {
            id: str("Stable id, keep existing ids when the range still exists"),
            label: str("What the user sees, e.g. '9.03 – 11.00' or '14.20 o superior'"),
            variant: str("Existing variant id this range applies to, or empty for all models"),
            min: str("Inclusive minimum version, or empty"),
            max: str("Inclusive maximum version, or empty"),
            status: statusEnum("Status for this range"),
            title: str("Short Spanish title"),
            explanation: str("2-4 plain Spanish sentences: what works, limitations, requirements"),
            method: str("Exploit / tool names, or '—' if none"),
          },
        },
      },
      sources: {
        type: "array",
        description: "1-4 reputable sources backing the data",
        items: {
          type: "object",
          additionalProperties: false,
          required: ["title", "url"],
          properties: { title: str("Source title"), url: str("https URL") },
        },
      },
    },
  },
};

export interface ScanResult {
  news: Omit<NewsItem, "createdAt">[];
  review: { consoleId: string; reason: string }[];
}

const isStatus = (v: unknown): v is StatusValue => typeof v === "string" && (STATUSES as readonly string[]).includes(v);
const text = (v: unknown, max = 600): string => (typeof v === "string" ? v.trim().slice(0, max) : "");
const isHttpUrl = (v: string) => /^https?:\/\/[^\s]+$/i.test(v);

export function parseScan(input: unknown, knownIds: Set<string>): ScanResult {
  const obj = (input ?? {}) as Record<string, unknown>;
  const news = (Array.isArray(obj.news) ? obj.news : [])
    .slice(0, 8)
    .map((raw) => {
      const n = (raw ?? {}) as Record<string, unknown>;
      const consoleIds = (Array.isArray(n.console_ids) ? n.console_ids : [])
        .map((id) => text(id, 40))
        .filter((id) => knownIds.has(id));
      return {
        title: text(n.title, 160),
        summary: text(n.summary, 500),
        consoleIds,
        status: isStatus(n.status) ? n.status : ("unknown" as const),
        url: isHttpUrl(text(n.url, 500)) ? text(n.url, 500) : "",
        source: text(n.source, 60),
        publishedAt: /^\d{4}-\d{2}-\d{2}$/.test(text(n.published_date, 10)) ? text(n.published_date, 10) : "",
      };
    })
    .filter((n) => n.title && n.summary && n.consoleIds.length > 0);

  const seen = new Set<string>();
  const review = (Array.isArray(obj.consoles_to_review) ? obj.consoles_to_review : [])
    .map((raw) => {
      const r = (raw ?? {}) as Record<string, unknown>;
      return { consoleId: text(r.console_id, 40), reason: text(r.reason, 300) };
    })
    .filter((r) => knownIds.has(r.consoleId) && !seen.has(r.consoleId) && seen.add(r.consoleId));

  return { news, review };
}

export interface ConsoleUpdate {
  changed: boolean;
  changeSummary: string;
  console: ConsoleInfo;
}

/**
 * Validates Claude's submission against the current console and returns the merged console.
 * Throws with a readable message when the submission is unusable, so the caller keeps the old data.
 */
export function parseConsoleUpdate(input: unknown, current: ConsoleInfo): ConsoleUpdate {
  const obj = (input ?? {}) as Record<string, unknown>;
  const mode = obj.mode === "all" ? "all" : "firmware";
  const variantIds = new Set(current.variants.map((v) => v.id));

  if (!isStatus(obj.summary_status)) throw new Error("summary_status missing");
  const summaryText = text(obj.summary_text, 120);
  if (!summaryText) throw new Error("summary_text missing");

  const av = (obj.all_versions ?? {}) as Record<string, unknown>;
  const allVersions: StatusInfo | null =
    mode === "all"
      ? {
          status: isStatus(av.status) ? av.status : obj.summary_status,
          title: text(av.title, 120) || summaryText,
          explanation: text(av.explanation, 900),
          method: text(av.method, 160),
        }
      : null;
  if (mode === "all" && !allVersions?.explanation) throw new Error("all_versions.explanation missing");

  const usedIds = new Set<string>();
  const firmwares: FirmwareRange[] =
    mode === "firmware"
      ? (Array.isArray(obj.firmwares) ? obj.firmwares : []).map((raw, index) => {
          const f = (raw ?? {}) as Record<string, unknown>;
          const label = text(f.label, 80);
          const min = text(f.min, 40);
          const max = text(f.max, 40);
          const variant = text(f.variant, 40);
          if (!label) throw new Error(`firmware #${index} has no label`);
          if (!isStatus(f.status)) throw new Error(`firmware ${label} has an invalid status`);
          if (variant && !variantIds.has(variant)) throw new Error(`firmware ${label} uses unknown variant ${variant}`);
          if (min && max && compareVersions(min, max) > 0) throw new Error(`firmware ${label}: min > max`);
          let id = text(f.id, 60).replace(/[^a-zA-Z0-9_-]/g, "-") || `${current.id}-${index}`;
          while (usedIds.has(id)) id += "x";
          usedIds.add(id);
          return {
            id,
            label,
            variant,
            min,
            max,
            status: f.status,
            title: text(f.title, 120),
            explanation: text(f.explanation, 900),
            method: text(f.method, 160) || "—",
          };
        })
      : [];
  if (mode === "firmware" && firmwares.length === 0) throw new Error("mode=firmware but no firmware ranges");
  if (mode === "firmware" && current.variants.length > 1) {
    for (const v of current.variants) {
      if (!firmwares.some((f) => f.variant === v.id || f.variant === "")) {
        throw new Error(`no firmware range covers variant ${v.id}`);
      }
    }
  }

  const sources = (Array.isArray(obj.sources) ? obj.sources : [])
    .map((raw) => {
      const s = (raw ?? {}) as Record<string, unknown>;
      return { title: text(s.title, 120), url: text(s.url, 500) };
    })
    .filter((s) => isHttpUrl(s.url))
    .slice(0, 4);

  return {
    changed: obj.changed === true,
    changeSummary: text(obj.change_summary, 300),
    console: {
      ...current,
      mode,
      latestFirmware: text(obj.latest_firmware, 40),
      summary: { status: obj.summary_status, text: summaryText },
      allVersions,
      firmwares,
      sources: sources.length > 0 ? sources : current.sources,
    },
  };
}
