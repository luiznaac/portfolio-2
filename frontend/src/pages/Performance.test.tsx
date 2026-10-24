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
});
