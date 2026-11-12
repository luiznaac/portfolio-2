import { describe, expect, it } from "vitest";
import type { Allocation, Gap, Position, Quote } from "../api/types.ts";
import { latestQuote, positionRows, positionValue, positionsTotal } from "./position.ts";

function quote(date: string, close: number): Quote {
  return { ticker: "PETR4", date, close, source: "COTAHIST" };
}

describe("latestQuote", () => {
  it("returns undefined for an empty series", () => {
    expect(latestQuote([])).toBeUndefined();
  });

  it("returns the quote with the latest date, not the last element", () => {
    const quotes = [quote("2026-10-10", 38), quote("2026-10-14", 40), quote("2026-10-12", 39)];

    expect(latestQuote(quotes)?.date).toBe("2026-10-14");
    expect(latestQuote(quotes)?.close).toBe(40);
  });

  it("returns the only quote in a single-element series", () => {
    expect(latestQuote([quote("2026-10-14", 40)])?.close).toBe(40);
  });
});

describe("positionValue", () => {
  it("multiplies the quantity by the close", () => {
    expect(positionValue(100, 40)).toBe(4000);
  });

  it("is zero for an empty position", () => {
    expect(positionValue(0, 40)).toBe(0);
  });
});

function position(ticker: string, quantity: number, average_price: number): Position {
  return { ticker, quantity, average_price };
}

function gap(ticker: string, status: Gap["status"] = "OPEN"): Gap {
  return {
    id: 1,
    ticker,
    date: "2026-10-01",
    quantity: 1,
    status,
    trade_watermark: 0,
    action_watermark: 0,
  };
}

function allocation(): Allocation {
  return {
    period: "2026-10",
    capital: 10_000,
    floor: 0,
    ideal: 10_000,
    actual: 1_150,
    deficit: 0,
    drift: 0,
    unattributed: 0,
    unclassified: {
      actual: 150,
      tickers: [
        { ticker: "ZZZZ9", quantity: 10, actual: 150, price: 15, quote_date: "2026-10-13" },
      ],
    },
    classes: [
      {
        asset_class: "ACOES",
        target: 50,
        ideal: 5_000,
        actual: 1_000,
        deficit: 0,
        drift: 0,
        unattributed: 0,
        sub_classes: [],
        outside: { actual: 0, tickers: [] },
        strategies: [
          {
            strategy: "Top Dividendos",
            target: 50,
            ideal: 5_000,
            actual: 1_000,
            deficit: 0,
            drift: 0,
            tickers: [
              {
                ticker: "PETR4",
                quantity: 100,
                target: 50,
                ideal: 5_000,
                actual: 4_000,
                price: 40,
                quote_date: "2026-09-01",
                stale: true,
              },
              {
                ticker: "VALE3",
                quantity: 10,
                target: 50,
                ideal: 5_000,
              },
            ],
          },
        ],
      },
    ],
  };
}

describe("positionRows", () => {
  it("joins the stored position to its allocation leaf and sorts by ticker", () => {
    const rows = positionRows(
      [position("VALE3", 10, 60), position("PETR4", 100, 30), position("ZZZZ9", 10, 12)],
      allocation(),
    );

    expect(rows.map((row) => row.ticker)).toEqual(["PETR4", "VALE3", "ZZZZ9"]);
    expect(rows[0]).toMatchObject({
      quantity: 100,
      averagePrice: 30,
      assetClass: "ACOES",
      close: 40,
      closeDate: "2026-09-01",
      value: 4_000,
      stale: true,
    });
    expect(rows[1]).toMatchObject({ assetClass: "ACOES", value: undefined, close: undefined });
    expect(rows[2]).toMatchObject({
      assetClass: null,
      quantity: 10,
      value: 150,
      close: 15,
    });
  });

  it("values a ticker split across attribution slices from its whole custody, not one slice", () => {
    const split = allocation();
    split.classes[0].strategies[0].tickers = [
      {
        ticker: "PETR4",
        quantity: 60,
        target: 50,
        ideal: 5_000,
        actual: 2_400,
        price: 40,
        quote_date: "2026-09-01",
        stale: true,
      },
      {
        ticker: "PETR4",
        quantity: 40,
        target: 50,
        ideal: 5_000,
        actual: 1_600,
        price: 40,
        quote_date: "2026-09-01",
      },
    ];

    const rows = positionRows([position("PETR4", 100, 30)], split);

    expect(rows).toHaveLength(1);
    expect(rows[0]?.value).toBe(4_000);
    expect(rows[0]?.stale).toBe(true);
  });

  it("counts only open gaps per ticker", () => {
    const rows = positionRows(
      [position("PETR4", 100, 30), position("VALE3", 10, 60)],
      allocation(),
      [gap("PETR4"), gap("PETR4"), gap("VALE3", "REPLACED")],
    );

    expect(rows.find((row) => row.ticker === "PETR4")?.gaps).toBe(2);
    expect(rows.find((row) => row.ticker === "VALE3")?.gaps).toBe(0);
  });

  it("treats a position missing from the allocation as unclassified and unpriced", () => {
    const rows = positionRows([position("MISS3", 5, 9)], allocation());

    expect(rows[0]).toMatchObject({
      ticker: "MISS3",
      assetClass: null,
      close: undefined,
      value: undefined,
      stale: false,
      gaps: 0,
    });
  });
});

describe("positionsTotal", () => {
  it("sums the priced rows and marks the total partial while a row has no value", () => {
    const rows = positionRows(
      [position("PETR4", 100, 30), position("VALE3", 10, 60)],
      allocation(),
    );

    expect(positionsTotal(rows)).toEqual({ value: 4_000, partial: true });
  });

  it("is undefined, not zero, when every row is unpriced", () => {
    const rows = positionRows([position("VALE3", 10, 60)], allocation());

    expect(positionsTotal(rows)).toEqual({ value: undefined, partial: true });
  });

  it("follows the rows it is given, so a filtered table carries a filtered total", () => {
    const rows = positionRows(
      [position("PETR4", 100, 30), position("ZZZZ9", 10, 12)],
      allocation(),
    );

    expect(positionsTotal(rows.filter((row) => row.ticker === "ZZZZ9"))).toEqual({
      value: 150,
      partial: false,
    });
  });
});
