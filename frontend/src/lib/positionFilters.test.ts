import { describe, expect, it } from "vitest";
import { emptyFilterValues } from "./filters.ts";
import type { PositionRow } from "./position.ts";
import {
  POSITION_FILTER_FIELDS,
  UNCLASSIFIED_CLASS_FILTER,
  filterPositions,
} from "./positionFilters.ts";

function row(overrides: Partial<PositionRow>): PositionRow {
  return {
    ticker: "PETR4",
    quantity: 100,
    averagePrice: 30,
    assetClass: "ACOES",
    close: 40,
    closeDate: "2026-10-14",
    value: 4_000,
    stale: false,
    gaps: 0,
    ...overrides,
  };
}

const ROWS = [
  row({ ticker: "PETR4", assetClass: "ACOES" }),
  row({ ticker: "HGLG11", assetClass: "FIIS" }),
  row({ ticker: "ZZZZ9", assetClass: null }),
];

describe("positions filter", () => {
  it("narrows by ticker, case-insensitively", () => {
    const values = { ...emptyFilterValues(POSITION_FILTER_FIELDS), ticker: "petr" };

    expect(filterPositions(ROWS, values).map((item) => item.ticker)).toEqual(["PETR4"]);
  });

  it("narrows by class", () => {
    const values = { ...emptyFilterValues(POSITION_FILTER_FIELDS), class: "FIIS" };

    expect(filterPositions(ROWS, values).map((item) => item.ticker)).toEqual(["HGLG11"]);
  });

  it("narrows to unclassified tickers with the Sem classe option", () => {
    const values = {
      ...emptyFilterValues(POSITION_FILTER_FIELDS),
      class: UNCLASSIFIED_CLASS_FILTER,
    };

    expect(filterPositions(ROWS, values).map((item) => item.ticker)).toEqual(["ZZZZ9"]);
  });

  it("declares the Sem classe option on the class control", () => {
    const control = POSITION_FILTER_FIELDS.find((field) => field.control.key === "class")?.control;

    expect(control?.kind).toBe("select");
    if (control?.kind !== "select") throw new Error("class control is not a select");
    expect(control.options).toContainEqual({ value: "sem-classe", label: "Sem classe" });
  });
});
