import { describe, expect, it } from "vitest";
import { TRADE_FILTER_FIELDS, tradeLedgerQuery } from "./tradeFilters.ts";

describe("tradeLedgerQuery", () => {
  it("sends no narrowing when every field is blank", () => {
    expect(tradeLedgerQuery({ ticker: "", from: "", to: "", side: "" })).toEqual({
      ticker: undefined,
      from: undefined,
      to: undefined,
      side: undefined,
    });
  });

  it("maps the ticker, the inclusive date window and the side", () => {
    expect(
      tradeLedgerQuery({
        ticker: "  petr4  ",
        from: "2026-10-01",
        to: "2026-10-31",
        side: "SELL",
      }),
    ).toEqual({
      ticker: "petr4",
      from: "2026-10-01",
      to: "2026-10-31",
      side: "SELL",
    });
  });

  it("drops a whitespace-only ticker but keeps the other filters", () => {
    expect(tradeLedgerQuery({ ticker: "   ", side: "BUY" })).toEqual({
      ticker: undefined,
      from: undefined,
      to: undefined,
      side: "BUY",
    });
  });

  it("declares the four controls the ledger filters by", () => {
    expect(TRADE_FILTER_FIELDS.map((field) => field.control.key)).toEqual([
      "ticker",
      "from",
      "to",
      "side",
    ]);
  });
});
