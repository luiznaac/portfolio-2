import type { ImportRow, Position } from "../api/types.ts";

// Pure custody divergence helpers — no React imports here (see AGENTS.md). A custody upload is
// state-like: the snapshot's quantity is the truth for its ticker and the stored ledger position is
// what the book currently holds. A difference is stated, never a blocker: applying opens a GAP in
// Ativos · Lacunas, so reconciliation stays a separate act.

// A missing buy (custody above the ledger) enters the position replay at the market close; a missing
// sale (custody below the ledger) carries proceeds the owner has not informed, so its realized
// result stays pending.
export type CustodyDivergenceKind = "aligned" | "missing_buy" | "missing_sale";

export interface CustodyDivergence {
  ticker: string;
  custodyQuantity: number;
  ledgerQuantity: number;
  // Custody minus ledger: positive is a missing buy, negative a missing sale.
  delta: number;
  kind: CustodyDivergenceKind;
  pending: boolean;
}

export interface CustodyDivergenceSummary {
  total: number;
  divergent: number;
  missingBuys: number;
  missingSales: number;
}

// The stored columns are decimal(18,6), so the snapshot arithmetic is rounded to the same scale
// before comparing — a repeated fractional line must not read as a phantom divergence.
const QUANTITY_SCALE = 1_000_000;

function roundQuantity(quantity: number): number {
  return Math.round(quantity * QUANTITY_SCALE) / QUANTITY_SCALE;
}

// The union of both sides, so a ticker the snapshot drops is a divergence too (a full missing
// sale) rather than a row that silently vanishes. A flagged row is not part of the snapshot: apply
// rejects it, so it must not skew the list.
export function custodyDivergences(
  rows: readonly ImportRow[],
  positions: readonly Position[],
): CustodyDivergence[] {
  const custodyByTicker = new Map<string, number>();
  for (const row of rows) {
    if (row.error !== undefined) continue;
    custodyByTicker.set(row.ticker, (custodyByTicker.get(row.ticker) ?? 0) + row.quantity);
  }
  const ledgerByTicker = new Map(positions.map((position) => [position.ticker, position.quantity]));

  return [...new Set([...custodyByTicker.keys(), ...ledgerByTicker.keys()])]
    .sort((left, right) => left.localeCompare(right))
    .map((ticker) => {
      const custodyQuantity = roundQuantity(custodyByTicker.get(ticker) ?? 0);
      const ledgerQuantity = roundQuantity(ledgerByTicker.get(ticker) ?? 0);
      const delta = roundQuantity(custodyQuantity - ledgerQuantity);
      const kind: CustodyDivergenceKind =
        delta > 0 ? "missing_buy" : delta < 0 ? "missing_sale" : "aligned";
      return {
        ticker,
        custodyQuantity,
        ledgerQuantity,
        delta,
        kind,
        pending: kind === "missing_sale",
      };
    });
}

export function custodyDivergenceSummary(
  divergences: readonly CustodyDivergence[],
): CustodyDivergenceSummary {
  return {
    total: divergences.length,
    divergent: divergences.filter((divergence) => divergence.kind !== "aligned").length,
    missingBuys: divergences.filter((divergence) => divergence.kind === "missing_buy").length,
    missingSales: divergences.filter((divergence) => divergence.kind === "missing_sale").length,
  };
}
