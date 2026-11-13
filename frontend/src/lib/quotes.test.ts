import { describe, expect, it } from "vitest";
import type { Quote, StaleQuote } from "../api/types.ts";
import { quoteRows } from "./quotes.ts";

function quote(ticker: string, date: string, close: number): Quote {
  return { ticker, date, close, source: "COTAHIST" };
}

function stale(ticker: string, date: string, daysOld: number): StaleQuote {
  return { ticker, date, close: 1, days_old: daysOld };
}

describe("quoteRows", () => {
  it("renders each ticker's latest close with its date and source", () => {
    const rows = quoteRows(
      ["PETR4", "VALE3"],
      new Map([
        ["PETR4", [quote("PETR4", "2026-10-29", 38), quote("PETR4", "2026-10-30", 39)]],
        ["VALE3", [quote("VALE3", "2026-10-30", 61.2)]],
      ]),
    );

    expect(rows).toEqual([
      {
        ticker: "PETR4",
        date: "2026-10-30",
        close: 39,
        source: "COTAHIST",
        staleDays: undefined,
        failed: false,
      },
      {
        ticker: "VALE3",
        date: "2026-10-30",
        close: 61.2,
        source: "COTAHIST",
        staleDays: undefined,
        failed: false,
      },
    ]);
  });

  it("marks a ticker stale from the status aggregate and keeps its age", () => {
    const rows = quoteRows(["VALE3"], new Map([["VALE3", [quote("VALE3", "2026-10-24", 61.2)]]]), [
      stale("VALE3", "2026-10-24", 6),
    ]);

    expect(rows[0]?.staleDays).toBe(6);
  });

  it("keeps a ticker with no stored close unknown, never R$ 0,00", () => {
    const rows = quoteRows(["NVDC34"], new Map());

    expect(rows).toEqual([
      {
        ticker: "NVDC34",
        date: undefined,
        close: undefined,
        source: undefined,
        staleDays: undefined,
        failed: false,
      },
    ]);
  });

  it("marks a failed per-ticker read apart from a missing close", () => {
    const rows = quoteRows(["ERRO3"], new Map(), [], new Set(["ERRO3"]));

    expect(rows[0]?.failed).toBe(true);
    expect(rows[0]?.close).toBeUndefined();
  });

  it("sorts by ticker and keeps one row per held ticker", () => {
    const rows = quoteRows(
      ["VALE3", "PETR4", "PETR4"],
      new Map([["PETR4", [quote("PETR4", "2026-10-30", 39)]]]),
    );

    expect(rows.map((row) => row.ticker)).toEqual(["PETR4", "VALE3"]);
  });
});
