// Pure month + targets form helpers — no React imports here (see AGENTS.md). The month-scoped
// screens map their forms to the backend payloads only through this module, so the parsing rules
// and the pt-BR labels are unit-tested here rather than inside the page.

import type {
  AssetClass,
  FixedIncomeSubClass,
  Month,
  MonthCreation,
  MonthStatus,
  MonthUpdate,
  Targets,
} from "../api/types.ts";

export interface MonthFormState {
  period: string;
  capital: string;
  floor: string;
}

// The opener seeds the period with the given one (the current month) so both selects start set.
export function emptyMonthForm(period = ""): MonthFormState {
  return { period, capital: "", floor: "" };
}

// Seeds the edit form from the open month; the period is editable only at open time but kept so the
// form is one shape.
export function monthFormFrom(month: Month): MonthFormState {
  return { period: month.period, capital: String(month.capital), floor: String(month.floor) };
}

// YYYY-MM with a real month — the ISO form the backend's YearMonth parses.
const PERIOD = /^(\d{4})-(0[1-9]|1[0-2])$/;

// An amount field is valid when blank-as-zero or a finite non-negative number.
function parseAmount(value: string): number | null {
  const trimmed = value.trim();
  if (trimmed.length === 0) return null;
  const parsed = Number(trimmed);
  return Number.isFinite(parsed) ? parsed : null;
}

function parseFloor(value: string): number | null {
  if (value.trim().length === 0) return 0;
  const parsed = parseAmount(value);
  return parsed === null || parsed < 0 ? null : parsed;
}

// The Capital + Floor half every month form shares: a non-negative Capital and a Floor that is
// blank-as-zero. Null while incomplete, so the caller keeps submit disabled.
function parseCapitalFloor(form: MonthFormState): { capital: number; floor: number } | null {
  const capital = parseAmount(form.capital);
  if (capital === null || capital < 0) return null;

  const floor = parseFloor(form.floor);
  if (floor === null) return null;

  return { capital, floor };
}

// Builds the open payload, or null while the form is incomplete — the page keeps submit disabled on
// null and never opens half a month.
export function toMonthCreation(form: MonthFormState): MonthCreation | null {
  if (!PERIOD.test(form.period)) return null;

  const amounts = parseCapitalFloor(form);
  return amounts === null ? null : { period: form.period, ...amounts };
}

export function toMonthUpdate(form: MonthFormState): MonthUpdate | null {
  return parseCapitalFloor(form);
}

export const MONTHS = [
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

// The opener's period is two pickable selects, never a native `<input type="month">` (Firefox and
// Safari render that as a plain text box with no picker). A blank period has no parts.
export const MONTH_OPTIONS = MONTHS.map((label, index) => ({
  value: String(index + 1).padStart(2, "0"),
  label,
}));

export function periodYear(period: string): string {
  return PERIOD.exec(period)?.[1] ?? "";
}

export function periodMonth(period: string): string {
  return PERIOD.exec(period)?.[2] ?? "";
}

// The years around today, so the default month is always pickable; a selected year outside that
// window is kept rather than dropped from the select.
export function periodYearOptions(period: string, today: string, span = 2): string[] {
  const anchor = Number(today.slice(0, 4));
  const selected = periodYear(period);
  const years = new Set<string>();
  for (let offset = -span; offset <= span; offset += 1) years.add(String(anchor + offset));
  if (selected.length > 0) years.add(selected);
  return [...years].sort();
}

export function monthPeriodLabel(period: string): string {
  const match = /^(\d{4})-(\d{2})$/.exec(period);
  if (!match) return period;

  const [, year, month] = match;
  const label = MONTHS[Number(month) - 1];
  return label === undefined ? period : `${label}/${year}`;
}

export function monthStatusLabel(status: MonthStatus): string {
  return status === "OPEN" ? "Aberto" : "Fechado";
}

// The backend lists closed months newest first; the newest one is what the history screens open on.
export function defaultClosedMonthId(months: Month[]): number | null {
  return months.length > 0 ? months[0].id : null;
}

const ASSET_CLASS_LABELS: Record<AssetClass, string> = {
  ACOES: "Ações",
  FIIS: "Fundos imob.",
  RENDA_FIXA: "Renda fixa",
};

export function assetClassLabel(assetClass: AssetClass): string {
  return ASSET_CLASS_LABELS[assetClass];
}

// The chain's fixed rungs, in the order the entry form shows them.
export const ASSET_CLASSES: AssetClass[] = ["ACOES", "FIIS", "RENDA_FIXA"];
export const RF_SUB_CLASSES: FixedIncomeSubClass[] = [
  "FIXED_RATE",
  "FLOATING_RATE",
  "INFLATION_LINKED",
];

export interface ClassTargetRow {
  assetClass: AssetClass;
  percentage: string;
}

export interface StrategyTargetRow {
  id: string;
  assetClass: AssetClass;
  strategy: string;
  percentage: string;
}

export interface SubClassTargetRow {
  subClass: FixedIncomeSubClass;
  percentage: string;
}

export interface TickerTargetRow {
  id: string;
  strategy: string;
  ticker: string;
  percentage: string;
}

// Every percentage is a string while it is being typed; classes and sub-classes are fixed rows (one
// per rung), while strategies and tickers are added by the user.
export interface TargetsFormState {
  classes: ClassTargetRow[];
  strategies: StrategyTargetRow[];
  subClasses: SubClassTargetRow[];
  tickers: TickerTargetRow[];
}

export function emptyTargetsForm(): TargetsFormState {
  return {
    classes: ASSET_CLASSES.map((assetClass) => ({ assetClass, percentage: "" })),
    strategies: [],
    subClasses: RF_SUB_CLASSES.map((subClass) => ({ subClass, percentage: "" })),
    tickers: [],
  };
}

// Seeds the form from stored targets: the fixed rungs always show (blank when the month has no
// value), the variable rungs carry their stored rows.
export function targetsFormFrom(targets: Targets): TargetsFormState {
  const classByKey = new Map(
    targets.classes.map((target) => [target.asset_class, target.percentage]),
  );
  const subClassByKey = new Map(
    targets.sub_classes.map((target) => [target.sub_class, target.percentage]),
  );

  return {
    classes: ASSET_CLASSES.map((assetClass) => ({
      assetClass,
      percentage: percentageString(classByKey.get(assetClass)),
    })),
    strategies: targets.strategies.map((target) => ({
      id: newRowId(),
      assetClass: target.asset_class,
      strategy: target.strategy,
      percentage: String(target.percentage),
    })),
    subClasses: RF_SUB_CLASSES.map((subClass) => ({
      subClass,
      percentage: percentageString(subClassByKey.get(subClass)),
    })),
    tickers: targets.tickers.map((target) => ({
      id: newRowId(),
      strategy: target.strategy,
      ticker: target.ticker,
      percentage: String(target.percentage),
    })),
  };
}

function percentageString(value: number | undefined): string {
  return value === undefined ? "" : String(value);
}

// A stable identity for a variable row, so the page can key its inputs by identity rather than by an
// editable value — a value-derived key remounts the input on every keystroke and drops focus.
function newRowId(): string {
  return crypto.randomUUID();
}

// Maps the form to the payload the replace-all PUT takes: a row is kept only when it carries a
// finite percentage (and, for the named rungs, a name); a ticker is normalised to its canonical key.
export function toTargets(form: TargetsFormState): Targets {
  return {
    classes: form.classes
      .filter(hasPercentage)
      .map((row) => ({ asset_class: row.assetClass, percentage: Number(row.percentage) })),
    strategies: form.strategies
      .filter((row) => row.strategy.trim().length > 0 && hasPercentage(row))
      .map((row) => ({
        asset_class: row.assetClass,
        strategy: row.strategy.trim(),
        percentage: Number(row.percentage),
      })),
    sub_classes: form.subClasses
      .filter(hasPercentage)
      .map((row) => ({ sub_class: row.subClass, percentage: Number(row.percentage) })),
    tickers: form.tickers
      .filter((row) => row.ticker.trim().length > 0 && hasPercentage(row))
      .map((row) => ({
        strategy: row.strategy.trim(),
        ticker: row.ticker.trim().toUpperCase(),
        percentage: Number(row.percentage),
      })),
  };
}

function hasPercentage(row: { percentage: string }): boolean {
  const trimmed = row.percentage.trim();
  return trimmed.length > 0 && Number.isFinite(Number(trimmed));
}

// Strategy Targets are shares of their class, so the whole class is the ceiling. The server rejects
// a class above it; the form mirrors the same rule.
export const STRATEGY_TARGET_LIMIT = 100;

// The per-class sum of the Strategy Targets the form would submit. Nameless and blank rows are
// ignored exactly as toTargets drops them, so the sum validated is the sum the server receives.
export function strategyTargetSums(form: TargetsFormState): Partial<Record<AssetClass, number>> {
  const sums: Partial<Record<AssetClass, number>> = {};
  for (const row of form.strategies) {
    if (row.strategy.trim().length === 0 || !hasPercentage(row)) continue;
    sums[row.assetClass] = (sums[row.assetClass] ?? 0) + Number(row.percentage);
  }
  return sums;
}

// Binary floating-point can put a hand-typed 100 a hair above it (33,33 + 33,33 + 33,34); the
// server sums exact decimals, so the form tolerates that noise but not a real over-allocation.
const SHARE_EPSILON = 1e-9;

// The classes whose report shares over-plan the class — the rule the targets write path enforces,
// surfaced inline so the form never sends a chain the server will reject.
export function overplannedStrategyClasses(form: TargetsFormState): AssetClass[] {
  const sums = strategyTargetSums(form);
  return ASSET_CLASSES.filter(
    (assetClass) => (sums[assetClass] ?? 0) > STRATEGY_TARGET_LIMIT + SHARE_EPSILON,
  );
}

export interface RemainingLegacyShare {
  assetClass: AssetClass;
  percentage: number;
}

// The read-only "restante" share of a class that has a Manual Strategy (bucket): the part no report
// Strategy targets. It is derived, never stored — the bucket carries no Target row. A class without a
// bucket has no line, and an over-planned class clamps to zero while the form shows the error.
// Renda Fixa has no Strategy rung at all (the server refuses a Strategy in that class), so it can
// never carry a bucket.
export function remainingLegacyShares(
  form: TargetsFormState,
  manualClasses: ReadonlySet<AssetClass>,
): RemainingLegacyShare[] {
  const sums = strategyTargetSums(form);
  return ASSET_CLASSES.filter(
    (assetClass) => assetClass !== "RENDA_FIXA" && manualClasses.has(assetClass),
  ).map((assetClass) => ({
    assetClass,
    percentage: Math.max(0, STRATEGY_TARGET_LIMIT - (sums[assetClass] ?? 0)),
  }));
}

// Row add/remove are the entry form's state transitions, kept pure so the page stays a thin
// renderer and the behaviour is covered here.
export function addStrategyRow(form: TargetsFormState): TargetsFormState {
  return {
    ...form,
    strategies: [
      ...form.strategies,
      { id: newRowId(), assetClass: "ACOES", strategy: "", percentage: "" },
    ],
  };
}

export function removeStrategyRow(form: TargetsFormState, index: number): TargetsFormState {
  return { ...form, strategies: form.strategies.filter((_, position) => position !== index) };
}

export function addTickerRow(form: TargetsFormState, strategy: string): TargetsFormState {
  return {
    ...form,
    tickers: [...form.tickers, { id: newRowId(), strategy, ticker: "", percentage: "" }],
  };
}

export function removeTickerRow(form: TargetsFormState, index: number): TargetsFormState {
  return { ...form, tickers: form.tickers.filter((_, position) => position !== index) };
}
