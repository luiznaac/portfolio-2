import type {
  AssetClass,
  EditionDiff,
  EditionEntry,
  EditionSummary,
  ManualAllocation,
  Rating,
  Strategy,
  StrategyAllocation,
} from "../api/types.ts";
import { ASSET_CLASSES, MONTHS } from "./month.ts";

// Pure helpers for the Estratégias screens — no React imports here (see AGENTS.md). The list's
// row join, labels and edition chips are unit-tested in strategy.test.ts.

export interface StrategyGroup {
  assetClass: AssetClass;
  strategies: Strategy[];
}

// The list groups by asset class in the chain's order, skipping a class with no strategies. Input
// order is preserved inside a group, except that the manual bucket is listed last.
export function groupStrategiesByClass(strategies: Strategy[]): StrategyGroup[] {
  return ASSET_CLASSES.map((assetClass) => ({
    assetClass,
    strategies: strategies
      .filter((strategy) => strategy.asset_class === assetClass)
      .sort((left, right) => Number(left.kind === "MANUAL") - Number(right.kind === "MANUAL")),
  })).filter((group) => group.strategies.length > 0);
}

// The allocation tree slice the comparison list reads: each class's report strategies and its
// optional manual bucket, narrowed from the mirrored wire types. `Allocation` satisfies it
// structurally; the list needs nothing else.
export interface StrategyPricing {
  classes: {
    strategies: Pick<StrategyAllocation, "strategy" | "actual" | "drift">[];
    manual?: Pick<ManualAllocation, "strategy" | "actual"> | null;
  }[];
}

export interface StrategyListRow {
  strategy: Strategy;
  // The strategy's priced Actual in the open month's allocation tree, absent when the chain carries
  // no rung for it — rendered "—", never R$ 0,00.
  value?: number;
  // Drift in p.p.; a manual bucket has no target, so it is always absent.
  drift?: number;
}

// One row per Strategy for the comparison list, in class order with each class's bucket last. The
// value and drift join the allocation tree by the Strategy's unique name; a strategy the chain does
// not carry stays unpriced. The manual bucket never carries drift (no target).
export function strategyRows(strategies: Strategy[], pricing?: StrategyPricing): StrategyListRow[] {
  const priced = new Map<string, { value?: number; drift?: number }>();
  for (const assetClass of pricing?.classes ?? []) {
    for (const strategy of assetClass.strategies) {
      priced.set(strategy.strategy, { value: strategy.actual, drift: strategy.drift });
    }
    if (assetClass.manual) {
      priced.set(assetClass.manual.strategy, { value: assetClass.manual.actual });
    }
  }

  return groupStrategiesByClass(strategies).flatMap((group) =>
    group.strategies.map((strategy) => {
      const node = priced.get(strategy.name);
      return { strategy, value: node?.value, drift: node?.drift };
    }),
  );
}

export function isReportDriven(strategy: Strategy | null | undefined): boolean {
  return strategy?.kind === "REPORT_DRIVEN";
}

export function reportDrivenStrategies(strategies: Strategy[]): Strategy[] {
  return strategies.filter(isReportDriven);
}

export interface EditionStatusChip {
  label: string;
  tone: "green" | "amber" | "neutral";
}

// The comparison row's edition cell: the latest edition's month, ✓ when active and a "revisão"
// badge when still staged.
export function editionSummaryChip(latest: EditionSummary): EditionStatusChip {
  return latest.status === "ACTIVE"
    ? { label: `${referenceMonthLabel(latest.reference_month)} ✓`, tone: "green" }
    : { label: `${referenceMonthLabel(latest.reference_month)} · revisão`, tone: "amber" };
}

// "2026-09" -> "set/26", matching the reference's edition chips.
export function referenceMonthLabel(referenceMonth: string): string {
  const [year, month] = referenceMonth.split("-");
  const label = MONTHS[Number(month) - 1] ?? month;
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

// Suggests the strategy a dropped report belongs to, from its file name. Only report-driven
// Strategies can receive a report, so the bucket is never a candidate; an unrecognised name falls
// back to the first report-driven Strategy.
export function suggestStrategy(strategies: Strategy[], fileName: string): Strategy | undefined {
  const report = reportDrivenStrategies(strategies);
  const hint = FILE_HINTS.find(({ pattern }) => pattern.test(fileName));
  const match = hint
    ? report.find((strategy) => strategy.name.toLowerCase().includes(hint.name.toLowerCase()))
    : undefined;
  return match ?? report[0];
}
