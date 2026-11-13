import type {
  AssetClass,
  Attribution,
  AttributionSlice,
  Edition,
  OrderPlan,
  Strategy,
} from "../api/types.ts";
import { formatNumber } from "./format.ts";

// The attribution ledger's arithmetic — pure helpers so the Ativos detail's bar and form do not
// re-derive the same sums in JSX. The backend already returns the authoritative totals; these are
// for the bar widths and for sizing the form while the user types.

export function attributionPercent(quantity: number, custodyQuantity: number): number {
  return custodyQuantity > 0 ? (quantity / custodyQuantity) * 100 : 0;
}

// How much a slice may hold without breaking Σ attributions ≤ custody. Replacing a slice excludes
// that slice's current quantity from the others, since the write overwrites it.
export function remainingAttributable(
  custodyQuantity: number,
  slices: AttributionSlice[],
  replacingStrategyId?: number,
): number {
  const others = slices
    .filter((slice) => slice.strategy_id !== replacingStrategyId)
    .reduce((total, slice) => total + slice.quantity, 0);
  return Math.max(0, custodyQuantity - others);
}

// One destination the manual transfer modal may offer: a report-driven Strategy of the ticker's
// class whose current Edition weighs the ticker. `shortfall` is the receiver's whole-unit gap from
// the order plan; absent when the plan does not name the slice (no open month or no Target).
export interface TransferDestination {
  strategyId: number;
  strategyName: string;
  shortfall?: number;
}

// The destination rule from the ticket: report-driven, same class, and the latest active Edition
// gives the ticker a weight above zero. A Strategy with no active Edition contributes nothing.
export function transferDestinations(
  strategies: readonly Strategy[],
  editionsByStrategyId: ReadonlyMap<number, readonly Edition[]>,
  assetClass: AssetClass,
  ticker: string,
  plan: OrderPlan | null | undefined,
): TransferDestination[] {
  return strategies
    .filter((strategy) => strategy.kind === "REPORT_DRIVEN" && strategy.asset_class === assetClass)
    .filter((strategy) => activeEditionWeighs(editionsByStrategyId.get(strategy.id), ticker))
    .map((strategy) => ({
      strategyId: strategy.id,
      strategyName: strategy.name,
      shortfall: shortfallFor(plan, assetClass, strategy.name, ticker),
    }))
    .sort((left, right) => left.strategyName.localeCompare(right.strategyName));
}

// The receiver's whole-unit shortfall for one (strategy, ticker) slice, floored at zero: a surplus
// is not a shortfall the transfer should preselect.
function shortfallFor(
  plan: OrderPlan | null | undefined,
  assetClass: AssetClass,
  strategyName: string,
  ticker: string,
): number | undefined {
  const cls = plan?.classes.find((candidate) => candidate.asset_class === assetClass);
  if (!cls || !("strategies" in cls)) return undefined;
  const leaf = cls.strategies
    .find((strategy) => strategy.strategy === strategyName)
    ?.tickers.find((candidate) => candidate.ticker === ticker);
  if (!leaf || leaf.target_quantity === undefined) return undefined;
  return Math.max(0, Math.floor(leaf.target_quantity - leaf.attributed_quantity));
}

// The transfer defaults to the receiver's whole-unit shortfall, capped by the bucket slice. When no
// shortfall is known (no open month or no Target for the slice) there is nothing to preselect, so
// the field starts empty and the user types the amount.
export function defaultTransferQuantity(
  destination: TransferDestination,
  bucketQuantity: number,
): number {
  if (destination.shortfall === undefined) return 0;
  return Math.min(Math.max(0, destination.shortfall), Math.max(0, bucketQuantity));
}

// The quantity the server accepts: positive and no larger than the bucket slice it moves.
export function isValidTransferQuantity(quantity: number, bucketQuantity: number): boolean {
  return Number.isFinite(quantity) && quantity > 0 && quantity <= bucketQuantity;
}

function activeEditionWeighs(editions: readonly Edition[] | undefined, ticker: string): boolean {
  const active = (editions ?? [])
    .filter((edition) => edition.status === "ACTIVE")
    .sort((left, right) => right.reference_month.localeCompare(left.reference_month))[0];
  return active?.entries.some((entry) => entry.ticker === ticker && entry.weight > 0) ?? false;
}

// The strategy detail's Atribuição tab: custody against the ledger attribution for the tickers the
// strategy holds. A row reconciles when the ledger explains the whole custody — whether that custody
// sits in this strategy, in another one, or split between them. A row diverges when the ledger leaves
// custody unattributed (an attribution gap) or attributes more than custody holds; the divergence is
// carried in words, never only in a tone.
interface StrategyAttributionOther {
  strategyName: string;
  quantity: number;
}

interface StrategyAttributionBase {
  ticker: string;
  custody: number;
  own: number;
  // Custody outside this strategy's slice: another strategy's units plus the unattributed remainder.
  outsideSlice: number;
  others: StrategyAttributionOther[];
}

export type StrategyAttributionRow =
  | (StrategyAttributionBase & { state: "reconciled"; tone: "green"; statement?: undefined })
  | (StrategyAttributionBase & { state: "gap" | "over"; tone: "amber"; statement: string });

export interface StrategyAttributionView {
  rows: StrategyAttributionRow[];
  finding: string;
}

// Quantities are decimal share counts, so a reconciliation compares them with a tolerance rather
// than demanding bit-identical floats.
const QUANTITY_EPSILON = 1e-9;

function quantityLabel(quantity: number): string {
  return `${formatNumber(quantity)} ${quantity === 1 ? "cota" : "cotas"}`;
}

function attributionRow(strategyId: number, attribution: Attribution): StrategyAttributionRow {
  const own = attribution.slices.find((slice) => slice.strategy_id === strategyId)?.quantity ?? 0;
  const others = attribution.slices
    .filter((slice) => slice.strategy_id !== strategyId)
    .map((slice) => ({ strategyName: slice.strategy_name, quantity: slice.quantity }))
    .sort((left, right) => left.strategyName.localeCompare(right.strategyName));
  const custody = attribution.custody_quantity;
  const excess = attribution.attributed_quantity - custody;
  const gap = custody - attribution.attributed_quantity;
  const base = {
    ticker: attribution.ticker,
    custody,
    own,
    outsideSlice: custody - own,
    others,
  };

  if (excess > QUANTITY_EPSILON) {
    return {
      ...base,
      state: "over",
      tone: "amber",
      statement: `atribuição ${quantityLabel(excess)} acima da custódia`,
    };
  }
  if (gap > QUANTITY_EPSILON) {
    return {
      ...base,
      state: "gap",
      tone: "amber",
      statement: `${quantityLabel(gap)} fora de toda estratégia`,
    };
  }
  return { ...base, state: "reconciled", tone: "green", statement: undefined };
}

// The strategy's own tickers — its chain targets plus the ledger slices it holds — arrive as their
// resolved attribution reads, so a target it holds nothing of still shows its custody under another
// strategy. A ticker with no read (no held position) has nothing to reconcile and is absent.
export function strategyAttributionView(
  strategyId: number,
  attributions: readonly Attribution[],
): StrategyAttributionView {
  const rows = attributions
    .map((attribution) => attributionRow(strategyId, attribution))
    .sort((left, right) => left.ticker.localeCompare(right.ticker));
  const divergentRows = rows.filter(
    (row): row is Extract<StrategyAttributionRow, { statement: string }> =>
      row.state !== "reconciled",
  );

  let finding: string;
  if (rows.length === 0) {
    finding = "Nenhum ativo atribuído a esta estratégia.";
  } else if (divergentRows.length === 0) {
    finding = "Custódia e atribuição conferem em todos os tickers.";
  } else {
    const verb = divergentRows.length === 1 ? "ticker diverge" : "tickers divergem";
    const detail = divergentRows.map((row) => `${row.ticker} (${row.statement})`).join("; ");
    finding = `${divergentRows.length} de ${rows.length} ${verb} da custódia · ${detail}`;
  }

  return { rows, finding };
}
