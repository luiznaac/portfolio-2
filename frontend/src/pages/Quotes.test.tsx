// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { PortfolioStatus, Position, Quote, TickerRefreshResult } from "../api/types.ts";
import { Quotes } from "./Quotes.tsx";

const mock = vi.hoisted(() => ({
  positions: [] as Position[],
  quotes: new Map<string, Quote[]>(),
  status: null as PortfolioStatus | null,
  refresh: null as TickerRefreshResult | null,
  refreshed: 0,
}));

vi.mock("../api/client.ts", () => ({
  ApiError: class ApiError extends Error {
    constructor(readonly status: number) {
      super(`HTTP ${status}`);
    }
  },
  api: {
    getPositions: () => Promise.resolve(mock.positions),
    getTickerQuotes: (ticker: string) =>
      ticker === "ERRO3"
        ? Promise.reject(new Error("leitura falhou"))
        : Promise.resolve(mock.quotes.get(ticker) ?? []),
    getPortfolioStatus: () => Promise.resolve(mock.status),
    refreshCatalog: () => {
      mock.refreshed += 1;
      return Promise.resolve(mock.refresh);
    },
  },
}));

function quote(ticker: string, date: string, close: number): Quote {
  return { ticker, date, close, source: "COTAHIST" };
}

function status(): PortfolioStatus {
  return {
    as_of: "2026-11-02",
    capital: 100_000,
    invested: 70_000,
    deficit: 30_000,
    drift: 2.5,
    month: {
      open: false,
      steps_done: 0,
      steps_total: 6,
      suggested_orders: 0,
      pending_transfers: 0,
    },
    data: {
      quotes_as_of: "2026-10-30",
      stale_quotes: [{ ticker: "VALE3", date: "2026-10-24", close: 61.2, days_old: 6 }],
      open_gaps: 0,
    },
    attention: [],
  };
}

function renderQuotes() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <Quotes />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  mock.positions = [];
  mock.quotes = new Map();
  mock.status = status();
  mock.refresh = null;
  mock.refreshed = 0;
});

afterEach(cleanup);

describe("Ativos · Cotações", () => {
  it("renders each held ticker's last close, date, source and freshness", async () => {
    mock.positions = [
      { ticker: "PETR4", quantity: 100, average_price: 30 },
      { ticker: "VALE3", quantity: 50, average_price: 52 },
      { ticker: "NVDC34", quantity: 10, average_price: 20 },
    ];
    mock.quotes = new Map([
      ["PETR4", [quote("PETR4", "2026-10-29", 38), quote("PETR4", "2026-10-30", 39)]],
      ["VALE3", [quote("VALE3", "2026-10-24", 61.2)]],
    ]);
    renderQuotes();

    const table = await screen.findByTestId("quotes-table");
    const petr = within(table).getByText("PETR4").closest("tr") as HTMLElement;
    expect(within(petr).getByText("30/10/2026")).toBeDefined();
    expect(within(petr).getByText("R$ 39,00")).toBeDefined();
    expect(within(petr).getByText("COTAHIST")).toBeDefined();
    expect(within(petr).getByText("ok")).toBeDefined();

    const vale = within(table).getByText("VALE3").closest("tr") as HTMLElement;
    expect(within(vale).getByText("24/10/2026")).toBeDefined();
    expect(within(vale).getByText("desatualizada · há 6 dias")).toBeDefined();

    const unpriced = within(table).getByText("NVDC34").closest("tr") as HTMLElement;
    expect(within(unpriced).getByText("sem cotação")).toBeDefined();
    expect(within(unpriced).getAllByText("—").length).toBeGreaterThan(0);
  });

  it("names a failed quote read instead of calling the ticker unpriced", async () => {
    mock.positions = [{ ticker: "ERRO3", quantity: 10, average_price: 20 }];
    renderQuotes();

    const table = await screen.findByTestId("quotes-table");
    const row = within(table).getByText("ERRO3").closest("tr") as HTMLElement;
    expect(within(row).getByText("erro na leitura")).toBeDefined();
    expect(within(row).queryByText("sem cotação")).toBeNull();
  });

  it("hosts the catalogue and quote refresh and reports what it wrote", async () => {
    mock.positions = [{ ticker: "PETR4", quantity: 100, average_price: 30 }];
    mock.quotes = new Map([["PETR4", [quote("PETR4", "2026-10-30", 39)]]]);
    mock.refresh = {
      catalog: 412,
      quotes: 11,
      as_of: "2026-10-30",
      failed: [{ kind: "TICKER", ticker: "NVDC34", reason: "Sem preço no arquivo COTAHIST" }],
      unclassified: [],
    };
    const user = userEvent.setup();
    renderQuotes();

    await user.click(await screen.findByTestId("quotes-refresh"));

    await waitFor(() => expect(mock.refreshed).toBe(1));
    const report = await screen.findByTestId("catalog-refresh-report");
    expect(report.textContent).toContain("Catálogo: 412 · Cotações: 11 · Fechamento: 30/10/2026");
    expect(screen.getByTestId("catalog-refresh-failures").textContent).toContain(
      "NVDC34: Sem preço no arquivo COTAHIST",
    );
  });

  it("says the portfolio has no tickers to quote", async () => {
    renderQuotes();

    expect(await screen.findByText("Nenhuma posição na carteira.")).toBeDefined();
  });
});
