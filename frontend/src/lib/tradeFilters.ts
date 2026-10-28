import type { Trade, TradeSide } from "../api/types.ts";

export interface TradeFilters {
  ticker: string;
  from: string;
  to: string;
  side: TradeSide | "";
}

export const emptyTradeFilters: TradeFilters = { ticker: "", from: "", to: "", side: "" };

// Pure filter over the ledger for the Ativos list. Dates are ISO `YYYY-MM-DD`, so a lexicographic
// comparison is the date order; `from`/`to` are inclusive bounds.
export function filterTrades(trades: Trade[], filters: TradeFilters): Trade[] {
  const ticker = filters.ticker.trim().toUpperCase();

  return trades.filter((trade) => {
    if (ticker && !trade.ticker.toUpperCase().includes(ticker)) return false;
    if (filters.from && trade.date < filters.from) return false;
    if (filters.to && trade.date > filters.to) return false;
    if (filters.side && trade.side !== filters.side) return false;
    return true;
  });
}
