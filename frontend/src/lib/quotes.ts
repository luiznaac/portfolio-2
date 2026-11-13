// Pure Cotações helpers — no React imports here (see AGENTS.md). The page lists the last close per
// held ticker; the latest-close rule is position.ts's, so the table and the ticker drawer agree.

import type { Quote, QuoteSource, StaleQuote } from "../api/types.ts";
import { latestQuote } from "./position.ts";

export interface QuoteRow {
  ticker: string;
  date?: string;
  close?: number;
  source?: QuoteSource;
  // Present only when the status aggregate measured the close as stale: the backend's five-day rule
  // against the as-of it used, so the page never re-derives freshness from a browser clock.
  staleDays?: number;
  // The per-ticker quote read failed; distinct from a ticker with no stored close.
  failed: boolean;
}

// One row per ticker, in ticker order. A ticker with no stored close stays unknown ("—"), never
// R$ 0,00.
export function quoteRows(
  tickers: readonly string[],
  quotesByTicker: ReadonlyMap<string, readonly Quote[]>,
  staleQuotes: readonly StaleQuote[] = [],
  failedTickers: ReadonlySet<string> = new Set(),
): QuoteRow[] {
  const staleDays = new Map(staleQuotes.map((quote) => [quote.ticker, quote.days_old]));

  return [...new Set(tickers)]
    .sort((left, right) => left.localeCompare(right))
    .map((ticker) => {
      const latest = latestQuote(quotesByTicker.get(ticker) ?? []);
      return {
        ticker,
        date: latest?.date,
        close: latest?.close,
        source: latest?.source,
        staleDays: staleDays.get(ticker),
        failed: failedTickers.has(ticker),
      };
    });
}
