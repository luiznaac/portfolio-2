// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import type {
  Allocation,
  AssetClass,
  Attribution,
  CatalogEntry,
  ClassificationQueueItem,
  Gap,
  Position,
  Quote,
  Strategy,
  Trade,
} from "../api/types.ts";
import { Positions } from "./Positions.tsx";

const mock = vi.hoisted(() => ({
  positions: [] as Position[],
  allocation: null as Allocation | null,
  gaps: [] as Gap[],
  catalog: undefined as CatalogEntry | undefined,
  trades: [] as Trade[],
  quotes: [] as Quote[],
  attribution: null as Attribution | null,
  strategies: [] as Strategy[],
  classified: [] as Array<[string, AssetClass]>,
  reverted: [] as string[],
  queue: [] as ClassificationQueueItem[],
  batch: [] as Array<Record<string, AssetClass>>,
}));

vi.mock("../api/client.ts", () => ({
  ApiError: class ApiError extends Error {
    constructor(readonly status: number) {
      super(`HTTP ${status}`);
    }
  },
  api: {
    getPositions: () => Promise.resolve(mock.positions),
    getCurrentAllocation: () => Promise.resolve(mock.allocation),
    getGaps: () => Promise.resolve(mock.gaps),
    getPosition: (ticker: string) =>
      Promise.resolve({
        position: mock.positions.find((position) => position.ticker === ticker) ?? {
          ticker,
          quantity: 0,
          average_price: 0,
        },
        trades: mock.trades,
      }),
    getAttribution: () => Promise.resolve(mock.attribution),
    getStrategies: () => Promise.resolve(mock.strategies),
    getTickerQuotes: () => Promise.resolve(mock.quotes),
    getTicker: () =>
      mock.catalog === undefined
        ? Promise.reject(Object.assign(new Error("HTTP 404"), { status: 404 }))
        : Promise.resolve(mock.catalog),
    setAttribution: () => Promise.resolve(null),
    removeAttribution: () => Promise.resolve(null),
    classifyTicker: (ticker: string, assetClass: AssetClass) => {
      mock.classified.push([ticker, assetClass]);
      return Promise.resolve({ ...catalogEntry(ticker), asset_class_override: assetClass });
    },
    revertTickerClassification: (ticker: string) => {
      mock.reverted.push(ticker);
      return Promise.resolve(catalogEntry(ticker));
    },
    refreshCatalog: () => Promise.resolve({ catalog: 1, quotes: 1 }),
    getClassificationQueue: () => Promise.resolve(mock.queue),
    classifyTickers: (classifications: Record<string, AssetClass>) => {
      mock.batch.push(classifications);
      return Promise.resolve([]);
    },
  },
}));

function catalogEntry(ticker: string): CatalogEntry {
  return {
    ticker,
    short_name: ticker.slice(0, 4),
    bdi_type: "??",
    asset_kind: "UNKNOWN",
    first_seen: "2026-10-01",
    last_seen: "2026-10-01",
  };
}

function position(ticker: string, quantity: number, average_price: number): Position {
  return { ticker, quantity, average_price };
}

function gap(ticker: string, status: Gap["status"]): Gap {
  return {
    id: 1,
    ticker,
    date: "2026-10-01",
    quantity: 1,
    status,
    trade_watermark: 0,
    action_watermark: 0,
  };
}

function trade(): Trade {
  return {
    id: 1,
    ticker: "PETR4",
    date: "2026-10-02",
    venue: "VISTA",
    side: "BUY",
    quantity: 100,
    unit_price: 30,
    institution_id: 7,
    source: "B3_IMPORT",
    confirmed_by_b3: false,
    ordinal: 1,
    absorbed_quantity: 0,
  };
}

function quote(date: string, close: number): Quote {
  return { ticker: "PETR4", date, close, source: "COTAHIST" };
}

function allocation(): Allocation {
  return {
    period: "2026-10",
    capital: 10_000,
    floor: 0,
    ideal: 10_000,
    actual: 4_150,
    deficit: 0,
    drift: 0,
    unattributed: 150,
    unclassified: {
      actual: 150,
      tickers: [
        { ticker: "ZZZZ9", quantity: 10, actual: 150, price: 15, quote_date: "2026-10-13" },
      ],
    },
    classes: [
      {
        asset_class: "ACOES",
        target: 50,
        ideal: 5_000,
        actual: 4_000,
        deficit: 0,
        drift: 0,
        unattributed: 0,
        sub_classes: [],
        outside: { actual: 0, tickers: [] },
        strategies: [
          {
            strategy: "Top Dividendos",
            target: 50,
            ideal: 5_000,
            actual: 4_000,
            deficit: 0,
            drift: 0,
            tickers: [
              {
                ticker: "PETR4",
                quantity: 100,
                target: 50,
                ideal: 5_000,
                actual: 4_000,
                price: 40,
                quote_date: "2026-09-01",
                stale: true,
              },
              { ticker: "VALE3", quantity: 10, target: 50, ideal: 5_000 },
            ],
          },
        ],
      },
    ],
  };
}

function attribution(): Attribution {
  return {
    ticker: "PETR4",
    custody_quantity: 100,
    attributed_quantity: 100,
    unattributed_quantity: 0,
    slices: [{ strategy_id: 1, strategy_name: "Top Dividendos", quantity: 100 }],
  };
}

function LocationProbe() {
  const location = useLocation();
  return <p data-testid="location-search">{location.search}</p>;
}

function renderPositions(path = "/ativos/positions") {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <LocationProbe />
        <Routes>
          <Route path="/ativos/positions/:ticker?" element={<Positions />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function rowFor(ticker: string): HTMLElement {
  const table = screen.getByTestId("positions-table");
  const row = within(table).getByText(ticker).closest("tr");
  if (!row) throw new Error(`no row for ${ticker}`);
  return row;
}

function seedPositions() {
  mock.positions = [
    position("PETR4", 100, 30),
    position("VALE3", 10, 60),
    position("ZZZZ9", 10, 12),
  ];
  mock.allocation = allocation();
  mock.gaps = [gap("PETR4", "OPEN"), gap("VALE3", "REPLACED")];
  mock.attribution = attribution();
  mock.strategies = [
    { id: 1, name: "Top Dividendos", asset_class: "ACOES", kind: "REPORT_DRIVEN" },
  ];
  mock.trades = [trade()];
  mock.quotes = [quote("2026-10-13", 40), quote("2026-10-10", 39)];
}

afterEach(() => {
  cleanup();
  mock.positions = [];
  mock.allocation = null;
  mock.gaps = [];
  mock.catalog = undefined;
  mock.trades = [];
  mock.quotes = [];
  mock.attribution = null;
  mock.strategies = [];
  mock.classified = [];
  mock.reverted = [];
  mock.queue = [];
  mock.batch = [];
});

describe("Ativos · Posições", () => {
  it("renders the custody table with class, close, value, chips and a total row", async () => {
    seedPositions();
    renderPositions();

    expect(await screen.findByText("3 de 3")).toBeDefined();
    expect(within(rowFor("PETR4")).getByText("Ações")).toBeDefined();
    expect(within(rowFor("PETR4")).getByText(/desatualizada/)).toBeDefined();
    expect(within(rowFor("PETR4")).getByText(/1 lacuna/)).toBeDefined();
    expect(within(rowFor("VALE3")).getAllByText("—").length).toBeGreaterThan(0);
    expect(within(rowFor("ZZZZ9")).getByText("Sem classe")).toBeDefined();

    const total = screen.getByTestId("positions-total");
    expect(total.textContent).toContain("R$ 4.150,00");
    expect(total.textContent).toContain("parcial");
  });

  it("narrows only the table and the total, keeping the URL clean", async () => {
    seedPositions();
    renderPositions();
    const user = userEvent.setup();

    await screen.findByText("3 de 3");
    await user.type(screen.getByLabelText("Ticker"), "petr");

    expect(await screen.findByText("1 de 3")).toBeDefined();
    expect(within(screen.getByTestId("positions-table")).queryByText("VALE3")).toBeNull();
    expect(screen.getByTestId("positions-total").textContent).toContain("R$ 4.000,00");
    expect(screen.getByTestId("location-search").textContent).toBe("");

    await user.click(screen.getByRole("button", { name: "limpar" }));
    await screen.findByText("3 de 3");
  });

  it("narrows to unclassified tickers from the class filter and offers the classification actions", async () => {
    seedPositions();
    renderPositions();
    const user = userEvent.setup();

    await screen.findByText("3 de 3");
    await user.selectOptions(screen.getByLabelText("Classe"), "sem-classe");

    expect(await screen.findByText("1 de 3")).toBeDefined();
    expect(within(screen.getByTestId("positions-table")).getByText("ZZZZ9")).toBeDefined();
    expect(screen.getByRole("button", { name: "Classificar sem classe" })).toBeDefined();
  });

  it("shows the shared empty state when no row matches", async () => {
    seedPositions();
    renderPositions();
    const user = userEvent.setup();

    await screen.findByText("3 de 3");
    await user.selectOptions(screen.getByLabelText("Classe"), "FIIS");

    expect(await screen.findByText(/Nenhuma linha com esse filtro/)).toBeDefined();
  });

  it("opens the ticker drawer from a row with position, attribution, trades, quotes, gaps and classification", async () => {
    seedPositions();
    mock.catalog = { ...catalogEntry("PETR4"), asset_class: "ACOES" };
    renderPositions();
    const user = userEvent.setup();

    await screen.findByText("3 de 3");
    await user.click(rowFor("PETR4"));

    const drawer = await screen.findByRole("dialog", { name: "Detalhe de PETR4" });
    expect(within(drawer).getByText("Custódia")).toBeDefined();
    expect(within(drawer).getByText("Preço médio (fiscal)")).toBeDefined();
    expect(within(drawer).getByText("Atribuição")).toBeDefined();
    expect(within(drawer).getByText("Negócios recentes")).toBeDefined();
    expect(within(drawer).getByText("Cotações")).toBeDefined();
    expect(within(drawer).getByText("Lacunas")).toBeDefined();
    expect(within(drawer).getByText("Classificação")).toBeDefined();
    expect(within(drawer).getByText("Ações")).toBeDefined();
  });

  it("opens the drawer on a deep link", async () => {
    seedPositions();
    renderPositions("/ativos/positions/ZZZZ9");

    expect(await screen.findByRole("dialog", { name: "Detalhe de ZZZZ9" })).toBeDefined();
  });

  it("classifies a single unclassified ticker from the drawer", async () => {
    seedPositions();
    mock.catalog = catalogEntry("ZZZZ9");
    renderPositions("/ativos/positions/ZZZZ9");
    const user = userEvent.setup();

    const drawer = await screen.findByRole("dialog", { name: "Detalhe de ZZZZ9" });
    await user.click(within(drawer).getByRole("button", { name: "Classificar" }));
    await user.click(screen.getByRole("button", { name: "FIIs" }));

    expect(mock.classified).toEqual([["ZZZZ9", "FIIS"]]);
    expect(screen.queryByRole("dialog", { name: "Classificar ZZZZ9" })).toBeNull();
  });

  it("keeps the manual class and offers the revert in the drawer", async () => {
    seedPositions();
    mock.catalog = { ...catalogEntry("ZZZZ9"), asset_class: "ACOES", asset_class_override: "FIIS" };
    renderPositions("/ativos/positions/ZZZZ9");
    const user = userEvent.setup();

    const drawer = await screen.findByRole("dialog", { name: "Detalhe de ZZZZ9" });
    expect(
      within(drawer).getByText("classe manual: FII — o catálogo agora diz Ações"),
    ).toBeDefined();

    await user.click(within(drawer).getByRole("button", { name: "Reverter" }));
    expect(mock.reverted).toEqual(["ZZZZ9"]);
  });

  it("bulk classifies the chosen rows from the page action", async () => {
    seedPositions();
    mock.queue = [
      { ticker: "ZZZZ9", short_name: "ZZZZ", quantity: 10, reason: "UNHANDLED_BDI_CODE" },
    ];
    renderPositions();
    const user = userEvent.setup();

    await screen.findByText("3 de 3");
    await user.click(screen.getByRole("button", { name: "Classificar sem classe" }));

    const dialog = await screen.findByRole("dialog", { name: "Classificar tickers sem classe" });
    await user.click(within(dialog).getByRole("button", { name: "Classificar ZZZZ9 como FIIs" }));
    await user.click(within(dialog).getByRole("button", { name: /Salvar 1 classificação/ }));

    expect(mock.batch).toEqual([{ ZZZZ9: "FIIS" }]);
  });
});
