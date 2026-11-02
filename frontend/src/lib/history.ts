// Pure Histórico helpers — no React imports here (see AGENTS.md). The adoption defaults, the GAP
// reading and the badge arithmetic are unit-tested without a browser; the page only renders them.

import type { Gap, HeldTicker, Quote, Trade } from "../api/types.ts";
import { addDays, todayIso } from "./dates.ts";

export interface AdoptionRow {
  ticker: string;
  quantity: number;
  date: string;
  unitPrice: number;
}

// The pre-window custody the export reveals as negative: the missing quantity is what the seed must
// buy back, so the default is the absolute value of the negative position.
export function adoptionRows(negative: HeldTicker[]): AdoptionRow[] {
  return negative.map((position) => ({
    ticker: position.ticker,
    quantity: Math.abs(position.quantity),
    date: "",
    unitPrice: 0,
  }));
}

// The adoption date defaults to the day before the earliest imported trade: the seed is the custody
// that existed before the export window opens. An OPENING seed is not history and an unconfirmed
// manual trade is not B3-confirmed, so neither can set the boundary.
export function adoptionDefaultDate(trades: Trade[], today: string = todayIso()): string {
  const importedDates = trades
    .filter((trade) => trade.source === "B3_IMPORT" || trade.confirmed_by_b3)
    .map((trade) => trade.date)
    .sort();

  return importedDates.length === 0 ? today : addDays(importedDates[0], -1);
}

export function latestClose(quotes: Quote[]): number | null {
  if (quotes.length === 0) return null;
  return quotes.reduce((latest, quote) => (quote.date > latest.date ? quote : latest)).close;
}

export function isValidAdoptionRow(row: AdoptionRow): boolean {
  return (
    row.ticker.trim().length > 0 &&
    Number.isFinite(row.quantity) &&
    row.quantity > 0 &&
    isIsoDate(row.date) &&
    Number.isFinite(row.unitPrice) &&
    row.unitPrice >= 0
  );
}

// A real calendar day, not only the right shape: `2026-13-09` matches the pattern but is not a date.
function isIsoDate(value: string): boolean {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (!match) return false;
  const [, year, month, day] = match;
  const date = new Date(Date.UTC(Number(year), Number(month) - 1, Number(day)));
  return (
    date.getUTCFullYear() === Number(year) &&
    date.getUTCMonth() === Number(month) - 1 &&
    date.getUTCDate() === Number(day)
  );
}

export function openGaps(gaps: Gap[]): Gap[] {
  return gaps.filter((gap) => gap.status === "OPEN");
}

// A missing sale serialises `realized_pending`; a missing buy does not. That flag is the variant
// discriminator the wire shape carries, so the UI never guesses from a nullable price.
export function isMissingBuy(gap: Gap): boolean {
  return gap.realized_pending === undefined;
}

export function pendingProceedsGaps(gaps: Gap[]): Gap[] {
  return openGaps(gaps).filter((gap) => gap.realized_pending === true);
}

export function historyAttentionCount(negativeCount: number, gaps: Gap[]): number {
  return negativeCount + openGaps(gaps).length;
}
