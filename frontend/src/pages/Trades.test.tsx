// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Link, MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type {
  Allocation,
  BackfillReport,
  OpeningSeedCreation,
  PositionReconciliation,
  Quote,
  Trade,
  TradeLedgerPage,
} from "../api/types.ts";
import { Trades } from "./Trades.tsx";

const mock = vi.hoisted(() => ({
  trades: [] as Trade[],
  ledgerQueries: [] as Array<Record<string, string | undefined>>,
  allocation: null as Allocation | null,
  quotes: {} as Record<string, Quote[]>,
  seeds: [] as OpeningSeedCreation[],
  backfillFile: null as File | null,
  backfillRuns: 0,
  report: {
    absorbed_seeds: 1,
    adjusted_seeds: 0,
    gaps_created: 2,
    gaps_replaced: 1,
    positions_reprojected: 3,
  } as BackfillReport | null,
  custodyFile: null as File | null,
  reconcile: null as PositionReconciliation[] | null,
}));

// The ledger endpoint's own contract: the filter narrows the dataset and the cursor pages it. The
// mock keeps the page size at two so the cursor loop is visible in a three-row ledger.
vi.mock("../api/client.ts", () => ({
  ApiError: class ApiError extends Error {
    constructor(readonly status: number) {
      super(`HTTP ${status}`);
    }
  },
  api: {
    getTradeLedger: (query: Record<string, string | undefined>): Promise<TradeLedgerPage> => {
      mock.ledgerQueries.push(query);
      const filtered = mock.trades.filter(
        (trade) =>
          (query.ticker === undefined ||
            trade.ticker.toLowerCase().includes(query.ticker.toLowerCase())) &&
          (query.from === undefined || trade.date >= query.from) &&
          (query.to === undefined || trade.date <= query.to) &&
          (query.side === undefined || trade.side === query.side),
      );
      const start = query.cursor === undefined ? 0 : Number(query.cursor);
      const rows = filtered.slice(start, start + 2);
      const next = start + rows.length < filtered.length ? String(start + rows.length) : null;
      const financialTotal = filtered.reduce(
        (sum, trade) => sum + trade.quantity * trade.unit_price,
        0,
      );
      return Promise.resolve({
        rows,
        next_cursor: next,
        total: filtered.length,
        financial_total: financialTotal,
      });
    },
    getTrades: () => Promise.resolve(mock.trades),
    getCurrentAllocation: () => Promise.resolve(mock.allocation),
    getTickerQuotes: (ticker: string) => Promise.resolve(mock.quotes[ticker] ?? []),
    seedOpenings: (seeds: OpeningSeedCreation[]) => {
      mock.seeds = seeds;
      return Promise.resolve(mock.trades);
    },
    backfill: (file?: File) => {
      mock.backfillFile = file ?? null;
      mock.backfillRuns += 1;
      return Promise.resolve(mock.report);
    },
    reconcileCustody: (file: File) => {
      mock.custodyFile = file;
      return Promise.resolve(mock.reconcile ?? []);
    },
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

function allocationWith(negative: Array<{ ticker: string; quantity: number }>): Allocation {
  return {
    capital: 1000,
    floor: 0,
    ideal: 0,
    actual: 0,
    deficit: 0,
    drift: 0,
    unattributed: 0,
    negative,
    unpriced: [],
    unclassified: { actual: 0, tickers: [] },
    classes: [],
  };
}

function quote(date: string, close: number): Quote {
  return { ticker: "PETR4", date, close, source: "COTAHIST" };
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

beforeEach(() => {
  mock.trades = [];
  mock.ledgerQueries = [];
  mock.allocation = allocationWith([]);
  mock.quotes = {};
  mock.seeds = [];
  mock.backfillFile = null;
  mock.backfillRuns = 0;
  mock.custodyFile = null;
  mock.reconcile = null;
});

afterEach(cleanup);

describe("Ativos · Negócios ledger", () => {
  it("renders the ledger columns and narrows the request, count and total through the filter bar", async () => {
    mock.trades = [
      trade({ id: 1, ticker: "PETR4", date: "2026-10-01", side: "BUY" }),
      trade({
        id: 2,
        ticker: "VALE3",
        date: "2026-10-15",
        side: "SELL",
        quantity: 10,
        unit_price: 60,
      }),
      trade({
        id: 3,
        ticker: "PETR3",
        date: "2026-10-20",
        side: "SELL",
        quantity: 5,
        unit_price: 40,
      }),
      trade({
        id: 4,
        ticker: "ITUB4",
        date: "2026-10-25",
        side: "BUY",
        quantity: 1,
        unit_price: 30,
      }),
    ];
    const user = userEvent.setup();
    renderTrades();

    expect(await screen.findByText("2 de 4")).toBeDefined();
    expect(screen.getByRole("columnheader", { name: "Financeiro" })).toBeDefined();
    expect(screen.getByTestId("ledger-total").textContent).toContain("Total filtrado");
    expect(screen.getByTestId("ledger-total").textContent).not.toContain("parcial");
    expect(screen.getByTestId("ledger-total-value").textContent).toBe("R$ 5.722,00");

    const ticker = screen.getByLabelText("Ticker");
    await user.type(ticker, "petr");

    expect(await screen.findByText("2 de 2")).toBeDefined();
    expect(screen.getByTestId("ledger-total-value").textContent).toBe("R$ 5.092,00");
    expect((ticker as HTMLInputElement).value).toBe("petr");
    await vi.waitFor(() =>
      expect(mock.ledgerQueries.map((query) => query.ticker)).toContain("petr"),
    );
    expect(document.activeElement).toBe(ticker);
    expect(screen.getByTestId("location-search").textContent).toBe("");
  });

  it("pages the ledger with the cursor and keeps the filtered total", async () => {
    mock.trades = [
      trade({ id: 1, ticker: "PETR4", date: "2026-10-01" }),
      trade({ id: 2, ticker: "VALE3", date: "2026-10-02" }),
      trade({ id: 3, ticker: "ITUB4", date: "2026-10-03" }),
    ];
    const user = userEvent.setup();
    renderTrades();

    expect(await screen.findByText("2 de 3")).toBeDefined();
    expect(ledgerRows()).toHaveLength(4);

    await user.click(screen.getByRole("button", { name: "Carregar mais" }));

    expect(await screen.findByText("3 de 3")).toBeDefined();
    expect(ledgerRows()).toHaveLength(5);
    expect(mock.ledgerQueries.at(-1)?.cursor).toBe("2");
  });

  it("clears a filter back to the first page instead of restoring the pages loaded before it", async () => {
    mock.trades = [
      trade({ id: 1, ticker: "PETR4", date: "2026-10-01" }),
      trade({ id: 2, ticker: "VALE3", date: "2026-10-02" }),
      trade({ id: 3, ticker: "PETR4", date: "2026-10-03" }),
      trade({ id: 4, ticker: "VALE3", date: "2026-10-04" }),
    ];
    const user = userEvent.setup();
    renderTrades();

    expect(await screen.findByText("2 de 4")).toBeDefined();
    await user.click(screen.getByRole("button", { name: "Carregar mais" }));
    expect(await screen.findByText("4 de 4")).toBeDefined();

    await user.type(screen.getByLabelText("Ticker"), "petr");
    expect(await screen.findByText("2 de 2")).toBeDefined();

    await user.click(screen.getByRole("button", { name: "limpar" }));
    expect(await screen.findByText("2 de 4")).toBeDefined();
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
    expect(await screen.findByText("1 de 1")).toBeDefined();

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

    expect(await screen.findByText("Nenhuma linha com esse filtro.")).toBeDefined();

    await user.click(screen.getAllByRole("button", { name: "limpar" })[1]);
    expect(await screen.findByText("1 de 1")).toBeDefined();
  });

  it("renders the empty state when the ledger has no trades at all", async () => {
    renderTrades();

    expect(await screen.findByText("Nenhum negócio encontrado.")).toBeDefined();
  });
});

describe("Ativos · Negócios · Reconstruir histórico", () => {
  it("lists a negative position as an adoption row with the default date and latest close", async () => {
    mock.allocation = allocationWith([{ ticker: "PETR4", quantity: -10 }]);
    mock.trades = [
      trade({ id: 2, date: "2026-01-10" }),
      trade({ id: 1, date: "2025-12-01", source: "OPENING" }),
    ];
    mock.quotes = { PETR4: [quote("2026-01-31", 9), quote("2026-02-27", 12)] };
    renderTrades();

    expect(await screen.findByTestId("adoption-quantity-PETR4")).toHaveProperty("value", "10");
    expect(screen.getByTestId("adoption-date-PETR4")).toHaveProperty("value", "2026-01-09");
    expect(screen.getByTestId("adoption-price-PETR4")).toHaveProperty("value", "12");
  });

  it("posts one opening seed per adoption row and unlocks the replay on save", async () => {
    mock.allocation = allocationWith([{ ticker: "PETR4", quantity: -10 }]);
    mock.trades = [
      trade({ id: 2, date: "2026-01-10" }),
      trade({ id: 1, date: "2025-12-01", source: "OPENING" }),
    ];
    mock.quotes = { PETR4: [quote("2026-02-27", 12)] };
    const user = userEvent.setup();
    renderTrades();

    expect(await screen.findByRole("button", { name: "Reconstruir histórico" })).toHaveProperty(
      "disabled",
      true,
    );

    await user.click(screen.getByRole("button", { name: "Salvar adoção" }));

    expect(mock.seeds).toEqual([
      { ticker: "PETR4", quantity: 10, unit_price: 12, date: "2026-01-09" },
    ]);
    expect(await screen.findByRole("button", { name: "Reconstruir histórico" })).toHaveProperty(
      "disabled",
      false,
    );
  });

  it("runs the backfill without a file and renders what it absorbed, created and reprojected", async () => {
    const user = userEvent.setup();
    renderTrades();
    await user.click(await screen.findByRole("button", { name: "Pular adoção" }));

    await user.click(screen.getByRole("button", { name: "Reconstruir histórico" }));

    expect(mock.backfillRuns).toBe(1);
    expect(mock.backfillFile).toBeNull();
    expect((await screen.findByTestId("backfill-report")).textContent).toContain(
      "1 seeds absorvidos",
    );
    expect(screen.getByTestId("backfill-report").textContent).toContain("2 lacunas criadas");
    expect(screen.getByTestId("backfill-report").textContent).toContain("3 posições reprojetadas");
  });

  it("renders custody drift from an upload and persists nothing", async () => {
    mock.reconcile = [
      { ticker: "PETR4", custody_quantity: 90, ledger_quantity: 100, has_drift: true },
    ];
    const user = userEvent.setup();
    renderTrades();

    await user.upload(await screen.findByLabelText("Posição"), new File(["sheet"], "posicao.xlsx"));
    await user.click(screen.getByRole("button", { name: "Conferir custódia" }));

    expect(mock.custodyFile?.name).toBe("posicao.xlsx");
    const table = await screen.findByTestId("custody-drift");
    expect(within(table).getByText("PETR4")).toBeDefined();
    expect(within(table).getByText("90")).toBeDefined();
  });
});
