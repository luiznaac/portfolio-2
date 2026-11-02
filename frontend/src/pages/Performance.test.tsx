// @vitest-environment jsdom
import { cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { Month, MonthPerformance, PerformanceSeries } from "../api/types.ts";
import { Performance } from "./Performance.tsx";

const mock = vi.hoisted(() => ({
  closedMonths: [] as Month[],
  series: null as PerformanceSeries | null,
  detail: null as MonthPerformance | null,
}));

vi.mock("../api/queries.ts", () => ({
  useClosedMonths: () => ({ isLoading: false, isError: false, data: mock.closedMonths }),
  usePerformanceSeries: () => ({ isLoading: false, isError: false, data: mock.series }),
  useMonthPerformance: () => ({ isLoading: false, isError: false, data: mock.detail }),
}));

const closedMonths: Month[] = [
  { id: 102, period: "2026-02", capital: 103500, floor: 0, status: "CLOSED", aportes: 2000 },
  { id: 101, period: "2026-01", capital: 100000, floor: 0, status: "CLOSED", aportes: 0 },
];

const snapshotSeries: PerformanceSeries = {
  months: [
    {
      period: "2026-01",
      source: "snapshot",
      decomposition: {
        value_end: 100000,
        net_aportes: 0,
        net_income: 0,
      },
      xirr: undefined,
      benchmark_return: undefined,
      classes: [{ asset_class: "ACOES", value_end: 60000 }],
      strategies: [
        {
          strategy_id: 1,
          strategy: "Top Dividendos",
          kind: "REPORT_DRIVEN",
          value_end: 30000,
          flow: 0,
        },
        { strategy_id: 2, strategy: "Legado", kind: "MANUAL", value_end: 20000, flow: 0 },
      ],
    },
    {
      period: "2026-02",
      source: "snapshot",
      decomposition: {
        value_start: 100000,
        value_end: 103500,
        net_aportes: 2000,
        net_income: 0,
        return_percentage: 1.5,
      },
      xirr: 12,
      benchmark_return: 0.9,
      classes: [{ asset_class: "ACOES", value_end: 62100 }],
      strategies: [
        {
          strategy_id: 1,
          strategy: "Top Dividendos",
          kind: "REPORT_DRIVEN",
          value_end: 31000,
          flow: 0,
        },
        { strategy_id: 2, strategy: "Legado", kind: "MANUAL", value_end: 20000, flow: 0 },
      ],
    },
  ],
  xirr: 12,
};

const monthDetail: MonthPerformance = {
  month_id: 102,
  period: "2026-02",
  decomposition: {
    value_start: 100000,
    value_end: 103500,
    net_aportes: 2000,
    net_income: 0,
    return_percentage: 1.5,
  },
  xirr: 12,
  benchmark: {
    return_percentage: 0.9,
    contributions: [
      { label: "ACOES", target: 60, benchmark: "IBOV", return_percentage: 2.5 },
      { label: "RENDA_FIXA", target: 40, benchmark: "CDI", return_percentage: 0.9 },
    ],
  },
  classes: [
    {
      asset_class: "ACOES",
      decomposition: {
        value_start: 60000,
        value_end: 62100,
        net_aportes: 1200,
        net_income: 0,
        return_percentage: 1.5,
      },
      benchmark_return: 2.5,
      strategies: [
        {
          strategy_id: 1,
          strategy: "Top Dividendos",
          kind: "REPORT_DRIVEN",
          decomposition: {
            value_start: 30000,
            value_end: 31000,
            net_aportes: 600,
            net_income: 40,
            return_percentage: 1.3,
          },
        },
        {
          strategy_id: 2,
          strategy: "Legado",
          kind: "MANUAL",
          decomposition: {
            value_start: 20000,
            value_end: 20000,
            net_aportes: 400,
            net_income: 60,
            flow: 0,
            return_percentage: -2,
          },
        },
      ],
      outside: {
        value_start: 10000,
        value_end: 11100,
        net_aportes: 200,
        net_income: 10,
        return_percentage: 9,
      },
    },
  ],
  tickers: [
    {
      ticker: "PETR4",
      asset_class: "ACOES",
      decomposition: {
        value_start: 60000,
        value_end: 62100,
        net_aportes: 1200,
        net_income: 0,
        return_percentage: 1.5,
      },
      benchmark_return: 2.5,
    },
  ],
};

const computedSeries: PerformanceSeries = {
  months: [
    {
      period: "2025-12",
      source: "computed",
      decomposition: {
        value_end: 1000,
        net_aportes: 1000,
        net_income: 0,
      },
      benchmark_return: undefined,
      classes: [{ asset_class: "ACOES", value_end: 1000 }],
    },
    {
      period: "2026-01",
      source: "computed",
      decomposition: {
        value_start: 1000,
        value_end: 1100,
        net_aportes: 0,
        net_income: 0,
        return_percentage: 10,
      },
      benchmark_return: undefined,
      benchmarks: [{ index: "IBOV", return_percentage: 2 }, { index: "CDI" }, { index: "IPCA" }],
      classes: [{ asset_class: "ACOES", value_end: 1100 }],
      partial: true,
    },
  ],
  computed_xirr: 8,
  ledger_empty: false,
};

beforeEach(() => {
  mock.closedMonths = closedMonths;
  mock.series = snapshotSeries;
  mock.detail = monthDetail;
});

afterEach(cleanup);

function renderPage() {
  return render(
    <MemoryRouter>
      <Performance />
    </MemoryRouter>,
  );
}

describe("Performance", () => {
  it("shows the since-inception XIRR and the monthly decomposition", () => {
    renderPage();

    expect(screen.getByTestId("xirr").textContent).toContain("12,00%");
    expect(screen.getByTestId("decomposition-table")).toBeTruthy();
    expect(screen.getAllByText("fev/2026").length).toBeGreaterThan(0);
    expect(screen.getByText("R$ 1.500,00")).toBeTruthy();
  });

  it("renders the portfolio × benchmark and the aportes charts", () => {
    renderPage();

    expect(screen.getByTestId("return-chart")).toBeTruthy();
    expect(screen.getByTestId("cumulative-chart")).toBeTruthy();
    expect(screen.getByTestId("class-chart")).toBeTruthy();
    expect(screen.getByTestId("decomposition-chart")).toBeTruthy();
    expect(screen.getByTestId("aportes-performance")).toBeTruthy();
    expect(screen.getAllByText("IPCA").length).toBeGreaterThan(0);
    // Neither snapshot point carries a benchmark series, so the coverage strip names the gap.
    expect(screen.getByTestId("benchmark-coverage-IBOV").textContent).toContain("2 meses sem dado");
  });

  it("renders the selected month's benchmark, class and ticker views", () => {
    renderPage();

    expect(screen.getByTestId("benchmark-panel")).toBeTruthy();
    expect(screen.getAllByText("IBOV").length).toBeGreaterThan(0);
    expect(screen.getByText("Blend alvo")).toBeTruthy();
    expect(screen.getByTestId("class-table")).toBeTruthy();
    expect(screen.getByTestId("ticker-table")).toBeTruthy();
    expect(screen.getAllByText("PETR4").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Ações").length).toBeGreaterThan(0);
  });

  it("renders the per-Strategy slices, the outside row and the strategy series", () => {
    renderPage();

    expect(screen.getByTestId("strategy-table")).toBeTruthy();
    expect(screen.getAllByText("Top Dividendos").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Legado").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Fora da estratégia").length).toBeGreaterThan(0);
    // The shared-Ticker split: the report slice takes 40 of the ticker's income, the bucket 60.
    expect(screen.getAllByText("R$ 40,00").length).toBeGreaterThan(0);
    expect(screen.getAllByText("R$ 60,00").length).toBeGreaterThan(0);
    expect(screen.getByTestId("strategy-chart")).toBeTruthy();
  });

  it("renders the computed era with Valor investido, the divider and no month detail", () => {
    mock.closedMonths = [];
    mock.series = computedSeries;
    mock.detail = null;
    renderPage();

    expect(screen.getByText("Valor investido")).toBeTruthy();
    expect(screen.getByTestId("value-kpi").textContent).toContain("R$ 1.100,00");
    expect(screen.getByTestId("computed-xirr").textContent).toContain("8,00%");
    expect(screen.queryByTestId("xirr")).toBeNull();
    expect(screen.getByTestId("era-divider").textContent).toContain("histórico · valor investido");
    expect(screen.getByTestId("detail-snapshot-only")).toBeTruthy();
    expect(screen.getByTestId("month-partial-2026-01").textContent).toContain("parcial");
    expect(screen.queryByText("Sem meses fechados")).toBeNull();
  });

  it("keeps the empty-history state only for an empty ledger", () => {
    mock.closedMonths = [];
    mock.series = { months: [], xirr: undefined, ledger_empty: true };
    mock.detail = null;
    renderPage();

    expect(screen.getByText("Sem meses fechados")).toBeTruthy();
  });

  it("names the blocking leg and reads an absent index as sem dados", () => {
    mock.detail = {
      ...monthDetail,
      benchmark: {
        contributions: [
          { label: "ACOES", target: 60, benchmark: "IBOV" },
          { label: "RENDA_FIXA", target: 40, benchmark: "CDI", return_percentage: 0.9 },
        ],
      },
    };
    renderPage();

    expect(screen.getByTestId("benchmark-row-IBOV").textContent).toBe("sem dados");
    expect(screen.getByTestId("benchmark-row-blend").textContent).toBe("sem dados");
    expect(screen.getByTestId("blocking-legs").textContent).toContain("ACOES (IBOV)");
  });
});
