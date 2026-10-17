import { readFileSync, readdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

// The theme contract from issue #205: the reference palette, sign convention, radii and fonts live
// in the Tailwind v4 `@theme` (index.css), and no shipped component falls back to the scaffold's
// generic Tailwind defaults. This reads source files, so it is a build-time invariant guard, not
// component-render coverage (see AGENTS.md on the testing scope).

const SRC = join(dirname(fileURLToPath(import.meta.url)), "..");
const read = (relative: string) => readFileSync(join(SRC, relative), "utf8");

function sourceFiles(dir: string): string[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const path = join(dir, entry.name);
    if (entry.isDirectory()) return sourceFiles(path);
    return /\.(css|tsx|ts)$/.test(entry.name) ? [path] : [];
  });
}

const indexCss = read("index.css");

describe("theme tokens", () => {
  const CHROME = [
    "--color-ground",
    "--color-surface",
    "--color-surface-2",
    "--color-surface-3",
    "--color-line",
    "--color-line-soft",
    "--color-ink",
    "--color-ink-dim",
    "--color-ink-faint",
  ];

  const SIGN = [
    "--color-principal",
    "--color-principal-bg",
    "--color-yield",
    "--color-yield-bg",
    "--color-tax",
    "--color-tax-bg",
    "--color-accent",
    "--color-accent-2",
    "--color-accent-bg",
    "--color-accent-ink",
    "--color-violet",
    "--color-violet-bg",
  ];

  const RADII = ["--radius-xs", "--radius-sm", "--radius-md", "--radius-lg", "--radius-xl"];

  const FONTS = ["--font-sans", "--font-cond", "--font-mono"];

  it.each([...CHROME, ...SIGN, ...RADII, ...FONTS])("declares %s in @theme", (token) => {
    expect(indexCss).toContain(`${token}:`);
  });
});

describe("fonts", () => {
  it("bundles the three IBM Plex families via @fontsource", () => {
    const sources = sourceFiles(SRC)
      .map((file) => readFileSync(file, "utf8"))
      .join("\n");

    expect(sources).toContain("@fontsource/ibm-plex-sans/");
    expect(sources).toContain("@fontsource/ibm-plex-sans-condensed/");
    expect(sources).toContain("@fontsource/ibm-plex-mono/");
  });
});

describe("shipped components", () => {
  // A default-palette shade utility (bg-slate-900, text-emerald-400, hover:bg-rose-500/15, …).
  // Our tokens never carry a numeric shade, so this only ever matches scaffold leftovers.
  const DEFAULT_SHADE =
    /(?:bg|text|border|ring|divide|from|to|via|fill|stroke|outline|decoration|placeholder|caret|accent|shadow)-(?:slate|gray|zinc|neutral|stone|red|orange|amber|yellow|lime|green|emerald|teal|cyan|sky|blue|indigo|violet|purple|fuchsia|pink|rose)-\d{2,3}\b/;

  it.each(["components", "pages"])("keeps %s free of default-palette shades", (dir) => {
    const offenders = sourceFiles(join(SRC, dir))
      .filter((file) => DEFAULT_SHADE.test(readFileSync(file, "utf8")))
      .map((file) => file.slice(SRC.length + 1));

    expect(offenders).toEqual([]);
  });
});
