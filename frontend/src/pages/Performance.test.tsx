// @vitest-environment jsdom
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { Month, MonthPerformance, PerformanceSeries } from "../api/types.ts";
import { AnalysisWindowProvider } from "../hooks/useAnalysisWindow.tsx";
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
  useBenchmarkStatus: () => ({
    isLoading: false,
    isError: false,
    data: { indexes: [] },
    error: null,
  }),
  useRefreshBenchmarks: () => ({
    isPending: false,
    data: undefined,
    error: null,
    mutate: () => undefined,
  }),
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
      benchmarks: [{ index: "IBOV", return_percentage: 2.5 }, { index: "CDI" }],
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
      <AnalysisWindowProvider>
        <Performance />
      </AnalysisWindowProvider>
    </MemoryRouter>,
  );
}

describe("Performance", () => {
  it("opens on the shared rail with 12 months ending the last closed month", () => {
    renderPage();

    expect(screen.getByTestId("period-rail")).toBeTruthy();
    expect(screen.getByTestId("rail-range").textContent).toContain("mar/2025 – fev/2026");
    expect(screen.getByTestId("rail-preset-12m").getAttribute("aria-pressed")).toBe("true");
  });

  it("falls back to the newest series month while the closed list is empty", () => {
    mock.closedMonths = [];
    mock.detail = null;
    renderPage();

    expect(screen.getByTestId("rail-range").textContent).toContain("mar/2025 – fev/2026");
    expect(screen.getByTestId("month-row-2026-02")).toBeTruthy();
    expect(screen.getByTestId("month-row-2026-01")).toBeTruthy();
  });

  it("reads the window's return, the blend coverage and the closing value", () => {
    renderPage();

    expect(screen.getByTestId("period-return-kpi").textContent).toContain("+1,50%");
    expect(screen.getByTestId("benchmark-kpi").textContent).toContain("+0,90%");
    expect(screen.getByTestId("last-month-kpi").textContent).toContain("+1,50%");
    expect(screen.getByTestId("value-kpi").textContent).toContain("R$ 103.500,00");
    expect(screen.getByTestId("benchmark-coverage-caption").textContent).toContain(
      "blend cobre 1 de 2 meses",
    );
    expect(screen.getByTestId("benchmark-coverage-caption").textContent).toContain(
      "mar/2025 – fev/2026",
    );
    // The per-index strip stays beside the blend caption: IBOV is priced in February only.
    expect(screen.getByTestId("index-coverage-IBOV").textContent).toContain("1 mês sem dado");
  });

  it("renders the base-100 cumulative chart with the blend dashed", () => {
    renderPage();

    expect(screen.getByTestId("cumulative-chart")).toBeTruthy();
    expect(screen.getByText("início")).toBeTruthy();
    const blend = screen.getByTestId("blend-line");
    expect(blend.getAttribute("stroke-dasharray")).not.toBeNull();
    expect(screen.getByTestId("portfolio-line").getAttribute("stroke-dasharray")).toBeNull();
    expect(screen.getByTestId("index-line-IBOV").getAttribute("stroke-dasharray")).not.toBeNull();
    // February is the only priced month, so the dashed blend gets a marker there and none in January.
    expect(screen.getByTestId("blend-point-2026-02")).toBeTruthy();
    expect(screen.queryByTestId("blend-point-2026-01")).toBeNull();
  });

  it("renders the month-by-month table, the anchor mark and the decomposition in R$", () => {
    renderPage();

    expect(screen.getByTestId("month-table")).toBeTruthy();
    expect(screen.getByTestId("month-row-2026-02")).toBeTruthy();
    expect(screen.getByTestId("month-row-2026-01")).toBeTruthy();
    expect(screen.getByTestId("month-anchor-2026-02").textContent).toContain("âncora");
    expect(screen.getByTestId("decomposition-chart")).toBeTruthy();
    expect(screen.getByTestId("aportes-performance")).toBeTruthy();
    expect(screen.getByTestId("class-chart")).toBeTruthy();
    expect(screen.getByTestId("strategy-chart")).toBeTruthy();
  });

  it("keeps the strategy series surface with an empty message when snapshots carry no slices", () => {
    mock.series = {
      ...snapshotSeries,
      months: snapshotSeries.months.map((month) => ({ ...month, strategies: undefined })),
    };
    renderPage();

    const chart = screen.getByTestId("strategy-chart");
    expect(chart.textContent).toContain("Sem valores por estratégia nos meses fechados.");
  });

  it("anchors the month detail and the benchmark refresh panel", () => {
    renderPage();

    expect(screen.getByTestId("benchmark-panel")).toBeTruthy();
    expect(screen.getByTestId("class-table")).toBeTruthy();
    expect(screen.getByTestId("strategy-table")).toBeTruthy();
    expect(screen.getByTestId("ticker-table")).toBeTruthy();
    expect(screen.getByTestId("benchmark-refresh")).toBeTruthy();
    expect(screen.getAllByText("IBOV").length).toBeGreaterThan(0);
    expect(screen.getByText("Blend alvo")).toBeTruthy();
    expect(screen.getAllByText("PETR4").length).toBeGreaterThan(0);
  });

  it("narrows the month table when a preset or the anchor changes", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByTestId("rail-preset-1m"));
    expect(screen.getByTestId("rail-range").textContent).toContain("fev/2026 – fev/2026");
    expect(screen.getByTestId("month-row-2026-02")).toBeTruthy();
    expect(screen.queryByTestId("month-row-2026-01")).toBeNull();
    expect(screen.getByTestId("benchmark-coverage-caption").textContent).toContain(
      "blend cobre 1 de 1 mês",
    );
    // A one-month window still renders the per-class and per-strategy series as visible points.
    expect(screen.getByTestId("class-chart").querySelector("circle")).not.toBeNull();
    expect(screen.getByTestId("strategy-chart").querySelector("circle")).not.toBeNull();

    await user.click(screen.getByLabelText("Um mês antes"));
    expect(screen.getByTestId("rail-range").textContent).toContain("jan/2026 – jan/2026");
    expect(screen.getByTestId("month-row-2026-01")).toBeTruthy();
    expect(screen.queryByTestId("month-row-2026-02")).toBeNull();
  });

  it("renders the computed era with Valor investido and no benchmark line", () => {
    mock.closedMonths = [];
    mock.series = computedSeries;
    mock.detail = null;
    renderPage();

    expect(screen.getByText("Valor investido")).toBeTruthy();
    expect(screen.getByTestId("value-kpi").textContent).toContain("R$ 1.100,00");
    expect(screen.queryByTestId("blend-line")).toBeNull();
    expect(screen.queryByTestId("blend-point-2026-01")).toBeNull();
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
    expect(screen.queryByTestId("period-rail")).toBeNull();
  });

  it("names the blocking leg in the anchor month's benchmark panel", () => {
    mock.detail = {
      ...monthDetail,
      benchmark: {
        contributions: [
          { label: "ACOES", target: 60, benchmark: "IBOV" },
          { label: "RENDA_FIXA", target: 40, benchmark: "CDI", return_percentage: 0.9 },
        ],
        return_percentage: undefined,
      },
    };
    renderPage();

    const panel = screen.getByTestId("benchmark-panel");
    expect(within(panel).getByTestId("benchmark-row-IBOV").textContent).toBe("sem dados");
    expect(within(panel).getByTestId("benchmark-row-blend").textContent).toBe("sem dados");
    expect(within(panel).getByTestId("blocking-legs").textContent).toContain("ACOES (IBOV)");
  });
});
