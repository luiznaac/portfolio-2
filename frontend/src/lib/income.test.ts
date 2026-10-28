import { describe, expect, it } from "vitest";
import type { Income } from "../api/types.ts";
import {
  byPayableDate,
  incomeRows,
  incomeStatusLabel,
  incomeTypeLabel,
  netTotal,
} from "./income.ts";

const dividend: Income = {
  id: 1,
  ticker: "PETR4",
  date: "2026-11-20",
  type: "DIVIDEND",
  gross_amount: 78.39,
  retained_amount: 13.33,
  net_amount: 65.06,
  tax_nature: "INCOME",
  provenance: "B3",
  status: "ANNOUNCED",
};

const jcp: Income = {
  id: 2,
  ticker: "ITSA4",
  date: "2026-11-05",
  type: "JCP",
  gross_amount: 160.65,
  retained_amount: 24.1,
  net_amount: 136.55,
  tax_nature: "INCOME",
  provenance: "B3",
  status: "PAYABLE",
};

const rendimento: Income = {
  id: 3,
  ticker: "MXRF11",
  date: "2026-11-10",
  type: "FUND_INCOME",
  gross_amount: 30,
  retained_amount: 0,
  net_amount: 30,
  tax_nature: "INCOME",
  provenance: "B3",
  status: "RECEIVED",
};

describe("incomeTypeLabel", () => {
  it("renders each kind in pt-BR", () => {
    expect(incomeTypeLabel("DIVIDEND")).toBe("Dividendo");
    expect(incomeTypeLabel("JCP")).toBe("JCP");
    expect(incomeTypeLabel("FUND_INCOME")).toBe("Rendimento");
  });
});

describe("incomeStatusLabel", () => {
  it("renders each lifecycle stage in pt-BR", () => {
    expect(incomeStatusLabel("ANNOUNCED")).toBe("Anunciado");
    expect(incomeStatusLabel("PAYABLE")).toBe("Pagável");
    expect(incomeStatusLabel("RECEIVED")).toBe("Recebido");
    expect(incomeStatusLabel("RECONCILED")).toBe("Conciliado");
  });
});

describe("incomeRows", () => {
  it("carries the amounts and derived labels", () => {
    const [row] = incomeRows([dividend]);

    expect(row).toEqual({
      key: 1,
      date: "2026-11-20",
      ticker: "PETR4",
      typeLabel: "Dividendo",
      status: "ANNOUNCED",
      statusLabel: "Anunciado",
      gross: 78.39,
      retained: 13.33,
      net: 65.06,
    });
  });

  it("is empty when there is no income", () => {
    expect(incomeRows([])).toEqual([]);
  });
});

describe("netTotal", () => {
  it("sums the net amount of the movements", () => {
    expect(netTotal([dividend, jcp, rendimento])).toBeCloseTo(231.61);
  });

  it("is zero for an empty list", () => {
    expect(netTotal([])).toBe(0);
  });
});

describe("byPayableDate", () => {
  it("orders the movements soonest first without mutating the input", () => {
    const input = [dividend, jcp, rendimento];

    expect(byPayableDate(input).map((income) => income.id)).toEqual([2, 3, 1]);
    expect(input.map((income) => income.id)).toEqual([1, 2, 3]);
  });
});
