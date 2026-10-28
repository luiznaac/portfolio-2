import type { Quote } from "../api/types.ts";

// The "current" quote is the latest stored EOD close — the quote table is keyed on (ticker, date)
// and COTAHIST is authoritative. This is deliberately the max date, not the array's last
// element: the API's ordering is not part of the contract's promise.
export function latestQuote(quotes: Quote[]): Quote | undefined {
  return quotes.reduce<Quote | undefined>(
    (latest, candidate) => (!latest || candidate.date > latest.date ? candidate : latest),
    undefined,
  );
}

// The market value of a position: units held × the current close.
export function positionValue(quantity: number, close: number): number {
  return quantity * close;
}
