import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { test } from "node:test";
import { parseConsoleUpdate, parseScan } from "./schema";
import type { Catalog, ConsoleInfo } from "./types";
import { dueConsoles } from "./updater";
import { compareVersions } from "./versions";

const catalog: Catalog = JSON.parse(readFileSync(join(__dirname, "data", "consoles.json"), "utf8"));
const byId = (id: string) => catalog.consoles.find((c) => c.id === id) as ConsoleInfo;

test("compareVersions matches the app's rules", () => {
  assert.equal(compareVersions("9.03", "9.5"), -1);
  assert.equal(compareVersions("9.5", "9.50"), 0);
  assert.equal(compareVersions("13.60", "13.52"), 1);
  assert.equal(compareVersions("23.0.1", "4.1.0"), 1);
  assert.equal(compareVersions("10.0.25398.4909", "10.0.25398.4478"), 1);
});

test("parseScan keeps valid news and drops unknown consoles", () => {
  const ids = new Set(catalog.consoles.map((c) => c.id));
  const result = parseScan(
    {
      news: [
        {
          title: "Nuevo exploit para PS5 14.00",
          summary: "Se publicó un exploit de kernel.",
          console_ids: ["ps5", "nope"],
          status: "green",
          url: "https://example.com/a",
          source: "GBAtemp",
          published_date: "2026-10-27",
        },
        { title: "Sin consola", summary: "x", console_ids: ["nope"], status: "red", url: "", source: "", published_date: "" },
      ],
      consoles_to_review: [
        { console_id: "ps5", reason: "nuevo exploit" },
        { console_id: "ps5", reason: "duplicado" },
        { console_id: "nope", reason: "desconocida" },
      ],
    },
    ids,
  );
  assert.equal(result.news.length, 1);
  assert.deepEqual(result.news[0].consoleIds, ["ps5"]);
  assert.deepEqual(result.review, [{ consoleId: "ps5", reason: "nuevo exploit" }]);
});

const ps5Update = (overrides: Record<string, unknown> = {}) => ({
  changed: true,
  change_summary: "Nuevo exploit hasta 14.00",
  summary_status: "green",
  summary_text: "Jailbreak hasta la 14.00",
  latest_firmware: "14.20",
  mode: "firmware",
  all_versions: { status: "green", title: "", explanation: "", method: "" },
  firmwares: [
    { id: "ps5-100-1400", label: "1.00 – 14.00", variant: "", min: "1.00", max: "14.00", status: "green", title: "Jailbreak", explanation: "Funciona.", method: "X" },
    { id: "ps5-1410-plus", label: "14.10 o superior", variant: "", min: "14.10", max: "", status: "red", title: "Sin jailbreak", explanation: "No.", method: "—" },
  ],
  sources: [{ title: "GitHub", url: "https://github.com/example/exploit" }],
  ...overrides,
});

test("parseConsoleUpdate merges a valid update and keeps static fields", () => {
  const current = byId("ps5");
  const update = parseConsoleUpdate(ps5Update(), current);
  assert.equal(update.changed, true);
  assert.equal(update.console.name, current.name);
  assert.equal(update.console.firmwares.length, 2);
  assert.equal(update.console.summary.text, "Jailbreak hasta la 14.00");
  assert.equal(update.console.allVersions, null);
});

test("parseConsoleUpdate rejects inverted ranges and unknown variants", () => {
  const current = byId("ps5");
  const inverted = ps5Update({
    firmwares: [{ id: "a", label: "x", variant: "", min: "9.00", max: "5.00", status: "green", title: "", explanation: "", method: "" }],
  });
  assert.throws(() => parseConsoleUpdate(inverted, current), /min > max/);
  const badVariant = ps5Update({
    firmwares: [{ id: "a", label: "x", variant: "pro", min: "", max: "", status: "green", title: "", explanation: "", method: "" }],
  });
  assert.throws(() => parseConsoleUpdate(badVariant, current), /unknown variant/);
});

test("parseConsoleUpdate requires every Switch model to stay covered", () => {
  const current = byId("switch1");
  const onlyUnpatched = ps5Update({
    firmwares: [{ id: "a", label: "x", variant: "unpatched", min: "1.0.0", max: "23.0.1", status: "green", title: "", explanation: "", method: "" }],
  });
  assert.throws(() => parseConsoleUpdate(onlyUnpatched, current), /variant patched/);
});

test("dueConsoles returns never-checked consoles first and skips fresh ones", () => {
  const now = Date.parse("2026-10-28T12:00:00Z");
  const fresh = { ...byId("ps5"), lastCheckedAt: "2026-10-28T11:00:00Z" };
  const stale = { ...byId("ps4"), lastCheckedAt: "2026-10-26T00:00:00Z" };
  const never = { ...byId("switch2"), lastCheckedAt: "" };
  const due = dueConsoles([fresh, stale, never], now).map((c) => c.id);
  assert.deepEqual(due, ["switch2", "ps4"]);
});

test("bundled catalog ranges are well formed", () => {
  for (const c of catalog.consoles) {
    if (c.mode === "all") {
      assert.ok(c.allVersions, `${c.id} needs allVersions`);
      continue;
    }
    for (const f of c.firmwares) {
      if (f.min && f.max) assert.ok(compareVersions(f.min, f.max) <= 0, `${f.id} min <= max`);
    }
  }
});
