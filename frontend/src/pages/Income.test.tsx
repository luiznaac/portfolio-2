// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { IncomeQuery, Income as IncomeRecord, Month } from "../api/types.ts";
import { AnalysisWindowProvider } from "../hooks/useAnalysisWindow.tsx";
import { formatCurrency } from "../lib/format.ts";
import { Income as IncomePage } from "./Income.tsx";

const recorded = vi.hoisted(() => ({
  calls: [] as { query: IncomeQuery; enabled: boolean }[],
}));

const mock = vi.hoisted(() => ({
  income: [] as IncomeRecord[],
  filtered: [] as IncomeRecord[],
  closed: [] as Month[],
}));

vi.mock("../api/queries.ts", () => ({
  useIncome: (query: IncomeQuery = {}, enabled = true) => {
    recorded.calls.push({ query, enabled });
    const narrowed = query.ticker !== undefined || query.status !== undefined;
    return {
      data: narrowed ? mock.filtered : mock.income,
      isLoading: false,
      isError: false,
      error: null,
    };
  },
  useClosedMonths: () => ({ data: mock.closed, isLoading: false, isError: false }),
  useImportIncome: () => ({
    mutate: () => undefined,
    isPending: false,
    isSuccess: false,
    data: undefined,
    error: null,
  }),
}));

function income(overrides: Partial<IncomeRecord>): IncomeRecord {
  return {
    id: 1,
    ticker: "PETR4",
    date: "2026-11-10",
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

const closedMonth: Month = {
  id: 9,
  period: "2026-11",
  capital: 100000,
  floor: 0,
  status: "CLOSED",
  aportes: 0,
};

const ledgerMovements: IncomeRecord[] = [
  income({ id: 1, ticker: "PETR4", date: "2026-11-10", net_amount: 10 }),
  income({ id: 2, ticker: "PETR3", date: "2026-10-10", net_amount: 20 }),
  income({ id: 3, ticker: "VALE3", date: "2026-11-30", net_amount: 30 }),
  income({ id: 4, ticker: "VALE3", date: "2026-12-10", status: "PAYABLE", net_amount: 40 }),
  income({ id: 5, ticker: "PETR4", date: "2026-11-20", status: "ANNOUNCED", net_amount: 5 }),
  income({ id: 6, ticker: "ITSA4", date: "2026-12-01", net_amount: 99 }),
];

function renderIncome(entry = "/analysis/income") {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[entry]}>
        <AnalysisWindowProvider>
          <IncomePage />
        </AnalysisWindowProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function ledgerRows() {
  return within(screen.getByRole("table")).getAllByRole("row");
}

beforeEach(() => {
  mock.income = [];
  mock.filtered = [];
  mock.closed = [closedMonth];
  recorded.calls = [];
});

afterEach(cleanup);

describe("Análise · Proventos", () => {
  it("renders the window KPIs and chart, then the receivable rows before the window", async () => {
    mock.income = ledgerMovements;
    renderIncome();

    expect((await screen.findByTestId("income-received-kpi")).textContent).toBe(formatCurrency(60));
    expect(screen.getByTestId("income-to-receive-kpi").textContent).toBe(formatCurrency(45));
    expect(screen.getByTestId("income-next-kpi").textContent).toBe("PETR4");
    expect(screen.getByTestId("income-monthly-kpi").textContent).toBe(formatCurrency(5));
    expect(screen.getByTestId("income-chart")).toBeDefined();
    expect(screen.getByTestId("income-bar-2026-11")).toBeDefined();

    const rows = ledgerRows();
    expect(rows).toHaveLength(6);
    expect(within(rows[1] as HTMLElement).getByText("PETR4")).toBeDefined();
    expect(within(rows[1] as HTMLElement).getByText("Anunciado")).toBeDefined();
    expect(within(rows[2] as HTMLElement).getByText("VALE3")).toBeDefined();
    expect(within(rows[2] as HTMLElement).getByText("fora do recorte")).toBeDefined();
    expect(screen.getByTestId("outside-window-4")).toBeDefined();
    expect(within(rows[3] as HTMLElement).getByText("VALE3")).toBeDefined();
    expect(within(rows[4] as HTMLElement).getByText("PETR4")).toBeDefined();
    expect(within(rows[5] as HTMLElement).getByText("PETR3")).toBeDefined();
  });

  it("drops a settled payment that falls one day past the window boundary", async () => {
    mock.income = ledgerMovements;
    renderIncome();

    await screen.findByTestId("income-received-kpi");
    expect(within(screen.getByRole("table")).queryByText("ITSA4")).toBeNull();
  });

  it("narrows the ledger through the income query and leaves the KPIs and rail untouched", async () => {
    mock.income = ledgerMovements;
    mock.filtered = [income({ id: 1, ticker: "PETR4", date: "2026-11-10", net_amount: 10 })];
    const user = userEvent.setup();
    renderIncome();

    await screen.findByTestId("income-received-kpi");
    expect(screen.getByTestId("rail-window").textContent).toBe("dez/2025 – nov/2026");

    const ticker = screen.getByLabelText("Ativo");
    await user.type(ticker, "petr");

    expect(screen.getByText("2 de 5")).toBeDefined();
    expect(screen.getByTestId("income-received-kpi").textContent).toBe(formatCurrency(60));
    expect(screen.getByTestId("income-to-receive-kpi").textContent).toBe(formatCurrency(45));
    expect(screen.getByTestId("rail-window").textContent).toBe("dez/2025 – nov/2026");
    expect(document.activeElement).toBe(ticker);

    const narrowed = recorded.calls.filter((call) => call.enabled).at(-1);
    expect(narrowed?.query).toEqual({
      from: "2025-12-01",
      to: "2026-11-30",
      ticker: "petr",
      status: undefined,
    });
  });

  it("opens a deep-linked row in the right window and highlights it", async () => {
    mock.income = ledgerMovements;
    renderIncome("/analysis/income?focus=4");

    expect(await screen.findByText("dez/2026 é o mês-âncora")).toBeDefined();
    expect(screen.getByTestId("rail-window").textContent).toBe("jan/2026 – dez/2026");

    const alert = screen.getByTestId("income-focus-chip");
    const row = alert.closest("tr");
    expect(row).not.toBeNull();
    expect(within(row as HTMLElement).getByText("VALE3")).toBeDefined();
    expect((row as HTMLElement).className).toContain("bg-attention-bg");
  });
});
