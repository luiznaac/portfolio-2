// @vitest-environment jsdom
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type {
  CarryBucket,
  CurrentMonth,
  HarvestReport,
  Month,
  MonthPeriodRange,
  StockTaxReport,
} from "../api/types.ts";
import { Taxes } from "./Taxes.tsx";

const mock = vi.hoisted(() => ({
  current: null as CurrentMonth | null,
  range: { min: "2020-03", max: "2026-10", default: "2020-12" } as MonthPeriodRange,
  closed: [] as Month[],
  tax: undefined as unknown as StockTaxReport,
  harvest: undefined as unknown as HarvestReport,
}));

vi.mock("../api/queries.ts", () => ({
  useMonthPeriodRange: () => ({ isLoading: false, isError: false, data: mock.range }),
  useCurrentMonth: () => ({ isLoading: false, isError: false, data: mock.current }),
  useClosedMonths: () => ({ isLoading: false, isError: false, data: mock.closed }),
  useStockTax: () => ({ isLoading: false, isError: false, data: mock.tax }),
  useHarvest: () => ({ isLoading: false, isError: false, data: mock.harvest }),
  useAcceptHarvestPair: () => ({ mutate: vi.fn(), isPending: false, error: null }),
  useSetHarvestOptOut: () => ({ mutate: vi.fn(), isPending: false }),
}));

const carry: CarryBucket[] = [
  {
    kind: "SWING",
    available: false,
    opening_balance: null,
    loss_banked: null,
    loss_applied: null,
    closing_balance: null,
  },
];

function taxReport(overrides: Partial<StockTaxReport> = {}): StockTaxReport {
  return {
    period: "2020-12",
    meter: {
      gross_sales: 0,
      exemption_limit: 20000,
      exceeded: false,
      realized_gain: 0,
      taxable_gain: 0,
      rate: 15,
      tax: 0,
    },
    average_prices: [],
    realized: [
      { kind: "SWING", gross_sales: 0, gains: 0, losses: 0, result: 0 },
      { kind: "DAY_TRADE", gross_sales: 0, gains: 0, losses: 0, result: 0 },
      { kind: "FII", gross_sales: 0, gains: 0, losses: 0, result: 0 },
    ],
    carry,
    day_trades: [],
    pending_gaps: [],
    unclassified: [],
    darf_due_date: "2021-01-29",
    ...overrides,
  };
}

const harvest: HarvestReport = {
  period: "2026-10",
  headroom: 20000,
  minimum_loss: 500,
  suggestions: [],
  ignored: [],
  fii_report: [],
  pairs: [],
};

const month = (period: string, status: "OPEN" | "CLOSED"): Month => ({
  id: 1,
  period,
  capital: 1000,
  floor: 0,
  status,
  aportes: 0,
});

const currentMonth = (period: string): CurrentMonth => ({
  month: month(period, "OPEN"),
  targets: { classes: [], strategies: [], sub_classes: [], tickers: [] },
});

beforeEach(() => {
  mock.range = { min: "2020-03", max: "2026-10", default: "2020-12" };
  mock.current = null;
  mock.closed = [];
  mock.tax = taxReport();
  mock.harvest = harvest;
});

afterEach(cleanup);

describe("Impostos period read", () => {
  it("reads imported history without an open month and shows the empty state", () => {
    render(<Taxes />);

    expect(screen.getByLabelText("Ano")).toBeDefined();
    expect(screen.getByText("histórico")).toBeDefined();
    expect(screen.getByText("Sem vendas neste mês.")).toBeDefined();
    expect(screen.getAllByText(/só existe para o mês aberto/i).length).toBeGreaterThan(0);
    expect(screen.getAllByText("—").length).toBeGreaterThan(0);
    expect(screen.getByText(/Sem base antes da adoção/)).toBeDefined();
  });

  it("chips the open month and renders the harvest planner", () => {
    mock.range = { min: "2020-03", max: "2026-10", default: "2020-12" };
    mock.current = currentMonth("2026-10");
    mock.tax = taxReport({
      period: "2026-10",
      meter: {
        gross_sales: 1000,
        exemption_limit: 20000,
        exceeded: false,
        realized_gain: 100,
        taxable_gain: 0,
        rate: 15,
        tax: 0,
      },
      realized: [
        { kind: "SWING", gross_sales: 1000, gains: 100, losses: 0, result: 100 },
        { kind: "DAY_TRADE", gross_sales: 0, gains: 0, losses: 0, result: 0 },
        { kind: "FII", gross_sales: 0, gains: 0, losses: 0, result: 0 },
      ],
      carry: [{ ...carry[0], available: true }],
      darf_due_date: "2026-11-30",
    });

    render(<Taxes />);

    expect(screen.getByText("mês aberto")).toBeDefined();
    expect(screen.getByText("Harvest · step-up")).toBeDefined();
    expect((screen.getByLabelText("Mês") as HTMLSelectElement).value).toBe("10");
    expect(screen.queryByText("Sem vendas neste mês.")).toBeNull();
  });

  it("marks a past DARF deadline as overdue and flags pending gaps", () => {
    mock.tax = taxReport({
      pending_gaps: [{ id: 7, ticker: "PETR4", date: "2020-12-10", quantity: 100 }],
      unclassified: [{ ticker: "MYST3", status: "HELD_AND_SOLD" }],
    });

    render(<Taxes />);

    expect(screen.getByText("vencida")).toBeDefined();
    expect(screen.getByText("Resultado pendente")).toBeDefined();
    expect(screen.getByText("Sem classe")).toBeDefined();
    expect(screen.getByText(/PETR4 · 10\/12\/2020/)).toBeDefined();
  });
});
