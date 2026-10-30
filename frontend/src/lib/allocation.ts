// Pure allocation-tree helpers — no React imports here (see AGENTS.md). The Alocação page maps the
// backend's nested tree into flat, indented rows and scales the deficit/drift bars through this
// module, so the flattening and the bar arithmetic are unit-tested without rendering.

import type {
  ActualTickerAllocation,
  Allocation,
  AssetClass,
  ClassAllocation,
  StrategyAllocation,
  SubClassAllocation,
  TickerAllocation,
} from "../api/types.ts";
import { subClassLabel } from "./fixedIncome.ts";
import { formatPercent } from "./format.ts";
import { assetClassLabel } from "./month.ts";

export type AllocationRowKind =
  | "capital"
  | "class"
  | "strategy"
  | "sub_class"
  | "ticker"
  | "manual"
  | "manual_ticker"
  | "outside"
  | "outside_ticker"
  | "unclassified"
  | "unclassified_ticker";

// A planned rung carries its Alvo, Ideal, Δ R$ and Δ p.p.; an Actual-only rung (the bucket, the
// outside remainder, the unclassified line) leaves them undefined, which the page renders as "—".
export interface AllocationRow {
  key: string;
  kind: AllocationRowKind;
  depth: number;
  label: string;
  // The ticker leaf's class, so the bucket's transfer action can filter its destinations without
  // parsing the row key.
  assetClass?: AssetClass;
  // The ticker a leaf row names — the classify action keys on it.
  ticker?: string;
  // The units the leaf values; absent on rungs that aggregate other rows.
  quantity?: number;
  target?: number;
  ideal?: number;
  // Undefined on an unpriced leaf: unknown is rendered "—", never R$ 0,00.
  actual?: number;
  deficit?: number;
  drift?: number;
  // An old-but-real close: the value stays, with the date it belongs to.
  quoteDate?: string;
  stale?: boolean;
  // On the Capital root: the total covers only the priced part of the book.
  partial?: boolean;
  expandable: boolean;
}

export const UNCLASSIFIED_ROW_KEY = "unclassified";

// The planning cells a row shows while the month has a chain to scale. A month-free read leaves them
// absent, so the page renders "—" instead of zeroes for a plan that does not exist.
function planning(
  planned: boolean,
  cells: { target?: number; ideal: number; deficit: number; drift: number },
): Pick<AllocationRow, "target" | "ideal" | "deficit" | "drift"> {
  return planned ? cells : {};
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

export function manualRowKey(assetClass: AssetClass): string {
  return `manual:${assetClass}`;
}

export function manualTickerRowKey(assetClass: AssetClass, ticker: string): string {
  return `manual-ticker:${assetClass}:${ticker}`;
}

export function outsideRowKey(assetClass: AssetClass): string {
  return `outside:${assetClass}`;
}

export function outsideTickerRowKey(assetClass: AssetClass, ticker: string): string {
  return `outside-ticker:${assetClass}:${ticker}`;
}

export function unclassifiedTickerRowKey(ticker: string): string {
  return `unclassified-ticker:${ticker}`;
}

// The tree as indented rows: the Capital root, then every class; a class's report Strategies (with
// their tickers), its manual bucket and its outside row appear only while that row is expanded. The
// unclassified line sits at the root after every class, in no class rung.
export function allocationRows(
  allocation: Allocation,
  expanded: ReadonlySet<string>,
): AllocationRow[] {
  // A month-free read has no chain to scale, so Alvo, Ideal, Δ R$ and Δ p.p. are absent rather than
  // zero — a zero would read as an excess for custody that simply has no plan yet.
  const planned = allocation.period !== undefined;
  const rows: AllocationRow[] = [
    {
      key: "capital",
      kind: "capital",
      depth: 0,
      label: "Capital",
      ...planning(planned, {
        ideal: allocation.ideal,
        deficit: allocation.deficit,
        drift: allocation.drift,
      }),
      actual: allocation.actual,
      partial: allocation.partial,
      expandable: false,
    },
  ];

  for (const cls of allocation.classes) {
    const key = classRowKey(cls.asset_class);
    rows.push(classRow(cls, key, planned));
    if (!expanded.has(key)) continue;

    if (cls.sub_classes.length > 0) {
      for (const subClass of cls.sub_classes) rows.push(subClassRow(subClass, planned));
      continue;
    }

    for (const strategy of cls.strategies) {
      const strategyKey = strategyRowKey(cls.asset_class, strategy.strategy);
      rows.push(strategyRow(strategy, strategyKey, planned));
      if (!expanded.has(strategyKey)) continue;
      for (const ticker of strategy.tickers) rows.push(tickerRow(cls, strategy, ticker, planned));
    }

    if (cls.manual) {
      const manualKey = manualRowKey(cls.asset_class);
      rows.push(
        manualRow(cls.manual.strategy, cls.manual.actual, manualKey, cls.manual.tickers.length > 0),
      );
      if (expanded.has(manualKey)) {
        for (const ticker of cls.manual.tickers) {
          rows.push(actualTickerRow(cls.asset_class, ticker, "manual_ticker"));
        }
      }
    }

    if (cls.outside.tickers.length > 0) {
      const outsideKey = outsideRowKey(cls.asset_class);
      rows.push(outsideRow(cls.outside.actual, outsideKey));
      if (expanded.has(outsideKey)) {
        for (const ticker of cls.outside.tickers) {
          rows.push(actualTickerRow(cls.asset_class, ticker, "outside_ticker"));
        }
      }
    }
  }

  if (allocation.unclassified.tickers.length > 0) {
    rows.push(unclassifiedRow(allocation.unclassified.actual));
    if (expanded.has(UNCLASSIFIED_ROW_KEY)) {
      for (const ticker of allocation.unclassified.tickers) {
        rows.push({
          key: unclassifiedTickerRowKey(ticker.ticker),
          kind: "unclassified_ticker",
          depth: 2,
          label: ticker.ticker,
          ticker: ticker.ticker,
          quantity: ticker.quantity,
          actual: ticker.actual,
          quoteDate: ticker.quote_date,
          stale: ticker.stale,
          expandable: false,
        });
      }
    }
  }

  return rows;
}

function classRow(cls: ClassAllocation, key: string, planned: boolean): AllocationRow {
  return {
    key,
    kind: "class",
    depth: 1,
    label: assetClassLabel(cls.asset_class),
    ...planning(planned, {
      target: cls.target,
      ideal: cls.ideal,
      deficit: cls.deficit,
      drift: cls.drift,
    }),
    actual: cls.actual,
    partial: cls.partial,
    expandable:
      cls.strategies.length > 0 ||
      cls.sub_classes.length > 0 ||
      cls.manual !== undefined ||
      cls.outside.tickers.length > 0,
  };
}

function strategyRow(strategy: StrategyAllocation, key: string, planned: boolean): AllocationRow {
  return {
    key,
    kind: "strategy",
    depth: 2,
    label: strategy.strategy,
    ...planning(planned, {
      target: strategy.target,
      ideal: strategy.ideal,
      deficit: strategy.deficit,
      drift: strategy.drift,
    }),
    actual: strategy.actual,
    partial: strategy.partial,
    expandable: strategy.tickers.length > 0,
  };
}

function subClassRow(subClass: SubClassAllocation, planned: boolean): AllocationRow {
  return {
    key: `subclass:${subClass.sub_class}`,
    kind: "sub_class",
    depth: 2,
    label: subClassLabel(subClass.sub_class),
    ...planning(planned, {
      target: subClass.target,
      ideal: subClass.ideal,
      deficit: subClass.deficit,
      drift: subClass.drift,
    }),
    actual: subClass.actual,
    expandable: false,
  };
}

function tickerRow(
  cls: ClassAllocation,
  strategy: StrategyAllocation,
  ticker: TickerAllocation,
  planned: boolean,
): AllocationRow {
  return {
    key: tickerRowKey(cls.asset_class, strategy.strategy, ticker.ticker),
    kind: "ticker",
    depth: 3,
    label: ticker.ticker,
    quantity: ticker.quantity,
    // Target and Ideal are planned even when the value is unknown; the deviation cells cannot be
    // derived from an unknown Actual, so they stay blank rather than reading as zero.
    ...(planned ? { target: ticker.target, ideal: ticker.ideal } : {}),
    ...(planned && ticker.actual !== undefined
      ? { deficit: ticker.deficit, drift: ticker.drift }
      : {}),
    actual: ticker.actual,
    quoteDate: ticker.quote_date,
    stale: ticker.stale,
    expandable: false,
  };
}

function manualRow(label: string, actual: number, key: string, expandable: boolean): AllocationRow {
  return {
    key,
    kind: "manual",
    depth: 2,
    label,
    actual,
    expandable,
  };
}

function outsideRow(actual: number, key: string): AllocationRow {
  return {
    key,
    kind: "outside",
    depth: 2,
    label: "Fora da estratégia",
    actual,
    expandable: true,
  };
}

function unclassifiedRow(actual: number): AllocationRow {
  return {
    key: UNCLASSIFIED_ROW_KEY,
    kind: "unclassified",
    depth: 1,
    label: "Sem classe",
    actual,
    expandable: true,
  };
}

function actualTickerRow(
  assetClass: AssetClass,
  ticker: ActualTickerAllocation,
  kind: "manual_ticker" | "outside_ticker",
): AllocationRow {
  return {
    key:
      kind === "manual_ticker"
        ? manualTickerRowKey(assetClass, ticker.ticker)
        : outsideTickerRowKey(assetClass, ticker.ticker),
    kind,
    depth: 3,
    label: ticker.ticker,
    assetClass,
    quantity: ticker.quantity,
    actual: ticker.actual,
    quoteDate: ticker.quote_date,
    stale: ticker.stale,
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
  return maxAbs(rows, (row) => row.deficit ?? 0);
}

export function maxAbsDrift(rows: AllocationRow[]): number {
  return maxAbs(rows, (row) => row.drift ?? 0);
}

// The hatched share of the reconciliation bar: the unattributed value over the listed book. Bucket
// units are attributed, so they never enter the numerator; RF is the consolidation engine's fruit
// and is never "unattributed", so it is left out of the denominator.
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
  return formatPercent(percentage, 1);
}

export function formatPoints(drift: number | undefined): string {
  if (drift === undefined) return "—";
  return `${drift > 0 ? "+" : ""}${ONE_DECIMAL.format(drift)} p.p.`;
}
