// Shapes shared with the Android app (see data/consoles.json and the app's Models.kt).

export const STATUSES = ["green", "orange", "yellow", "red"] as const;
export type StatusValue = (typeof STATUSES)[number];

export interface Brand {
  id: string;
  name: string;
  company: string;
  color: string;
  order: number;
  description: string;
}

export interface StatusInfo {
  status: StatusValue;
  title: string;
  explanation: string;
  method: string;
}

export interface Variant {
  id: string;
  name: string;
  description: string;
}

export interface FirmwareRange {
  id: string;
  label: string;
  variant: string;
  min: string;
  max: string;
  status: StatusValue;
  title: string;
  explanation: string;
  method: string;
}

export interface Source {
  title: string;
  url: string;
}

export interface ConsoleInfo {
  id: string;
  brandId: string;
  name: string;
  shortName: string;
  year: number;
  formFactor: string;
  legacy: boolean;
  mode: "all" | "firmware";
  latestFirmware: string;
  checkEveryHours: number;
  summary: { status: StatusValue; text: string };
  versionHint: string;
  allVersions?: StatusInfo | null;
  variants: Variant[];
  firmwares: FirmwareRange[];
  sources: Source[];
  updatedAt: string;
  lastCheckedAt?: string;
}

export interface Catalog {
  seedVersion: number;
  generatedAt: string;
  brands: Brand[];
  consoles: ConsoleInfo[];
}

export interface NewsItem {
  title: string;
  summary: string;
  consoleIds: string[];
  status: StatusValue | "unknown";
  url: string;
  source: string;
  publishedAt: string;
  createdAt: string;
}
