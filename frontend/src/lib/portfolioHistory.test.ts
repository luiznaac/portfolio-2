import { describe, expect, it } from "vitest";
import type {
  ClassTotalSnapshot,
  Month,
  OrdersSnapshot,
  Snapshot,
  StrategySliceSnapshot,
} from "../api/types.ts";
import {
  FROZEN_CAPTION,
  frozenClassRows,
  frozenOrderRows,
  frozenSliceRows,
  historyListRows,
  snapshotSummary,
} from "./portfolioHistory.ts";

const month: Month = {
  id: 9,
  period: "2026-09",
  capital: 999_999,
  floor: 20_000,
  status: "CLOSED",
  aportes: 2_000,
};

const snapshot: Snapshot = {
  id: 3,
  month_id: 9,
  created_at: "2026-09-30T18:05:00",
  state: {
    period: "2026-09",
    capital: 240_000,
    floor: 20_000,
    aportes: 2_000,
    caixa: 45_000,
    holdings: [],
    attributions: [],
    classes: [],
    strategies: [],
    income: { gross: 100, retained: 15, net: 85 },
    orders: { orders: [], transfers: [], unplanned_trades: [] },
    tax: {
      carry: [],
      meter: {
        gross_sales: 0,
        exemption_limit: 20_000,
        exceeded: false,
        realized_gain: 0,
        taxable_gain: 0,
        rate: 15,
        tax: 250,
      },
    },
  },
};

const classes: ClassTotalSnapshot[] = [
  {
    asset_class: "ACOES",
    target: 60,
    ideal: 144_000,
    actual: 150_000,
    deficit: -6_000,
    drift: 2.5,
    unattributed: 0,
  },
  {
    asset_class: "RENDA_FIXA",
    ideal: 96_000,
    actual: 45_000,
    deficit: 51_000,
    drift: -21.2,
    unattributed: 45_000,
  },
];

const orders: OrdersSnapshot = {
  orders: [
    {
      id: 1,
      ticker: "ITUB4",
      side: "BUY",
      quantity: 10,
      suggested_price: 30,
      executed_quantity: 5,
      status: "SUGGESTED",
    },
    {
      id: 2,
      ticker: "B3SA3",
      side: "SELL",
      quantity: 10,
      executed_quantity: 10,
      status: "EXECUTED",
    },
  ],
  transfers: [],
  unplanned_trades: [{ id: 5, ticker: "VALE3", side: "SELL", quantity: 3 }],
};

const slices: StrategySliceSnapshot[] = [
  {
    strategy_id: 1,
    strategy: "Top Dividendos",
    kind: "REPORT_DRIVEN",
    value_end: 90_000,
    flow: 2_000,
  },
  { strategy_id: 2, strategy: "Legado", kind: "MANUAL", value_end: 60_000, flow: -1_000 },
];

describe("historyListRows", () => {
  it("makes one record per closed month, keeping the server's newest-first order", () => {
    const rows = historyListRows([month, { ...month, id: 8, period: "2026-08" }]);

    expect(rows.map((row) => row.label)).toEqual(["set/2026", "ago/2026"]);
    expect(rows[0]).toMatchObject({
      monthId: 9,
      href: "/portfolio/history/2026-09",
      capital: 999_999,
      aportes: 2_000,
    });
  });
});

describe("snapshotSummary", () => {
  it("reads the frozen values from the snapshot, not the live month row", () => {
    const summary = snapshotSummary(snapshot);

    expect(summary.capital).toBe(240_000);
    expect(summary.capital).not.toBe(month.capital);
    expect(summary.caixa).toBe(45_000);
    expect(summary.incomeNet).toBe(85);
    expect(summary.darf).toBe(250);
    expect(summary.periodLabel).toBe("set/2026");
    expect(summary.createdLabel).toBe("30/09/2026 18:05");
  });
});

describe("frozenClassRows", () => {
  it("keeps a manual class's absent target and labels every rung", () => {
    const rows = frozenClassRows(classes);

    expect(rows[0]).toMatchObject({ label: "Ações", target: 60, actual: 150_000 });
    expect(rows[1]).toMatchObject({ label: "Renda fixa", actual: 45_000 });
    expect(rows[1]?.target).toBeUndefined();
  });
});

describe("frozenOrderRows", () => {
  it("renders the month's orders and appends the unplanned trades", () => {
    const rows = frozenOrderRows(orders);

    expect(rows.map((row) => row.ticker)).toEqual(["ITUB4", "B3SA3", "VALE3"]);
    expect(rows[0]).toMatchObject({
      sideLabel: "Compra",
      quantity: 10,
      price: 30,
      stateLabel: "PARCIAL",
      stateTone: "amber",
    });
    expect(rows[1]).toMatchObject({
      sideLabel: "Venda",
      price: undefined,
      stateLabel: "Executado",
    });
    expect(rows[2]).toMatchObject({
      sideLabel: "Venda",
      quantity: 3,
      price: undefined,
      stateLabel: "Não planejada",
    });
  });
});

describe("frozenSliceRows", () => {
  it("labels each frozen strategy slice by kind", () => {
    expect(frozenSliceRows(slices).map((row) => row.kindLabel)).toEqual([
      "Com relatório",
      "Manual (legado)",
    ]);
    expect(frozenSliceRows(slices)[0]).toMatchObject({ valueEnd: 90_000, flow: 2_000 });
  });

  it("renders an older snapshot with no frozen slices as an empty list", () => {
    expect(frozenSliceRows(undefined)).toEqual([]);
  });
});

describe("FROZEN_CAPTION", () => {
  it("states that nothing on the surface is recomputed", () => {
    expect(FROZEN_CAPTION).toContain("Nada aqui é recalculado depois");
  });
});
