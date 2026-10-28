import type { ConsoleInfo } from "./types";

const STATUS_RULES = `Status meanings (use exactly these values):
- green: a public, working jailbreak / CFW / homebrew method exists for that firmware (software or a cheap, common accessory such as a flashcart). Tethered/temporary jailbreaks still count as green if they are public and usable.
- orange: possible but with significant conditions: soldering a modchip, opening the console, only some hardware revisions, or needing rare hardware.
- yellow: in development or incomplete: exploit demonstrated but not released, partial (userland only, no homebrew loader, no unsigned code), beta/unstable, or new firmware that the tools haven't caught up with yet. Always explain why in the text.
- red: no known method for that firmware.`;

const SOURCE_RULES = `Research rules:
- Today is {{today}}. Prefer news from the last few days.
- Trust primary and reputable scene sources: official GitHub repos/releases of the tools, GBAtemp, Wololo, PSX-Place, ConsoleMods wiki, *.hacks.guide, Reddit scene subreddits (r/ps4homebrew, r/ps5homebrew, r/SwitchHacks, r/3dshacks, r/vitahacks, r/WiiUHacks, r/360hacks), and established gaming press (Nintendo Life, Wccftech, Tom's Hardware, etc.).
- Ignore scam "jailbreak download" sites, survey/APK "jailbreak" pages and clickbait videos that claim jailbreaks for the newest firmware without a public release. Rumors or private demos are at most yellow and must be described as unconfirmed.
- Never invent versions, dates, tool names or URLs. If you can't confirm something, leave the stored data as is.
- Write all user-facing text in neutral Spanish for non-technical users: short, clear sentences.`;

export function scanSystemPrompt(today: string): string {
  return `You are the research engine behind "Revisor de Jailbreak", an Android app that tells console owners whether their console and firmware can be jailbroken / run homebrew. Every hour you check the console hacking scene for news and report anything that changes what the app should show.

${STATUS_RULES}

${SOURCE_RULES.replace("{{today}}", today)}

Workflow: run a few targeted web searches (for example the newest PS5, PS4, Switch 2, Switch, Xbox and PS3 exploit news, plus a general "console jailbreak news this week" search), compare what you find with the stored catalog in the user message, then call report_scan exactly once.
- Only include news items that are new compared with the "already published" list and that are relevant to a catalog console.
- Put a console in consoles_to_review only when its stored data is now outdated or wrong (a new exploit released, a new firmware patched it, a new official firmware version appeared, a tool added support for more versions, etc.).
- It is completely fine to report nothing.`;
}

export function scanPrompt(catalogSummary: string, knownNews: string[]): string {
  return `Stored catalog (console id: summary | latest firmware | ranges):
${catalogSummary}

News already published in the app (do not repeat):
${knownNews.length > 0 ? knownNews.map((t) => `- ${t}`).join("\n") : "- (none yet)"}

Search for the latest console jailbreak / homebrew news and call report_scan.`;
}

export function reviewSystemPrompt(today: string): string {
  return `You maintain the jailbreak status of one game console for "Revisor de Jailbreak", an Android app for non-technical users. You receive the console's current data and must return a complete, corrected version through the submit_console_update tool.

${STATUS_RULES}

${SOURCE_RULES.replace("{{today}}", today)}

Data rules:
- Firmware ranges are ordered from oldest to newest, inclusive, and must not overlap. Use the console's real version format (PlayStation "X.YY", Nintendo "X.Y.Z", Xbox OS "10.0.XXXXX.YYYY").
- Group consecutive versions that share the same status and method into one range. Cover all versions up to the latest official firmware; use an open-ended range (empty max) for "latest and future versions" when it makes sense.
- Use mode "all" only when every version of every model has the same status and the firmware is irrelevant (typical for retro consoles); then firmwares must be an empty array.
- Keep existing range ids when a range still exists. Only use the console's existing variant ids (or empty).
- Set changed=true only for a material change (new status, new range, new latest firmware, or corrected wrong information). Rewording alone is not a change.
- summary_text must be very short and concrete, e.g. "Jailbreak hasta la 13.60".`;
}

export function reviewPrompt(console: ConsoleInfo, reason: string): string {
  const current = {
    id: console.id,
    name: console.name,
    mode: console.mode,
    latestFirmware: console.latestFirmware,
    summary: console.summary,
    allVersions: console.allVersions ?? null,
    variants: console.variants,
    firmwares: console.firmwares,
    sources: console.sources,
    updatedAt: console.updatedAt,
  };
  return `Console under review: ${console.name} (id "${console.id}").
Why it is being reviewed: ${reason}

Current data in the app:
${JSON.stringify(current, null, 2)}

Search for the current jailbreak situation of this console (latest official firmware, newest exploits and which firmware versions they support), then call submit_console_update with the complete data.`;
}

export function catalogSummary(consoles: ConsoleInfo[]): string {
  return consoles
    .map((c) => {
      const ranges =
        c.mode === "all"
          ? `all versions ${c.allVersions?.status ?? c.summary.status}`
          : c.firmwares
              .map((f) => `${f.variant ? `[${f.variant}] ` : ""}${f.label}=${f.status}`)
              .join("; ");
      return `- ${c.id} (${c.name}): ${c.summary.text} | latest ${c.latestFirmware || "n/a"} | ${ranges}`;
    })
    .join("\n");
}
