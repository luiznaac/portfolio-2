// Pure allocation-tree helpers — no React imports here (see AGENTS.md). The Alocação page maps the
// backend's nested tree into flat, indented rows and scales the deficit/drift bars through this
// module, so the flattening and the bar arithmetic are unit-tested without rendering.

import type {
  Allocation,
  AssetClass,
  ClassAllocation,
  StrategyAllocation,
  SubClassAllocation,
  TickerAllocation,
} from "../api/types.ts";
import { subClassLabel } from "./fixedIncome.ts";
import { assetClassLabel } from "./month.ts";

export type AllocationRowKind = "capital" | "class" | "strategy" | "sub_class" | "ticker";

export interface AllocationRow {
  key: string;
  kind: AllocationRowKind;
  depth: number;
  label: string;
  target?: number;
  ideal: number;
  actual: number;
  deficit: number;
  drift: number;
  expandable: boolean;
}

// A row's stable identity — the tree's expand/collapse toggle keys on it. Strategies and tickers are
// namespaced by their class so two strategies that share a name never collide.
export function classRowKey(assetClass: AssetClass): string {
  return `class:${assetClass}`;
}

export function strategyRowKey(assetClass: AssetClass, strategy: string): string {
  return `strategy:${assetClass}:${strategy}`;
}

export function tickerRowKey(assetClass: AssetClass, strategy: string, ticker: string): string {
  return `ticker:${assetClass}:${strategy}:${ticker}`;
}

// The tree as indented rows: the Capital root, then every class; a class's strategies (with their
// tickers) or an RF class's sub-classes appear only while that row is expanded.
export function allocationRows(
  allocation: Allocation,
  expanded: ReadonlySet<string>,
): AllocationRow[] {
  const rows: AllocationRow[] = [
    {
      key: "capital",
      kind: "capital",
      depth: 0,
      label: "Capital",
      ideal: allocation.ideal,
      actual: allocation.actual,
      deficit: allocation.deficit,
      drift: allocation.drift,
      expandable: false,
    },
  ];

  for (const cls of allocation.classes) {
    const key = classRowKey(cls.asset_class);
    rows.push(classRow(cls, key));
    if (!expanded.has(key)) continue;

    if (cls.sub_classes.length > 0) {
      for (const subClass of cls.sub_classes) rows.push(subClassRow(subClass));
      continue;
    }

    for (const strategy of cls.strategies) {
      const strategyKey = strategyRowKey(cls.asset_class, strategy.strategy);
      rows.push(strategyRow(strategy, strategyKey));
      if (!expanded.has(strategyKey)) continue;
      for (const ticker of strategy.tickers) rows.push(tickerRow(cls, strategy, ticker));
    }
  }

  return rows;
}

function classRow(cls: ClassAllocation, key: string): AllocationRow {
  return {
    key,
    kind: "class",
    depth: 1,
    label: assetClassLabel(cls.asset_class),
    target: cls.target,
    ideal: cls.ideal,
    actual: cls.actual,
    deficit: cls.deficit,
    drift: cls.drift,
    expandable: cls.strategies.length > 0 || cls.sub_classes.length > 0,
  };
}

function strategyRow(strategy: StrategyAllocation, key: string): AllocationRow {
  return {
    key,
    kind: "strategy",
    depth: 2,
    label: strategy.strategy,
    target: strategy.target,
    ideal: strategy.ideal,
    actual: strategy.actual,
    deficit: strategy.deficit,
    drift: strategy.drift,
    expandable: strategy.tickers.length > 0,
  };
}

function subClassRow(subClass: SubClassAllocation): AllocationRow {
  return {
    key: `subclass:${subClass.sub_class}`,
    kind: "sub_class",
    depth: 2,
    label: subClassLabel(subClass.sub_class),
    target: subClass.target,
    ideal: subClass.ideal,
    actual: subClass.actual,
    deficit: subClass.deficit,
    drift: subClass.drift,
    expandable: false,
  };
}

function tickerRow(
  cls: ClassAllocation,
  strategy: StrategyAllocation,
  ticker: TickerAllocation,
): AllocationRow {
  return {
    key: tickerRowKey(cls.asset_class, strategy.strategy, ticker.ticker),
    kind: "ticker",
    depth: 3,
    label: ticker.ticker,
    target: ticker.target,
    ideal: ticker.ideal,
    actual: ticker.actual,
    deficit: ticker.deficit,
    drift: ticker.drift,
    expandable: false,
  };
}

// The divergent bars fill from a centre axis; a value maps to at most half the track, so both a
// deficit (left, blue) and an excess (right, amber) fit on the same scale.
export function barWidth(value: number, maxAbs: number): number {
  if (maxAbs <= 0) return 0;
  return Math.min(50, (Math.abs(value) / maxAbs) * 50);
}

export function maxAbs(rows: AllocationRow[], value: (row: AllocationRow) => number): number {
  return rows.reduce((max, row) => Math.max(max, Math.abs(value(row))), 0);
}

export function maxAbsDeficit(rows: AllocationRow[]): number {
  return maxAbs(rows, (row) => row.deficit);
}

export function maxAbsDrift(rows: AllocationRow[]): number {
  return maxAbs(rows, (row) => row.drift);
}

// The hatched share of the reconciliation bar: the unattributed value over the listed book. RF is the
// consolidation engine's fruit and is never "unattributed", so it is left out of the denominator.
export function unattributedShare(allocation: Allocation): number {
  const fixedIncome =
    allocation.classes.find((cls) => cls.asset_class === "RENDA_FIXA")?.actual ?? 0;
  const listed = allocation.actual - fixedIncome;
  return listed > 0 ? allocation.unattributed / listed : 0;
}

const ONE_DECIMAL = new Intl.NumberFormat("pt-BR", {
  minimumFractionDigits: 1,
  maximumFractionDigits: 1,
});

// A target is the percentage number itself (50 = half); drift is already in percentage points.
export function formatTarget(percentage: number | undefined): string {
  return percentage === undefined ? "—" : `${ONE_DECIMAL.format(percentage)}%`;
}

export function formatPoints(drift: number): string {
  return `${drift > 0 ? "+" : ""}${ONE_DECIMAL.format(drift)} p.p.`;
}
