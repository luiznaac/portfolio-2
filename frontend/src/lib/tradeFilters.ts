import type { Trade } from "../api/types.ts";
import {
  type FilterField,
  type FilterValues,
  applyFilters,
  emptyFilterValues,
  matchesText,
} from "./filters.ts";
import { formatTradeSide } from "./format.ts";

// The Ativos · Negócios filter (#367): ticker, an inclusive date range and the side. Declared once so
// the shared bar and the ledger narrowing read the same fields.
export const TRADE_FILTER_FIELDS: FilterField<Trade>[] = [
  {
    control: { kind: "text", key: "ticker", label: "Ticker", placeholder: "Ticker" },
    match: (trade, value) => matchesText(value, trade.ticker),
  },
  {
    control: { kind: "date", key: "from", label: "De" },
    // ISO `YYYY-MM-DD` dates compare lexicographically, which is the calendar order; both bounds
    // are inclusive.
    match: (trade, value) => trade.date >= value,
  },
  {
    control: { kind: "date", key: "to", label: "Até" },
    match: (trade, value) => trade.date <= value,
  },
  {
    control: {
      kind: "select",
      key: "side",
      label: "Lado",
      emptyLabel: "Todos os lados",
      options: (["BUY", "SELL"] as const).map((side) => ({
        value: side,
        label: formatTradeSide(side),
      })),
    },
    match: (trade, value) => trade.side === value,
  },
];

export const emptyTradeFilters: FilterValues = emptyFilterValues(TRADE_FILTER_FIELDS);

export function filterTrades(trades: Trade[], values: FilterValues): Trade[] {
  return applyFilters(trades, values, TRADE_FILTER_FIELDS);
}
