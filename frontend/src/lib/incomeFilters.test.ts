import { describe, expect, it } from "vitest";
import type { IncomeRow } from "./income.ts";
import { emptyIncomeFilters, filterIncome } from "./incomeFilters.ts";

function row(overrides: Partial<IncomeRow>): IncomeRow {
  return {
    key: 1,
    date: "2026-10-14",
    ticker: "PETR4",
    typeLabel: "Dividendo",
    status: "RECEIVED",
    statusLabel: "Recebido",
    gross: 10,
    retained: 0,
    net: 10,
    ...overrides,
  };
}

const rows = [
  row({ key: 1, ticker: "PETR4", status: "RECEIVED" }),
  row({ key: 2, ticker: "VALE3", status: "PAYABLE" }),
  row({ key: 3, ticker: "PETR3", status: "RECEIVED" }),
];

describe("filterIncome", () => {
  it("returns every row when no filter is set", () => {
    expect(filterIncome(rows, emptyIncomeFilters)).toEqual(rows);
  });

  it("filters by ticker case-insensitively", () => {
    expect(filterIncome(rows, { ...emptyIncomeFilters, ticker: "petr" }).map((r) => r.key)).toEqual(
      [1, 3],
    );
  });

  it("filters by status", () => {
    expect(
      filterIncome(rows, { ...emptyIncomeFilters, status: "PAYABLE" }).map((r) => r.key),
    ).toEqual([2]);
  });

  it("combines ticker and status", () => {
    expect(filterIncome(rows, { ticker: "PETR", status: "RECEIVED" }).map((r) => r.key)).toEqual([
      1, 3,
    ]);
  });
});
