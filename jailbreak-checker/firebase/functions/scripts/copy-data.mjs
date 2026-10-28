// Copies the shared catalog (../../data/consoles.json) next to the compiled code,
// because Cloud Functions only uploads the functions/ directory.
import { copyFileSync, mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const source = join(here, "..", "..", "..", "data", "consoles.json");
const target = join(here, "..", "lib", "data", "consoles.json");
mkdirSync(dirname(target), { recursive: true });
copyFileSync(source, target);
console.log(`Copied ${source} -> ${target}`);
