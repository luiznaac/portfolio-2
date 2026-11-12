// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Link, MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { Trade } from "../api/types.ts";
import { Trades } from "./Trades.tsx";

const mock = vi.hoisted(() => ({ trades: [] as Trade[] }));

vi.mock("../api/client.ts", () => ({
  api: {
    getTrades: () => Promise.resolve(mock.trades),
    getImports: () => Promise.resolve([]),
    importOriginalUrl: (id: number) => `/api/imports/${id}/file`,
  },
}));

function trade(overrides: Partial<Trade>): Trade {
  return {
    id: 1,
    ticker: "PETR4",
    date: "2026-10-01",
    venue: "VISTA",
    side: "BUY",
    quantity: 100,
    unit_price: 48.92,
    institution_id: 7,
    source: "B3_IMPORT",
    confirmed_by_b3: false,
    ordinal: 1,
    absorbed_quantity: 0,
    ...overrides,
  };
}

function LocationProbe() {
  const location = useLocation();
  return <p data-testid="location-search">{location.search}</p>;
}

function renderTrades() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={["/ativos/trades"]}>
        <LocationProbe />
        <nav>
          <Link to="/ativos/trades">Negócios</Link>
          <Link to="/outra">Outra página</Link>
        </nav>
        <Routes>
          <Route path="/ativos/trades" element={<Trades />} />
          <Route path="/outra" element={<p>outra página</p>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function ledgerRows() {
  return within(screen.getByRole("table")).getAllByRole("row");
}

afterEach(() => {
  cleanup();
  mock.trades = [];
});

describe("Ativos · Negócios filters", () => {
  it("narrows the host table while the bar keeps the caret and the URL stays clean", async () => {
    mock.trades = [
      trade({ id: 1, ticker: "PETR4", date: "2026-10-01", side: "BUY" }),
      trade({ id: 2, ticker: "VALE3", date: "2026-10-15", side: "SELL" }),
      trade({ id: 3, ticker: "PETR3", date: "2026-10-20", side: "SELL" }),
    ];
    const user = userEvent.setup();
    renderTrades();

    expect(await screen.findByText("3 de 3")).toBeDefined();
    expect(ledgerRows()).toHaveLength(4);
    expect(screen.queryByRole("button", { name: "limpar" })).toBeNull();

    const ticker = screen.getByLabelText("Ticker");
    await user.type(ticker, "petr");

    expect(screen.getByText("2 de 3")).toBeDefined();
    expect(ledgerRows()).toHaveLength(3);
    expect((ticker as HTMLInputElement).value).toBe("petr");
    expect(document.activeElement).toBe(ticker);
    expect(screen.getByTestId("location-search").textContent).toBe("");
    expect(screen.getByRole("button", { name: "limpar" })).toBeDefined();
  });

  it("clears every field when the page is left and re-entered", async () => {
    mock.trades = [
      trade({ id: 1, ticker: "PETR4", side: "BUY" }),
      trade({ id: 2, ticker: "VALE3", side: "SELL" }),
    ];
    const user = userEvent.setup();
    renderTrades();

    const ticker = await screen.findByLabelText("Ticker");
    await user.type(ticker, "vale");
    await user.selectOptions(screen.getByLabelText("Lado"), "SELL");
    expect(screen.getByText("1 de 2")).toBeDefined();

    await user.click(screen.getByRole("link", { name: "Outra página" }));
    expect(screen.getByText("outra página")).toBeDefined();

    await user.click(screen.getByRole("link", { name: "Negócios" }));
    expect(await screen.findByText("2 de 2")).toBeDefined();
    expect((screen.getByLabelText("Ticker") as HTMLInputElement).value).toBe("");
    expect((screen.getByLabelText("Lado") as HTMLSelectElement).value).toBe("");
  });

  it("offers the same clear action from the narrowing empty state", async () => {
    mock.trades = [trade({ id: 1, ticker: "PETR4" })];
    const user = userEvent.setup();
    renderTrades();

    await user.type(await screen.findByLabelText("Ticker"), "zzz");

    expect(screen.getByText("Nenhuma linha com esse filtro.")).toBeDefined();
    expect(screen.queryByRole("table")).toBeNull();

    await user.click(screen.getAllByRole("button", { name: "limpar" })[1]);
    expect(await screen.findByText("1 de 1")).toBeDefined();
    expect(ledgerRows()).toHaveLength(2);
  });
});
