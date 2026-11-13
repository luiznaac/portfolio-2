// Pure Lacunas helpers — no React imports here (see AGENTS.md). A GAP is a custody delta the ledger
// cannot explain: an upward one is a missing buy priced at the market close, a downward one a
// missing sale whose proceeds (and therefore realized result) stay pending until replaced.

import type { Gap, GapStatus } from "../api/types.ts";

export function openGaps(gaps: readonly Gap[]): Gap[] {
  return gaps.filter((gap) => gap.status === "OPEN");
}

// A missing sale serialises `realized_pending`; a missing buy does not. That flag is the variant
// discriminator the wire shape carries, so the UI never guesses from a nullable price.
export function isMissingBuy(gap: Gap): boolean {
  return gap.realized_pending === undefined;
}

// The one pending-proceeds predicate: a missing sale whose proceeds have not been informed yet.
export function isPendingSale(gap: Gap): boolean {
  return gap.realized_pending === true;
}

export function pendingProceedsGaps(gaps: readonly Gap[]): Gap[] {
  return openGaps(gaps).filter(isPendingSale);
}

// The page's row: the gap's own fields flattened, plus the two variant flags the renderer names.
export interface GapRow {
  id: number;
  ticker: string;
  date: string;
  quantity: number;
  unitPrice?: number;
  status: GapStatus;
  missingBuy: boolean;
  pending: boolean;
}

// Open gaps first — they still need an action — then newest first inside each status.
export function gapRows(gaps: readonly Gap[]): GapRow[] {
  return [...gaps]
    .sort((left, right) => {
      if (left.status !== right.status) return left.status === "OPEN" ? -1 : 1;
      if (left.date !== right.date) return left.date < right.date ? 1 : -1;
      return right.id - left.id;
    })
    .map((gap) => ({
      id: gap.id,
      ticker: gap.ticker,
      date: gap.date,
      quantity: gap.quantity,
      unitPrice: gap.unit_price,
      status: gap.status,
      missingBuy: isMissingBuy(gap),
      pending: isPendingSale(gap),
    }));
}
