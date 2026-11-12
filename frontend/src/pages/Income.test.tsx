// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { Income } from "../api/types.ts";
import { formatCurrency } from "../lib/format.ts";
import { Income as IncomePage } from "./Income.tsx";

const mock = vi.hoisted(() => ({
  income: [] as Income[],
  upcoming: [] as Income[],
}));

vi.mock("../api/client.ts", () => ({
  api: {
    getIncome: () => Promise.resolve(mock.income),
    getUpcomingIncome: () => Promise.resolve(mock.upcoming),
    importIncome: () => Promise.resolve([]),
  },
}));

function income(overrides: Partial<Income>): Income {
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

function renderIncome() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={["/income"]}>
        <IncomePage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function ledgerRows() {
  return within(screen.getByRole("table")).getAllByRole("row");
}

function forecastPanel() {
  return screen
    .getByRole("heading", { name: "Proventos previstos" })
    .closest("section") as HTMLElement;
}

afterEach(() => {
  cleanup();
  mock.income = [];
  mock.upcoming = [];
});

describe("Análise · Proventos filters", () => {
  it("narrows the ledger without touching the forecast read above it", async () => {
    mock.income = [
      income({ id: 1, ticker: "PETR4", status: "RECEIVED", net_amount: 10 }),
      income({ id: 2, ticker: "VALE3", status: "PAYABLE", net_amount: 20 }),
      income({ id: 3, ticker: "PETR3", status: "RECEIVED", net_amount: 30 }),
    ];
    mock.upcoming = [
      income({ id: 4, ticker: "VALE3", status: "PAYABLE", net_amount: 20 }),
      income({ id: 5, ticker: "PETR4", status: "PAYABLE", net_amount: 5 }),
    ];
    const user = userEvent.setup();
    renderIncome();

    expect(await screen.findByText("3 de 3")).toBeDefined();
    expect(within(forecastPanel()).getByText(formatCurrency(25))).toBeDefined();
    expect(within(forecastPanel()).getByText("2 pagamentos a receber")).toBeDefined();

    const ticker = screen.getByLabelText("Ativo");
    await user.type(ticker, "petr");

    expect(screen.getByText("2 de 3")).toBeDefined();
    expect(ledgerRows()).toHaveLength(3);
    expect(document.activeElement).toBe(ticker);
    expect(within(forecastPanel()).getByText(formatCurrency(25))).toBeDefined();
    expect(within(forecastPanel()).getByText("2 pagamentos a receber")).toBeDefined();
  });

  it("filters by lifecycle state and reaches the narrowing empty state", async () => {
    mock.income = [
      income({ id: 1, ticker: "PETR4", status: "RECEIVED" }),
      income({ id: 2, ticker: "VALE3", status: "PAYABLE" }),
      income({ id: 3, ticker: "PETR3", status: "RECEIVED" }),
    ];
    const user = userEvent.setup();
    renderIncome();

    await screen.findByText("3 de 3");
    const status = screen.getByLabelText("Situação");

    await user.selectOptions(status, "RECEIVED");
    expect(screen.getByText("2 de 3")).toBeDefined();
    expect(ledgerRows()).toHaveLength(3);

    await user.selectOptions(status, "PAYABLE");
    expect(screen.getByText("1 de 3")).toBeDefined();
    expect(within(screen.getByRole("table")).getByText("VALE3")).toBeDefined();

    await user.type(screen.getByLabelText("Ativo"), "petr");
    expect(screen.getByText("0 de 3")).toBeDefined();
    expect(screen.getByText("Nenhuma linha com esse filtro.")).toBeDefined();

    const clears = screen.getAllByRole("button", { name: "limpar" });
    await user.click(clears[0]);
    expect(await screen.findByText("3 de 3")).toBeDefined();
    expect((screen.getByLabelText("Situação") as HTMLSelectElement).value).toBe("");
    expect((screen.getByLabelText("Ativo") as HTMLInputElement).value).toBe("");
  });
});
