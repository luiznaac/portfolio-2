import type { AttributionSlice } from "../api/types.ts";

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
