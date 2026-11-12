import { describe, expect, it } from "vitest";
import type { Income } from "../api/types.ts";
import {
  byPayableDate,
  incomeLedgerRows,
  incomeRows,
  incomeStatusLabel,
  incomeTypeLabel,
  incomeWindowView,
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

const window = { start: "2026-10", end: "2026-11" };

function movement(overrides: Partial<Income>): Income {
  return {
    id: 1,
    ticker: "PETR4",
    date: "2026-10-14",
    type: "DIVIDEND",
    gross_amount: 10,
    retained_amount: 0,
    net_amount: 10,
    tax_nature: "INCOME",
    provenance: "B3",
    status: "RECEIVED",
    ...overrides,
  };
}

describe("incomeWindowView", () => {
  const receivedInWindow = movement({ id: 2, date: "2026-10-10", net_amount: 20 });
  const receivedBefore = movement({ id: 3, date: "2026-01-05", net_amount: 30 });
  const openInWindow = movement({ id: 1, date: "2026-11-20", status: "ANNOUNCED", net_amount: 40 });
  const openAfter = movement({ id: 4, date: "2026-12-01", status: "PAYABLE", net_amount: 50 });
  const all = [openAfter, receivedBefore, openInWindow, receivedInWindow];

  it("keeps the receivable rows forward-looking, even outside the window", () => {
    const view = incomeWindowView(all, window);

    expect(view.open.map((row) => row.id)).toEqual([1, 4]);
    expect(view.toReceiveTotal).toBe(90);
    expect(view.upcomingOutsideRange).toBe(true);
    expect(view.next?.id).toBe(1);
  });

  it("counts only settled rows inside the window as received", () => {
    const view = incomeWindowView(all, window);

    expect(view.received.map((row) => row.id)).toEqual([2]);
    expect(view.receivedTotal).toBe(20);
    expect(view.byMonth).toEqual([
      { period: "2026-10", value: 20 },
      { period: "2026-11", value: 0 },
    ]);
    expect(view.monthsInWindow).toBe(2);
  });

  it("has no receivable flag when every open row falls inside the window", () => {
    const view = incomeWindowView([openInWindow], window);

    expect(view.upcomingOutsideRange).toBe(false);
  });
});

describe("incomeLedgerRows", () => {
  const open = incomeRows([
    movement({ id: 1, date: "2026-11-20", status: "ANNOUNCED" }),
    movement({ id: 4, date: "2026-12-01", status: "PAYABLE" }),
  ]);
  const received = incomeRows([movement({ id: 2, date: "2026-10-10" })]);

  it("puts every receivable row before the settled window rows", () => {
    expect(incomeLedgerRows(open, received, window).map((row) => row.key)).toEqual([1, 4, 2]);
  });

  it("flags only a receivable row paid outside the window", () => {
    expect(incomeLedgerRows(open, received, window).map((row) => row.outsideWindow)).toEqual([
      false,
      true,
      false,
    ]);
  });

  it("marks the deep-linked row as the focused one", () => {
    expect(incomeLedgerRows(open, received, window, 4).map((row) => row.focused)).toEqual([
      false,
      true,
      false,
    ]);
  });
});
