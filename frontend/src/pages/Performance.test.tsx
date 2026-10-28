// @vitest-environment jsdom
import { cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { Performance } from "./Performance.tsx";

vi.mock("../api/queries.ts", () => ({
  useClosedMonths: () => ({
    isLoading: false,
    isError: false,
    data: [
      { id: 102, period: "2026-02", capital: 103500, floor: 0, status: "CLOSED", aportes: 2000 },
      { id: 101, period: "2026-01", capital: 100000, floor: 0, status: "CLOSED", aportes: 0 },
    ],
  }),
  usePerformanceSeries: () => ({
    isLoading: false,
    isError: false,
    data: {
      months: [
        {
          period: "2026-01",
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
    },
  }),
  useMonthPerformance: () => ({
    isLoading: false,
    isError: false,
    data: {
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
    },
  }),
}));

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
});
