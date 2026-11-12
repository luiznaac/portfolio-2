import { describe, expect, it } from "vitest";
import {
  type FilterField,
  applyFilters,
  clearFilterValues,
  emptyFilterValues,
  filterCountLabel,
  hasActiveFilter,
  matchesText,
  setFilterValue,
} from "./filters.ts";

interface Row {
  ticker: string;
  side: "BUY" | "SELL";
}

const fields: FilterField<Row>[] = [
  {
    control: { kind: "text", key: "ticker", label: "Ticker" },
    match: (row, value) => matchesText(value, row.ticker),
  },
  {
    control: {
      kind: "select",
      key: "side",
      label: "Lado",
      emptyLabel: "Todos",
      options: [
        { value: "BUY", label: "Compra" },
        { value: "SELL", label: "Venda" },
      ],
    },
    match: (row, value) => row.side === value,
  },
];

const rows: Row[] = [
  { ticker: "PETR4", side: "BUY" },
  { ticker: "VALE3", side: "SELL" },
  { ticker: "PETR3", side: "SELL" },
];

describe("matchesText", () => {
  it("matches a case-insensitive substring of any value", () => {
    expect(matchesText("petr", "PETR4")).toBe(true);
    expect(matchesText("PETR", "vale3", "petr4")).toBe(true);
    expect(matchesText("vale", "PETR4")).toBe(false);
  });

  it("treats a blank or whitespace-only query as no narrowing", () => {
    expect(matchesText("", "PETR4")).toBe(true);
    expect(matchesText("   ", "PETR4")).toBe(true);
  });
});

describe("applyFilters", () => {
  it("returns every row when no value is set", () => {
    expect(applyFilters(rows, emptyFilterValues(fields), fields)).toEqual(rows);
  });

  it("narrows by a text field", () => {
    const filtered = applyFilters(rows, { ticker: "petr" }, fields);

    expect(filtered.map((row) => row.ticker)).toEqual(["PETR4", "PETR3"]);
  });

  it("narrows by a select field and combines it with the text field", () => {
    expect(
      applyFilters(rows, { ticker: "petr", side: "SELL" }, fields).map((row) => row.ticker),
    ).toEqual(["PETR3"]);
  });
});

describe("hasActiveFilter", () => {
  it("is false only while every value is blank", () => {
    expect(hasActiveFilter(emptyFilterValues(fields))).toBe(false);
    expect(hasActiveFilter({ ticker: "", side: "" })).toBe(false);
    expect(hasActiveFilter({ ticker: "   ", side: "" })).toBe(false);
    expect(hasActiveFilter({ ticker: "petr", side: "" })).toBe(true);
    expect(hasActiveFilter({ ticker: "", side: "SELL" })).toBe(true);
  });
});

describe("filterCountLabel", () => {
  it("states the shown and total counts", () => {
    expect(filterCountLabel(2, 3)).toBe("2 de 3");
  });
});

describe("filter state helpers", () => {
  it("starts every declared field blank", () => {
    expect(emptyFilterValues(fields)).toEqual({ ticker: "", side: "" });
  });

  it("replaces one value without mutating the current state", () => {
    const current = emptyFilterValues(fields);
    const next = setFilterValue(current, "ticker", "petr");

    expect(next).toEqual({ ticker: "petr", side: "" });
    expect(current).toEqual({ ticker: "", side: "" });
  });

  it("clears every value while keeping the declared keys", () => {
    expect(clearFilterValues({ ticker: "petr", side: "SELL" })).toEqual({ ticker: "", side: "" });
  });
});
