import type {
  AssetClass,
  Edition,
  EditionDiff,
  EditionEntry,
  Rating,
  Strategy,
} from "../api/types.ts";

// Pure helpers for the Estratégias screen — no React imports here (see AGENTS.md). The screen's
// grouping, labels and diff summary are unit-tested in strategy.test.ts.

export interface StrategyGroup {
  assetClass: AssetClass;
  strategies: Strategy[];
}

const CLASS_ORDER: AssetClass[] = ["ACOES", "FIIS", "RENDA_FIXA"];

// The list groups by asset class in the chain's order, skipping a class with no strategies. Input
// order is preserved inside a group.
export function groupStrategiesByClass(strategies: Strategy[]): StrategyGroup[] {
  return CLASS_ORDER.map((assetClass) => ({
    assetClass,
    strategies: strategies.filter((strategy) => strategy.asset_class === assetClass),
  })).filter((group) => group.strategies.length > 0);
}

const MONTH_LABELS = [
  "jan",
  "fev",
  "mar",
  "abr",
  "mai",
  "jun",
  "jul",
  "ago",
  "set",
  "out",
  "nov",
  "dez",
];

// "2026-09" -> "set/26", matching the reference's edition chips.
export function referenceMonthLabel(referenceMonth: string): string {
  const [year, month] = referenceMonth.split("-");
  const label = MONTH_LABELS[Number(month) - 1] ?? month;
  return `${label}/${year.slice(2)}`;
}

// The import table's Diff column: "+2 −1 ~2" entered/exited/changed, or the first-edition case.
export function editionDiffSummary(diff?: EditionDiff): string {
  if (!diff) return "primeira edição";
  if (diff.entered.length + diff.exited.length + diff.changed.length === 0) return "sem mudanças";
  return `+${diff.entered.length} −${diff.exited.length} ~${diff.changed.length}`;
}

// The table's Sum column: weights should total 100, rounded to drop floating-point noise.
export function editionWeightSum(entries: EditionEntry[]): number {
  const total = entries.reduce((sum, entry) => sum + entry.weight, 0);
  return Math.round(total * 100) / 100;
}

// The weight rule the server also enforces (editionWeightProblem): a portfolio sums to 100 within a
// cent. The import table and the preview drawer both read it, so the tolerance lives in one place.
const WEIGHT_TOLERANCE = 0.01;

export function isWeightSumValid(entries: EditionEntry[]): boolean {
  return Math.abs(editionWeightSum(entries) - 100) <= WEIGHT_TOLERANCE;
}

export function ratingLabel(rating?: Rating): string {
  switch (rating) {
    case "BUY":
      return "Compra";
    case "NEUTRAL":
      return "Neutro";
    case "SELL":
      return "Venda";
    default:
      return "—";
  }
}

export function isEditionForMonth(edition: Edition, referenceMonth: string): boolean {
  return edition.reference_month === referenceMonth;
}

// Inline override in the preview drawer: replace one entry immutably, leaving the rest identical.
export function overrideEntry(
  entries: EditionEntry[],
  index: number,
  patch: Partial<EditionEntry>,
): EditionEntry[] {
  return entries.map((entry, position) => (position === index ? { ...entry, ...patch } : entry));
}

// File-name hints, most specific first: "Top Dividendos Plus" must not match plain "Dividendos".
const FILE_HINTS: { pattern: RegExp; name: string }[] = [
  { pattern: /plus/i, name: "Dividendos Plus" },
  { pattern: /small\s*caps?/i, name: "Small Caps" },
  { pattern: /fundamentalista|fii/i, name: "Fundamentalista" },
  { pattern: /top\s*a[cç][oõ]es/i, name: "Top Ações" },
  { pattern: /dividendos/i, name: "Top Dividendos" },
];

// Suggests the strategy a dropped report belongs to, from its file name. The import view seeds each
// row with this and the user can override it; an unrecognised name falls back to the first strategy.
export function suggestStrategy(strategies: Strategy[], fileName: string): Strategy | undefined {
  const hint = FILE_HINTS.find(({ pattern }) => pattern.test(fileName));
  const match = hint
    ? strategies.find((strategy) => strategy.name.toLowerCase().includes(hint.name.toLowerCase()))
    : undefined;
  return match ?? strategies[0];
}
