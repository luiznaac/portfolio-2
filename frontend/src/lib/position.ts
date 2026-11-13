import type { Allocation, AssetClass, Gap, Position, Quote } from "../api/types.ts";
import { heldTickerLeaves } from "./allocation.ts";
import { openGaps } from "./gaps.ts";

// The "current" quote is the latest stored EOD close — the quote table is keyed on (ticker, date)
// and COTAHIST is authoritative. This is deliberately the max date, not the array's last
// element: the API's ordering is not part of the contract's promise.
export function latestQuote(quotes: readonly Quote[]): Quote | undefined {
  return quotes.reduce<Quote | undefined>(
    (latest, candidate) => (!latest || candidate.date > latest.date ? candidate : latest),
    undefined,
  );
}

// The market value of a position: units held × the current close.
export function positionValue(quantity: number, close: number): number {
  return quantity * close;
}

// The Ativos · Posições row (#378): the stored custody joined to the allocation leaf that carries the
// class, the close and the staleness; the value is the whole custody × that close, never one
// attribution slice's share. The allocation read is the one source for the close and staleness, so
// the table, the Alocação tree and the data-quality panel cannot disagree about a ticker.
export interface PositionRow {
  ticker: string;
  quantity: number;
  averagePrice: number;
  assetClass: AssetClass | null;
  close?: number;
  closeDate?: string;
  // Undefined on an unpriced leaf: unknown, never R$ 0,00.
  value?: number;
  stale: boolean;
  gaps: number;
}

function openGapCounts(gaps: readonly Gap[]): Map<string, number> {
  const counts = new Map<string, number>();
  for (const gap of openGaps(gaps)) {
    counts.set(gap.ticker, (counts.get(gap.ticker) ?? 0) + 1);
  }
  return counts;
}

export function positionRows(
  positions: readonly Position[],
  allocation: Allocation,
  gaps: readonly Gap[] = [],
): PositionRow[] {
  const leaves = new Map(heldTickerLeaves(allocation).map((leaf) => [leaf.ticker, leaf]));
  const gapCounts = openGapCounts(gaps);

  return positions
    .map((position) => {
      const leaf = leaves.get(position.ticker);
      const close = leaf?.close;
      return {
        ticker: position.ticker,
        quantity: position.quantity,
        averagePrice: position.average_price,
        assetClass: leaf?.assetClass ?? null,
        close,
        closeDate: leaf?.closeDate,
        // The whole custody is valued, not one attribution slice's share.
        value: close === undefined ? undefined : positionValue(position.quantity, close),
        stale: leaf?.stale ?? false,
        gaps: gapCounts.get(position.ticker) ?? 0,
      };
    })
    .sort((left, right) => left.ticker.localeCompare(right.ticker));
}

export interface PositionsTotal {
  // Undefined when none of the rows is priced — distinct from a real zero.
  value: number | undefined;
  // True while at least one row carries an unknown value, so the total covers only the priced part.
  partial: boolean;
}

export function positionsTotal(rows: readonly PositionRow[]): PositionsTotal {
  let value = 0;
  let priced = 0;
  let unpriced = 0;
  for (const row of rows) {
    if (row.value === undefined) {
      unpriced += 1;
    } else {
      value += row.value;
      priced += 1;
    }
  }
  return {
    value: priced === 0 ? (rows.length === 0 ? 0 : undefined) : value,
    partial: unpriced > 0,
  };
}
