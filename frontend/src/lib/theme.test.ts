import { readFileSync, readdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

// The theme contract from issue #205, extended by the A · Console foundation (issue #366): the
// palette, type scale and density tokens live in the Tailwind v4 `@theme` (index.css), the primitives
// consume them by name, and no shipped component falls back to a raw colour or a scaffold default.
// This reads source files, so it is a build-time invariant guard, not component-render coverage (see
// AGENTS.md on the testing scope).

const SRC = join(dirname(fileURLToPath(import.meta.url)), "..");
const read = (relative: string) => readFileSync(join(SRC, relative), "utf8");

function sourceFiles(dir: string): string[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const path = join(dir, entry.name);
    if (entry.isDirectory()) return sourceFiles(path);
    return /\.(css|tsx|ts)$/.test(entry.name) ? [path] : [];
  });
}

// Every shipped module, wherever it lives: a raw colour in a hook or a root file is the same
// contract breach as one in a page, so the guard scans the whole source tree, not just the UI
// folders. Test files legitimately carry palette fixtures and are skipped.
function shippedSource(): string[] {
  return sourceFiles(SRC).filter((file) => /\.(ts|tsx)$/.test(file) && !/\.test\.tsx?$/.test(file));
}

// Comments legitimately mention issue numbers (#205, #366, …), so strip them before looking for a
// colour literal.
function stripComments(source: string): string {
  return source.replace(/\/\*[\s\S]*?\*\//g, "").replace(/(^|[^:])\/\/.*$/gm, "$1");
}

const indexCss = read("index.css");

describe("theme tokens", () => {
  // A · Console palette (issue #366). Semantic names, never the raw hex at a call site.
  const CONSOLE: Record<string, string> = {
    "--color-bg": "#0b0e12",
    "--color-panel": "#11161c",
    "--color-panel-2": "#161d25",
    "--color-panel-3": "#1b232c",
    "--color-line": "#232c37",
    "--color-line-soft": "#19212a",
    "--color-text": "#e7edf4",
    "--color-muted": "#9ba8b7",
    "--color-faint": "#64707f",
    "--color-action": "#78a9ff",
    "--color-action-bg": "#14243d",
    "--color-return-up": "#54c98c",
    "--color-return-up-bg": "#10241b",
    "--color-return-down": "#f0736a",
    "--color-return-down-bg": "#2a1515",
    "--color-attention": "#d9a45e",
    "--color-attention-bg": "#2a2115",
  };

  // Fixed sign semantics: deficit/short/action blue, surplus/attention amber, return green/red.
  const SIGN_ALIASES: Record<string, string> = {
    "--color-deficit": "--color-action",
    "--color-surplus": "--color-attention",
  };

  // The legacy vocabulary the un-migrated screens already consume. Each is an alias of its console
  // name, so a slice can move over without a second palette drifting away from this one.
  const LEGACY: Record<string, string> = {
    "--color-ground": "--color-bg",
    "--color-surface": "--color-panel",
    "--color-surface-2": "--color-panel-2",
    "--color-surface-3": "--color-panel-3",
    "--color-ink": "--color-text",
    "--color-ink-dim": "--color-muted",
    "--color-ink-faint": "--color-faint",
    "--color-principal": "--color-deficit",
    "--color-principal-bg": "--color-action-bg",
    "--color-yield": "--color-return-up",
    "--color-yield-bg": "--color-return-up-bg",
    "--color-tax": "--color-return-down",
    "--color-tax-bg": "--color-return-down-bg",
    "--color-accent": "--color-surplus",
    "--color-accent-bg": "--color-attention-bg",
    "--color-blend": "--color-attention",
    "--color-cdi": "--color-action",
    "--color-ibov": "--color-return-up",
  };

  // Tokens that stay literal because they have no console counterpart yet.
  const SUPPORT: Record<string, string> = {
    "--color-accent-2": "#b9873f",
    "--color-accent-ink": "#16090a",
    "--color-violet": "#a78bfa",
    "--color-violet-bg": "#a78bfa1f",
    "--color-ipca": "#f472b6",
  };

  const FONTS = ["--font-sans", "--font-cond", "--font-mono"];

  // Type: 12,5 body · 11,5 tabular numerals · 9,5 uppercase labels · 17/600 page titles.
  const TYPE: Record<string, string> = {
    "--text-body": "12.5px",
    "--text-num": "11.5px",
    "--text-label": "9.5px",
    "--text-title": "17px",
  };

  // Density: 26px rows, 1px hairlines, 3px panels, 198px sidebar, 44px top bar, 26px head floor.
  const DENSITY: Record<string, string> = {
    "--height-row": "26px",
    "--height-pagehead": "26px",
    "--height-topbar": "44px",
    "--width-sidebar": "198px",
    "--hairline": "1px",
    "--radius-panel": "3px",
  };

  it.each(Object.entries(CONSOLE))("declares %s as %s", (token, value) => {
    expect(indexCss).toContain(`${token}: ${value};`);
  });

  it.each(Object.entries(SIGN_ALIASES))("aliases %s to %s", (token, target) => {
    expect(indexCss).toContain(`${token}: var(${target});`);
  });

  it.each(Object.entries(LEGACY))("keeps legacy %s on %s", (token, target) => {
    expect(indexCss).toContain(`${token}: var(${target});`);
  });

  it.each(Object.entries(TYPE))("declares %s as %s", (token, value) => {
    expect(indexCss).toContain(`${token}: ${value};`);
  });

  it.each(Object.entries(DENSITY))("declares %s as %s", (token, value) => {
    expect(indexCss).toContain(`${token}: ${value};`);
  });

  it.each(Object.entries(SUPPORT))("declares %s as %s", (token, value) => {
    expect(indexCss).toContain(`${token}: ${value};`);
  });

  it.each(FONTS)("declares %s in @theme", (token) => {
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

describe("shipped source", () => {
  // A default-palette shade utility (bg-slate-900, text-emerald-400, hover:bg-rose-500/15, …) or a
  // black/white utility. Our tokens never carry a numeric shade, so this only matches scaffold
  // leftovers.
  const DEFAULT_SHADE =
    /(?:bg|text|border|ring|divide|from|to|via|fill|stroke|outline|decoration|placeholder|caret|accent|shadow)-(?:(?:slate|gray|zinc|neutral|stone|red|orange|amber|yellow|lime|green|emerald|teal|cyan|sky|blue|indigo|violet|purple|fuchsia|pink|rose)-\d{2,3}|white|black)\b/;

  // A colour literal anywhere but the theme seam — hex, rgb(a), hsl(a), oklch/oklab and the CSS
  // colour() function. The palette belongs in index.css and is consumed by name.
  const RAW_COLOUR = /#[0-9a-fA-F]{3,8}\b|\b(?:rgb|rgba|hsl|hsla|oklch|oklab|lab|lch|color)\s*\(/;

  it("keeps every shipped module free of default-palette shades", () => {
    const offenders = shippedSource()
      .filter((file) => DEFAULT_SHADE.test(readFileSync(file, "utf8")))
      .map((file) => file.slice(SRC.length + 1));

    expect(offenders).toEqual([]);
  });

  it("keeps every shipped module free of raw colours", () => {
    const offenders = shippedSource()
      .filter((file) => RAW_COLOUR.test(stripComments(readFileSync(file, "utf8"))))
      .map((file) => file.slice(SRC.length + 1));

    expect(offenders).toEqual([]);
  });
});
