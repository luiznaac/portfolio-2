import { describe, expect, it } from "vitest";
import type { Quote, Trade } from "../api/types.ts";
import { adoptionDefaultDate, isValidAdoptionRow, latestClose } from "./history.ts";

function trade(overrides: Partial<Trade> & Pick<Trade, "id" | "date">): Trade {
  return {
    ticker: "PETR4",
    venue: "VISTA",
    side: "BUY",
    quantity: 10,
    unit_price: 10,
    institution_id: 1,
    source: "B3_IMPORT",
    confirmed_by_b3: false,
    ordinal: 1,
    absorbed_quantity: 0,
    ...overrides,
  };
}

function quote(date: string, close: number): Quote {
  return { ticker: "PETR4", date, close, source: "COTAHIST" };
}

describe("adoption defaults", () => {
  it("defaults the seed date to the day before the earliest imported trade", () => {
    const trades = [trade({ id: 1, date: "2026-02-10" }), trade({ id: 2, date: "2026-01-10" })];

    expect(adoptionDefaultDate(trades, "2026-11-02")).toBe("2026-01-09");
  });

  it("ignores an opening seed and an unconfirmed manual trade", () => {
    const trades = [
      trade({ id: 1, date: "2025-12-01", source: "OPENING" }),
      trade({ id: 2, date: "2025-12-02", source: "MANUAL", confirmed_by_b3: false }),
      trade({ id: 3, date: "2026-03-01" }),
    ];

    expect(adoptionDefaultDate(trades, "2026-11-02")).toBe("2026-02-28");
  });

  it("falls back to today when no imported history exists", () => {
    expect(adoptionDefaultDate([], "2026-11-02")).toBe("2026-11-02");
  });

  it("uses the newest stored close as the unit price", () => {
    expect(latestClose([quote("2026-01-31", 9), quote("2026-02-27", 11)])).toBe(11);
    expect(latestClose([])).toBeNull();
  });

  it("accepts a row with a ticker, positive quantity, ISO date and non-negative price", () => {
    expect(
      isValidAdoptionRow({ ticker: "PETR4", quantity: 10, date: "2026-01-09", unitPrice: 0 }),
    ).toBe(true);
    expect(
      isValidAdoptionRow({ ticker: " ", quantity: 10, date: "2026-01-09", unitPrice: 1 }),
    ).toBe(false);
    expect(
      isValidAdoptionRow({ ticker: "PETR4", quantity: 0, date: "2026-01-09", unitPrice: 1 }),
    ).toBe(false);
    expect(
      isValidAdoptionRow({ ticker: "PETR4", quantity: 10, date: "2026-13-09", unitPrice: 1 }),
    ).toBe(false);
    expect(
      isValidAdoptionRow({ ticker: "PETR4", quantity: 10, date: "2026-01-09", unitPrice: -1 }),
    ).toBe(false);
  });
});
