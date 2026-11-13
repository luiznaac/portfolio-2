import type {
  Allocation,
  AssetClass,
  ClassAllocation,
  Edition,
  EditionDiff,
  EditionEntry,
  EditionStatus,
  EditionSummary,
  ManualAllocation,
  MonthPerformance,
  PerformanceDecomposition,
  Rating,
  Strategy,
  StrategyAllocation,
  StrategyKind,
  StrategyPerformance,
} from "../api/types.ts";
import { distanceText, formatPoints, formatTarget } from "./allocation.ts";
import { formatCurrency, formatPercent, formatSignedPercent } from "./format.ts";
import { ASSET_CLASSES, MONTHS, assetClassLabel } from "./month.ts";
import { type Tone, deficitTone, driftTone, returnTone } from "./tones.ts";

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

// The strategy detail's window into the allocation tree: the class rung it sits in plus the report
// rung (a Strategy with targets) or the manual bucket rung. A rung the open month does not carry is
// absent, never a zero.
export interface StrategySlice {
  cls?: ClassAllocation;
  report?: StrategyAllocation;
  manual?: ManualAllocation;
  actual?: number;
  target?: number;
  ideal?: number;
  deficit?: number;
  drift?: number;
}

// The additive `strategy_id` on both rungs is the preferred join; a chain that names a Strategy the
// catalog no longer carries falls back to the name the tree still renders.
function matchesStrategy(
  rung: { strategy: string; strategy_id?: number },
  strategy: Strategy,
): boolean {
  return rung.strategy_id === undefined
    ? rung.strategy === strategy.name
    : rung.strategy_id === strategy.id;
}

export function strategySlice(strategy: Strategy, allocation?: Allocation): StrategySlice {
  const cls = allocation?.classes.find(
    (candidate) => candidate.asset_class === strategy.asset_class,
  );
  if (cls === undefined) return {};

  const report = cls.strategies.find((candidate) => matchesStrategy(candidate, strategy));
  if (report !== undefined) {
    return {
      cls,
      report,
      actual: report.actual,
      target: report.target,
      ideal: report.ideal,
      deficit: report.deficit,
      drift: report.drift,
    };
  }

  const manual =
    cls.manual !== undefined && matchesStrategy(cls.manual, strategy) ? cls.manual : undefined;
  return manual === undefined ? { cls } : { cls, manual, actual: manual.actual };
}

// One Carteira row: the report rung carries target/Ideal/Drift, the manual bucket only custody.
export interface StrategyPositionRow {
  ticker: string;
  quantity?: number;
  price?: number;
  value?: number;
  target?: number;
  ideal?: number;
  drift?: number;
  stale?: boolean;
  quoteDate?: string;
}

export function strategyPositionRows(
  strategy: Strategy,
  allocation?: Allocation,
): StrategyPositionRow[] {
  const slice = strategySlice(strategy, allocation);
  if (slice.report !== undefined) {
    return slice.report.tickers.map((ticker) => ({
      ticker: ticker.ticker,
      quantity: ticker.quantity,
      price: ticker.price,
      value: ticker.actual,
      target: ticker.target,
      ideal: ticker.ideal,
      drift: ticker.drift,
      stale: ticker.stale,
      quoteDate: ticker.quote_date,
    }));
  }
  if (slice.manual !== undefined) {
    return slice.manual.tickers.map((ticker) => ({
      ticker: ticker.ticker,
      quantity: ticker.quantity,
      price: ticker.price,
      value: ticker.actual,
      target: undefined,
      ideal: undefined,
      drift: undefined,
      stale: ticker.stale,
      quoteDate: ticker.quote_date,
    }));
  }
  return [];
}

// The Fatia tab's composition table: each slice's weight inside the strategy, with the total and a
// completeness flag. An unpriced ticker leaves the total partial, so the footer never claims a 100%
// it did not value.
export interface StrategyCompositionRow extends StrategyPositionRow {
  weight?: number;
}

export interface StrategyComposition {
  rows: StrategyCompositionRow[];
  total: number;
  complete: boolean;
}

export function strategyComposition(positions: StrategyPositionRow[]): StrategyComposition {
  const total = positions.reduce((sum, row) => sum + (row.value ?? 0), 0);
  const complete = positions.every((row) => row.value !== undefined);

  return {
    total,
    complete,
    rows: positions.map((row) => ({
      ...row,
      weight: row.value === undefined || total <= 0 ? undefined : (row.value / total) * 100,
    })),
  };
}

// The detail's KPI row: fatia, target, Ideal, Drift and the last close's return. A value the tree or
// the close does not carry renders "—" (or "sem alvo" in a target column), never a fabricated zero.
export interface StrategyKpi {
  key: "fatia" | "alvo" | "ideal" | "drift" | "return";
  label: string;
  value: string;
  hint: string;
  tone: Tone;
}

function shareOf(value: number, total: number | undefined): string {
  if (total === undefined || total <= 0) return "—";
  return formatPercent((value / total) * 100, 1);
}

export function strategyKpis(
  strategy: Strategy,
  allocation: Allocation | undefined,
  performance: MonthPerformance | undefined,
): StrategyKpi[] {
  const slice = strategySlice(strategy, allocation);
  const perf = findStrategyPerformance(performance, strategy.id);
  const portfolioShare =
    slice.actual === undefined || allocation === undefined || allocation.actual <= 0
      ? undefined
      : formatPercent((slice.actual / allocation.actual) * 100, 1);
  const returnValue = perf?.decomposition.return_percentage;

  return [
    {
      key: "fatia",
      label: "Fatia",
      value: slice.actual === undefined ? "—" : formatCurrency(slice.actual),
      hint: portfolioShare === undefined ? "—" : `${portfolioShare} da carteira`,
      tone: "default",
    },
    {
      key: "alvo",
      label: "Alvo",
      value: slice.target === undefined ? "sem alvo" : formatTarget(slice.target),
      hint:
        strategy.kind === "MANUAL"
          ? "bucket manual"
          : slice.cls === undefined
            ? "sem alvo na cadeia"
            : `da classe ${assetClassLabel(slice.cls.asset_class)}`,
      tone: "default",
    },
    {
      key: "ideal",
      label: "Ideal",
      value: slice.ideal === undefined ? "—" : formatCurrency(slice.ideal),
      hint: slice.deficit === undefined ? "não gera ordem" : distanceText(slice.deficit),
      tone: slice.deficit === undefined ? "muted" : deficitTone(slice.deficit),
    },
    {
      key: "drift",
      label: "Drift",
      value: slice.drift === undefined ? "—" : formatPoints(slice.drift),
      hint: "contra o alvo",
      tone: slice.drift === undefined ? "muted" : driftTone(slice.drift),
    },
    {
      key: "return",
      label:
        performance === undefined
          ? "Retorno"
          : `Retorno ${referenceMonthLabel(performance.period)}`,
      value: returnValue === undefined ? "—" : formatSignedPercent(returnValue),
      hint: "último fechamento",
      tone: returnTone(returnValue),
    },
  ];
}

export function findStrategyPerformance(
  performance: MonthPerformance | undefined,
  strategyId: number,
): StrategyPerformance | undefined {
  for (const cls of performance?.classes ?? []) {
    const match = (cls.strategies ?? []).find((strategy) => strategy.strategy_id === strategyId);
    if (match !== undefined) return match;
  }
  return undefined;
}

// The Fatia tab's reads: the slice's share of the portfolio and of its class, its target/drift, and
// the last close's return decomposition. It carries no edition state — the Carteira strip owns that.
export interface StrategyDecompositionView {
  valueStart: string;
  valueEnd: string;
  aportes: string;
  income: string;
  return: string;
  returnTone: Tone;
}

export interface StrategyFatiaView {
  portfolioShare: string;
  classShare: string;
  target: string;
  drift: string;
  driftTone: Tone;
  period?: string;
  decomposition?: StrategyDecompositionView;
}

function decompositionView(decomposition: PerformanceDecomposition): StrategyDecompositionView {
  return {
    valueStart:
      decomposition.value_start === undefined ? "—" : formatCurrency(decomposition.value_start),
    valueEnd: formatCurrency(decomposition.value_end),
    aportes: formatCurrency(decomposition.net_aportes),
    income: formatCurrency(decomposition.net_income),
    return:
      decomposition.return_percentage === undefined
        ? "—"
        : formatSignedPercent(decomposition.return_percentage),
    returnTone: returnTone(decomposition.return_percentage),
  };
}

export function strategyFatiaView(
  strategy: Strategy,
  allocation: Allocation | undefined,
  performance: MonthPerformance | undefined,
): StrategyFatiaView {
  const slice = strategySlice(strategy, allocation);
  const perf = findStrategyPerformance(performance, strategy.id);

  return {
    portfolioShare: slice.actual === undefined ? "—" : shareOf(slice.actual, allocation?.actual),
    classShare: slice.actual === undefined ? "—" : shareOf(slice.actual, slice.cls?.actual),
    target: slice.target === undefined ? "sem alvo" : formatTarget(slice.target),
    drift: slice.drift === undefined ? "—" : formatPoints(slice.drift),
    driftTone: slice.drift === undefined ? "muted" : driftTone(slice.drift),
    period: perf === undefined ? undefined : performance?.period,
    decomposition: perf === undefined ? undefined : decompositionView(perf.decomposition),
  };
}

// The newest edition in one lifecycle state. The strategy's current edition is its newest ACTIVE
// one: promoting a further report leaves the older active editions ACTIVE as history, so a bare
// status check would mark them all "ativa".
function newestEdition(editions: Edition[], status: EditionStatus): Edition | undefined {
  return [...editions]
    .filter((edition) => edition.status === status)
    .sort((left, right) => right.reference_month.localeCompare(left.reference_month))[0];
}

// The Carteira strip's edition: the newest staged report, if any. The active edition is not shown
// here — the list already carries it, and the strip only asks for a review decision.
export function stagedEdition(editions: Edition[]): Edition | undefined {
  return newestEdition(editions, "STAGED");
}

export function activeEdition(editions: Edition[]): Edition | undefined {
  return newestEdition(editions, "ACTIVE");
}

// The edition a report of `referenceMonth` is staged against, matching the backend's
// `fetchLatestActive(before = report.referenceMonth)`: the newest active edition before that month.
// For a normal forward report this is the current active edition; for an older month it is the one
// the promotion will actually diff against.
export function activeEditionBefore(
  editions: Edition[],
  referenceMonth: string,
): Edition | undefined {
  return [...editions]
    .filter((edition) => edition.status === "ACTIVE" && edition.reference_month < referenceMonth)
    .sort((left, right) => right.reference_month.localeCompare(left.reference_month))[0];
}

// The tone an edition chip carries; the timeline and the comparison list both draw from it.
export type EditionChipTone = "green" | "amber" | "neutral";

// One Edições timeline row: the edition, whether it is the current active one, and the chip the row
// carries — "ativa", still-staged "revisão", or a superseded "anterior".
export interface EditionTimelineRow {
  edition: Edition;
  active: boolean;
  statusLabel: string;
  tone: EditionChipTone;
}

export function editionTimeline(editions: Edition[]): EditionTimelineRow[] {
  const current = activeEdition(editions);
  return [...editions]
    .sort((left, right) => {
      const byMonth = right.reference_month.localeCompare(left.reference_month);
      return byMonth !== 0
        ? byMonth
        : Number(right.status === "STAGED") - Number(left.status === "STAGED");
    })
    .map((edition) => {
      const active = edition.id === current?.id;
      if (active) {
        return { edition, active, statusLabel: "ativa", tone: "green" };
      }
      return edition.status === "STAGED"
        ? { edition, active, statusLabel: "revisão", tone: "amber" }
        : { edition, active, statusLabel: "anterior", tone: "neutral" };
    });
}

// One row of the staged edition's full table. `change` is the "alterada" rule of the review: a row
// is changed only when its weight, rating or target price differs from the current active edition;
// a ticker the active edition does not carry is new, never a fabricated change.
export type EditionEntryChange = "new" | "changed" | "unchanged";

// The two decisions the Edições tab and the XP assistant share: promote the staged edition, or
// leave the active one untouched.
export type EditionDecision = "promoted" | "discarded";

export interface EditionReviewRow {
  ticker: string;
  weight: number;
  rating?: Rating;
  targetPrice?: number;
  change: EditionEntryChange;
}

function entryChanged(before: EditionEntry, after: EditionEntry): boolean {
  return (
    before.weight !== after.weight ||
    before.rating !== after.rating ||
    before.target_price !== after.target_price
  );
}

export function editionReviewRows(staged: Edition, active?: Edition): EditionReviewRow[] {
  return editionDeltaRows(staged, active).map((row) => ({
    ticker: row.ticker,
    weight: row.weight,
    rating: row.rating,
    targetPrice: row.targetPrice,
    change: row.change,
  }));
}

// One row of the assistant's edition diff: the staged value beside the active edition's, so weight,
// rating, target price and the "não operar" flag all read as a before → after move. `change` keeps
// the review rule (weight/rating/target price); the policy flip is its own alert, never a silent
// "alterada".
export interface EditionDeltaRow {
  ticker: string;
  weight: number;
  weightBefore?: number;
  rating?: Rating;
  ratingBefore?: Rating;
  targetPrice?: number;
  targetPriceBefore?: number;
  doNotTrade: boolean;
  doNotTradeBefore: boolean;
  change: EditionEntryChange;
  doNotTradeFlip: boolean;
}

export interface EditionDeltaSummary {
  weightChanges: number;
  ratingChanges: number;
  targetPriceChanges: number;
  alerts: number;
}

export function editionDeltaRows(staged: Edition, active?: Edition): EditionDeltaRow[] {
  const previous = new Map((active?.entries ?? []).map((entry) => [entry.ticker, entry]));

  return staged.entries.map((entry) => {
    const before = previous.get(entry.ticker);
    const change: EditionEntryChange =
      before === undefined ? "new" : entryChanged(before, entry) ? "changed" : "unchanged";
    return {
      ticker: entry.ticker,
      weight: entry.weight,
      weightBefore: before?.weight,
      rating: entry.rating,
      ratingBefore: before?.rating,
      targetPrice: entry.target_price,
      targetPriceBefore: before?.target_price,
      doNotTrade: entry.do_not_trade,
      doNotTradeBefore: before?.do_not_trade ?? false,
      change,
      doNotTradeFlip: before !== undefined && before.do_not_trade !== entry.do_not_trade,
    };
  });
}

// The diff's headline counts. A ticker the active edition does not carry is a new row, not a
// change, so it contributes to none of the move counts.
export function editionDeltaSummary(rows: EditionDeltaRow[]): EditionDeltaSummary {
  const moved = rows.filter((row) => row.change !== "new");
  return {
    weightChanges: moved.filter((row) => row.weightBefore !== row.weight).length,
    ratingChanges: moved.filter((row) => row.ratingBefore !== row.rating).length,
    targetPriceChanges: moved.filter((row) => row.targetPriceBefore !== row.targetPrice).length,
    alerts: rows.filter((row) => row.doNotTradeFlip).length,
  };
}

// A ticker the report dropped is an exit: the active edition holds it and the staged set does not.
// Promotion really removes it, so the diff names it instead of letting it vanish from the table.
export interface EditionExitedRow {
  ticker: string;
  weight: number;
  rating?: Rating;
  targetPrice?: number;
  doNotTrade: boolean;
}

export function editionExitedRows(staged: Edition, active?: Edition): EditionExitedRow[] {
  const current = new Set(staged.entries.map((entry) => entry.ticker));
  return (active?.entries ?? [])
    .filter((entry) => !current.has(entry.ticker))
    .map((entry) => ({
      ticker: entry.ticker,
      weight: entry.weight,
      rating: entry.rating,
      targetPrice: entry.target_price,
      doNotTrade: entry.do_not_trade,
    }));
}

export function isReportDriven(strategy: Strategy | null | undefined): boolean {
  return strategy?.kind === "REPORT_DRIVEN";
}

// The one rendering of a Strategy's kind: a report Strategy is "Com relatório", the legacy bucket
// is "Manual (legado)". Shared by the comparison list and the frozen clips so the labels cannot drift.
export function strategyKindLabel(kind: StrategyKind): string {
  return kind === "MANUAL" ? "Manual (legado)" : "Com relatório";
}

export function reportDrivenStrategies(strategies: Strategy[]): Strategy[] {
  return strategies.filter(isReportDriven);
}

export interface EditionStatusChip {
  label: string;
  tone: EditionChipTone;
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
