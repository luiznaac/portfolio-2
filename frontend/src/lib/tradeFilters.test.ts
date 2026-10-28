import { describe, expect, it } from "vitest";
import type { Trade } from "../api/types.ts";
import { emptyTradeFilters, filterTrades } from "./tradeFilters.ts";

function trade(overrides: Partial<Trade>): Trade {
  return {
    id: 1,
    ticker: "PETR4",
    date: "2026-10-14",
    venue: "VISTA",
    side: "BUY",
    quantity: 100,
    unit_price: 48.92,
    institution_id: 7,
    source: "B3_IMPORT",
    confirmed_by_b3: false,
    ordinal: 1,
    absorbed_quantity: 0,
    ...overrides,
  };
}

const trades = [
  trade({ id: 1, ticker: "PETR4", date: "2026-10-01", side: "BUY" }),
  trade({ id: 2, ticker: "VALE3", date: "2026-10-15", side: "SELL" }),
  trade({ id: 3, ticker: "PETR4", date: "2026-10-20", side: "SELL" }),
];

describe("filterTrades", () => {
  it("returns every trade when no filter is set", () => {
    expect(filterTrades(trades, emptyTradeFilters)).toHaveLength(3);
  });

  it("filters by ticker case-insensitively", () => {
    const result = filterTrades(trades, { ...emptyTradeFilters, ticker: "petr" });

    expect(result.map((t) => t.id)).toEqual([1, 3]);
  });

  it("filters by an inclusive date range", () => {
    const result = filterTrades(trades, {
      ...emptyTradeFilters,
      from: "2026-10-01",
      to: "2026-10-15",
    });

    expect(result.map((t) => t.id)).toEqual([1, 2]);
  });

  it("filters by side", () => {
    const result = filterTrades(trades, { ...emptyTradeFilters, side: "SELL" });

    expect(result.map((t) => t.id)).toEqual([2, 3]);
  });

  it("combines ticker, date and side filters", () => {
    const result = filterTrades(trades, {
      ticker: "PETR",
      from: "2026-10-02",
      to: "2026-10-31",
      side: "SELL",
    });

    expect(result.map((t) => t.id)).toEqual([3]);
  });
});
