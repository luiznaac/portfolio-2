// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { Allocation, Edition, Month, MonthPerformance, Strategy } from "../api/types.ts";
import { StrategyDetail } from "./StrategyDetail.tsx";

const mock = vi.hoisted(() => ({
  strategies: [] as Strategy[],
  allocation: null as Allocation | null,
  editions: [] as Edition[],
  months: [] as Month[],
  performance: null as MonthPerformance | null,
}));

vi.mock("../api/client.ts", () => ({
  api: {
    getStrategies: () => Promise.resolve(mock.strategies),
    getCurrentAllocation: () => Promise.resolve(mock.allocation),
    getEditions: () => Promise.resolve(mock.editions),
    getClosedMonths: () => Promise.resolve(mock.months),
    getMonthPerformance: () => Promise.resolve(mock.performance),
  },
}));

const strategy: Strategy = {
  id: 1,
  name: "Top Dividendos",
  asset_class: "ACOES",
  kind: "REPORT_DRIVEN",
};

const allocation: Allocation = {
  period: "2026-10",
  capital: 200000,
  floor: 0,
  ideal: 200000,
  actual: 100000,
  deficit: 100000,
  drift: -50,
  unattributed: 0,
  unclassified: { actual: 0, tickers: [] },
  classes: [
    {
      asset_class: "ACOES",
      target: 60,
      ideal: 120000,
      actual: 80000,
      deficit: 40000,
      drift: -20,
      unattributed: 0,
      strategies: [
        {
          strategy: "Top Dividendos",
          target: 60,
          ideal: 60000,
          actual: 12000,
          deficit: 48000,
          drift: -12.5,
          tickers: [
            {
              ticker: "PETR4",
              quantity: 100,
              target: 50,
              ideal: 30000,
              actual: 6000,
              deficit: 24000,
              drift: -12,
              price: 60,
            },
            {
              ticker: "VALE3",
              quantity: 50,
              target: 50,
              ideal: 30000,
              actual: 6000,
              deficit: 24000,
              drift: -13,
              price: 120,
            },
          ],
        },
      ],
      outside: { actual: 0, tickers: [] },
      sub_classes: [],
    },
  ],
};

const activeEdition: Edition = {
  id: 1,
  strategy_id: 1,
  reference_month: "2026-09",
  status: "ACTIVE",
  entries: [],
};

const stagedEdition: Edition = {
  id: 2,
  strategy_id: 1,
  reference_month: "2026-10",
  status: "STAGED",
  entries: [],
};

const month: Month = {
  id: 5,
  period: "2026-10",
  capital: 200000,
  floor: 0,
  status: "CLOSED",
  aportes: 0,
};

const performance: MonthPerformance = {
  month_id: 5,
  period: "2026-10",
  decomposition: { value_end: 100000, net_aportes: 0, net_income: 0 },
  benchmark: { contributions: [] },
  classes: [
    {
      asset_class: "ACOES",
      decomposition: { value_end: 80000, net_aportes: 0, net_income: 0 },
      strategies: [
        {
          strategy_id: 1,
          strategy: "Top Dividendos",
          kind: "REPORT_DRIVEN",
          decomposition: {
            value_start: 10000,
            value_end: 12000,
            net_aportes: 1500,
            net_income: 100,
            return_percentage: 4,
          },
        },
      ],
    },
  ],
  tickers: [],
};

function renderDetail(path: string) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/strategies/:id/:tab?" element={<StrategyDetail />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function cellsOf(row: HTMLElement): (string | null)[] {
  return within(row)
    .getAllByRole("cell")
    .map((cell) => cell.textContent);
}

afterEach(() => {
  cleanup();
  mock.strategies = [];
  mock.allocation = null;
  mock.editions = [];
  mock.months = [];
  mock.performance = null;
});

describe("the strategy detail shell", () => {
  it("opens with the KPI row and the Carteira tab's tickers and staged edition", async () => {
    mock.strategies = [strategy];
    mock.allocation = allocation;
    mock.editions = [activeEdition, stagedEdition];
    mock.months = [month];
    mock.performance = performance;

    renderDetail("/strategies/1");

    expect(await screen.findByRole("heading", { level: 1, name: "Top Dividendos" })).toBeDefined();
    expect(screen.getByTestId("strategy-kpi-fatia").textContent).toBe("R$ 12.000,00");
    expect(screen.getByTestId("strategy-kpi-alvo").textContent).toBe("60,0%");
    expect(screen.getByTestId("strategy-kpi-ideal").textContent).toBe("R$ 60.000,00");
    expect(screen.getByTestId("strategy-kpi-drift").textContent).toBe("-12,5 p.p.");
    await waitFor(() =>
      expect(screen.getByTestId("strategy-kpi-return").textContent).toBe("+4,00%"),
    );
    expect(await screen.findByText("12,0% da carteira")).toBeDefined();
    expect(screen.getByText("da classe Ações")).toBeDefined();

    expect(screen.getByRole("link", { name: "Carteira" })).toBeDefined();
    expect(screen.getByRole("link", { name: "Fatia no portfólio" })).toBeDefined();

    expect(cellsOf(screen.getByRole("row", { name: /PETR4/ }))).toEqual([
      "PETR4",
      "100",
      "R$ 60,00",
      "R$ 6.000,00",
      "50,0%",
      "R$ 30.000,00",
      "-12,0 p.p.",
    ]);

    expect(await screen.findByText("Edição em revisão")).toBeDefined();
    expect(screen.getByText("out/26")).toBeDefined();
  });

  it("switches to Fatia for the class and portfolio shares and the close decomposition", async () => {
    mock.strategies = [strategy];
    mock.allocation = allocation;
    mock.editions = [activeEdition, stagedEdition];
    mock.months = [month];
    mock.performance = performance;

    renderDetail("/strategies/1");
    const user = userEvent.setup();

    await user.click(await screen.findByRole("link", { name: "Fatia no portfólio" }));

    expect(await screen.findByText("Fatia na carteira")).toBeDefined();
    expect(await screen.findByText("12,0%")).toBeDefined();
    expect(screen.getByText("Fatia na classe")).toBeDefined();
    expect(screen.getByText("15,0%")).toBeDefined();
    // The KPI and the decomposition panel both name the close's reference month.
    expect(screen.getAllByText("Retorno out/26")).toHaveLength(2);

    const decomposition = screen.getByTestId("strategy-decomposition");
    expect(within(decomposition).getByText("R$ 10.000,00")).toBeDefined();
    expect(within(decomposition).getByText("R$ 12.000,00")).toBeDefined();
    expect(within(decomposition).getByText("R$ 1.500,00")).toBeDefined();
    expect(within(decomposition).getByText("R$ 100,00")).toBeDefined();
    expect(within(decomposition).getByText("+4,00%")).toBeDefined();

    expect(screen.queryByText("Edição em revisão")).toBeNull();
  });

  it("renders a not-found state for an unknown strategy id", async () => {
    mock.strategies = [strategy];

    renderDetail("/strategies/999");

    expect(await screen.findByText("Estratégia não encontrada")).toBeDefined();
  });
});
