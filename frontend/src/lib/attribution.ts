import type { AssetClass, AttributionSlice, Edition, OrderPlan, Strategy } from "../api/types.ts";

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
