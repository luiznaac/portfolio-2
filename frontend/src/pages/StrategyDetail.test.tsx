// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import type {
  Allocation,
  Attribution,
  Edition,
  Month,
  MonthPerformance,
  Position,
  Strategy,
  StrategyHoldings,
} from "../api/types.ts";
import { StrategyDetail } from "./StrategyDetail.tsx";

const mock = vi.hoisted(() => ({
  strategies: [] as Strategy[],
  allocation: null as Allocation | null,
  editions: [] as Edition[],
  months: [] as Month[],
  performance: null as MonthPerformance | null,
  holdings: null as StrategyHoldings | null,
  positions: [] as Position[],
  attributions: {} as Record<string, Attribution>,
  promotions: [] as { referenceMonth: string; tickers: string[] }[],
}));

vi.mock("../api/client.ts", () => ({
  api: {
    getStrategies: () => Promise.resolve(mock.strategies),
    getCurrentAllocation: () => Promise.resolve(mock.allocation),
    getEditions: () => Promise.resolve(mock.editions),
    getClosedMonths: () => Promise.resolve(mock.months),
    getMonthPerformance: () => Promise.resolve(mock.performance),
    getStrategyHoldings: () => Promise.resolve(mock.holdings),
    getPositions: () => Promise.resolve(mock.positions),
    getAttribution: (ticker: string) => Promise.resolve(mock.attributions[ticker]),
    activateEdition: (
      _strategyId: number,
      activation: { reference_month: string; entries: { ticker: string }[] },
    ) => {
      mock.promotions.push({
        referenceMonth: activation.reference_month,
        tickers: activation.entries.map((entry) => entry.ticker),
      });
      mock.editions = mock.editions.map((edition) =>
        edition.reference_month === activation.reference_month
          ? { ...edition, status: "ACTIVE" as const }
          : edition,
      );
      const promoted = mock.editions.find(
        (edition) => edition.reference_month === activation.reference_month,
      );
      return Promise.resolve(promoted);
    },
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

const historicalEdition: Edition = {
  id: 3,
  strategy_id: 1,
  reference_month: "2026-08",
  status: "ACTIVE",
  entries: [
    { ticker: "PETR4", weight: 50, rating: "BUY", target_price: 58, do_not_trade: false },
    { ticker: "VALE3", weight: 50, rating: "NEUTRAL", target_price: 80, do_not_trade: false },
  ],
};

const activeEdition: Edition = {
  id: 1,
  strategy_id: 1,
  reference_month: "2026-09",
  status: "ACTIVE",
  entries: [
    { ticker: "PETR4", weight: 25, rating: "BUY", target_price: 60, do_not_trade: false },
    { ticker: "VALE3", weight: 25, rating: "NEUTRAL", target_price: 85, do_not_trade: false },
    { ticker: "ITUB4", weight: 20, rating: "BUY", target_price: 30, do_not_trade: false },
    { ticker: "EGIE3", weight: 20, rating: "BUY", target_price: 48, do_not_trade: false },
    { ticker: "B3SA3", weight: 10, rating: "NEUTRAL", target_price: 12, do_not_trade: false },
  ],
};

const stagedEdition: Edition = {
  id: 2,
  strategy_id: 1,
  reference_month: "2026-10",
  status: "STAGED",
  diff_from_previous: {
    entered: [{ ticker: "CXSE3", weight: 12.5 }],
    exited: [{ ticker: "B3SA3", weight: 0 }],
    changed: [
      { ticker: "VALE3", weight_before: 25, weight_after: 22.5 },
      {
        ticker: "ITUB4",
        weight_before: 20,
        weight_after: 20,
        rating_before: "BUY",
        rating_after: "SELL",
      },
    ],
  },
  entries: [
    { ticker: "PETR4", weight: 25, rating: "BUY", target_price: 60, do_not_trade: false },
    { ticker: "VALE3", weight: 22.5, rating: "NEUTRAL", target_price: 85, do_not_trade: false },
    { ticker: "ITUB4", weight: 20, rating: "SELL", target_price: 30, do_not_trade: false },
    { ticker: "EGIE3", weight: 20, rating: "BUY", target_price: 52, do_not_trade: false },
    { ticker: "CXSE3", weight: 12.5, rating: "BUY", target_price: 20, do_not_trade: false },
  ],
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
  mock.holdings = null;
  mock.positions = [];
  mock.attributions = {};
  mock.promotions = [];
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

describe("the strategy detail's Atribuição tab", () => {
  const attribution = (
    ticker: string,
    custody: number,
    slices: Attribution["slices"],
  ): Attribution => {
    const attributed = slices.reduce((sum, slice) => sum + slice.quantity, 0);
    return {
      ticker,
      custody_quantity: custody,
      attributed_quantity: attributed,
      unattributed_quantity: custody - attributed,
      slices,
    };
  };
  const position = (ticker: string, quantity: number): Position => ({
    ticker,
    quantity,
    average_price: 0,
  });

  // The chain carries WEGE3 (a target this strategy holds nothing of): its whole custody sits in the
  // manual bucket, so the reconciliation shows it through "Outra estratégia" rather than dropping it.
  const chainWithWege: Allocation = {
    ...allocation,
    classes: [
      {
        ...allocation.classes[0],
        strategies: [
          {
            ...allocation.classes[0].strategies[0],
            tickers: [
              ...allocation.classes[0].strategies[0].tickers,
              {
                ticker: "WEGE3",
                quantity: 0,
                target: 5,
                ideal: 5000,
                actual: 0,
                deficit: 5000,
                drift: -2.5,
                price: 52.4,
              },
            ],
          },
        ],
      },
    ],
  };
  const reconciledHoldings: StrategyHoldings = {
    strategy_id: 1,
    strategy_name: "Top Dividendos",
    total_value: 8400,
    holdings: [
      { ticker: "PETR4", quantity: 100, value: 6000, share: 71.43 },
      { ticker: "VALE3", quantity: 20, value: 2400, share: 28.57 },
    ],
  };
  const reconciledAttributions: Record<string, Attribution> = {
    PETR4: attribution("PETR4", 100, [
      { strategy_id: 1, strategy_name: "Top Dividendos", quantity: 100 },
    ]),
    VALE3: attribution("VALE3", 50, [
      { strategy_id: 1, strategy_name: "Top Dividendos", quantity: 20 },
      { strategy_id: 4, strategy_name: "Legado Ações", quantity: 30 },
    ]),
    WEGE3: attribution("WEGE3", 1000, [
      { strategy_id: 4, strategy_name: "Legado Ações", quantity: 1000 },
    ]),
  };
  const reconciledPositions = [
    position("PETR4", 100),
    position("VALE3", 50),
    position("WEGE3", 1000),
  ];
  const divergentHoldings: StrategyHoldings = {
    strategy_id: 1,
    strategy_name: "Top Dividendos",
    total_value: 12000,
    holdings: [
      { ticker: "PETR4", quantity: 60, value: 3600, share: 30 },
      { ticker: "VALE3", quantity: 70, value: 8400, share: 70 },
    ],
  };
  const divergentAttributions: Record<string, Attribution> = {
    PETR4: attribution("PETR4", 100, [
      { strategy_id: 1, strategy_name: "Top Dividendos", quantity: 60 },
    ]),
    VALE3: attribution("VALE3", 50, [
      { strategy_id: 1, strategy_name: "Top Dividendos", quantity: 70 },
    ]),
  };
  const divergentPositions = [position("PETR4", 100), position("VALE3", 50)];

  const tableRows = () => {
    const table = screen.getByTestId("attribution-reconciliation-table");
    return within(table)
      .getAllByRole("row")
      .slice(1)
      .map((row) => cellsOf(row));
  };

  it("titles the view Custódia × atribuição and reconciles split custody", async () => {
    mock.strategies = [strategy];
    mock.allocation = chainWithWege;
    mock.holdings = reconciledHoldings;
    mock.positions = reconciledPositions;
    mock.attributions = reconciledAttributions;

    renderDetail("/strategies/1/atribuicao");

    expect(await screen.findByRole("heading", { level: 1, name: "Top Dividendos" })).toBeDefined();
    expect(screen.getByRole("link", { name: "Atribuição" })).toBeDefined();
    expect(
      await screen.findByRole("heading", { level: 2, name: "Custódia × atribuição" }),
    ).toBeDefined();
    expect(screen.getByTestId("attribution-caption").textContent).toContain(
      "A coluna Fora mostra o que está na custódia mas não nesta fatia.",
    );

    expect(
      within(screen.getByTestId("attribution-reconciliation-table"))
        .getAllByRole("columnheader")
        .map((header) => header.textContent),
    ).toEqual(["Ativo", "Custódia", "Nesta estratégia", "Fora", "Outra estratégia", "Situação"]);
    expect(tableRows()).toEqual([
      ["PETR4", "100", "100", "0", "—", "confere"],
      ["VALE3", "50", "20", "30", "Legado Ações · 30", "confere"],
      ["WEGE3", "1.000", "0", "1.000", "Legado Ações · 1.000", "confere"],
    ]);
    expect(screen.getByTestId("attribution-finding").textContent).toBe(
      "Custódia e atribuição conferem em todos os tickers.",
    );
  });

  it("states the divergence the reconciliation finds instead of only tinting it", async () => {
    mock.strategies = [strategy];
    mock.holdings = divergentHoldings;
    mock.positions = divergentPositions;
    mock.attributions = divergentAttributions;

    renderDetail("/strategies/1/atribuicao");

    await screen.findByTestId("attribution-reconciliation-table");
    expect(tableRows()).toEqual([
      ["PETR4", "100", "60", "40", "—", "40 cotas fora de toda estratégia"],
      ["VALE3", "50", "70", "−20", "—", "atribuição 20 cotas acima da custódia"],
    ]);
    expect(screen.getByTestId("attribution-finding").textContent).toBe(
      "2 de 2 tickers divergem da custódia · PETR4 (40 cotas fora de toda estratégia); " +
        "VALE3 (atribuição 20 cotas acima da custódia)",
    );
  });
});

describe("the strategy detail's Edições tab", () => {
  const timelineRows = () => {
    const timeline = screen.getByTestId("edition-timeline");
    return within(timeline)
      .getAllByRole("row")
      .slice(1)
      .map((row) => cellsOf(row));
  };

  it("lists the edition timeline newest-first with only the current edition marked active", async () => {
    mock.strategies = [strategy];
    mock.editions = [historicalEdition, activeEdition, stagedEdition];

    renderDetail("/strategies/1/edicoes");

    await screen.findByTestId("edition-timeline");
    expect(timelineRows()).toEqual([
      ["out/26", "revisão", "5 ativos"],
      ["set/26", "ativa", "5 ativos"],
      ["ago/26", "anterior", "2 ativos"],
    ]);
  });

  it("shows the staged diff summary and the full table, calling only real moves alterada", async () => {
    mock.strategies = [strategy];
    mock.editions = [historicalEdition, activeEdition, stagedEdition];

    renderDetail("/strategies/1/edicoes");

    expect(await screen.findByText("+1 −1 ~2")).toBeDefined();
    expect(screen.getByText("+ CXSE3 12.5%")).toBeDefined();
    expect(screen.getByText("VALE3 25%→22.5%")).toBeDefined();

    const table = screen.getByTestId("edition-entries");
    expect(
      within(table)
        .getAllByRole("columnheader")
        .map((header) => header.textContent),
    ).toEqual(["Ativo", "Peso", "Rating", "Preço-alvo", "Situação"]);

    expect(
      within(table)
        .getAllByRole("row")
        .slice(1)
        .map((row) => cellsOf(row)),
    ).toEqual([
      ["PETR4", "25,0%", "Compra", "R$ 60,00", "—"],
      ["VALE3", "22,5%", "Neutro", "R$ 85,00", "alterada"],
      ["ITUB4", "20,0%", "Venda", "R$ 30,00", "alterada"],
      ["EGIE3", "20,0%", "Compra", "R$ 52,00", "alterada"],
      ["CXSE3", "12,5%", "Compra", "R$ 20,00", "nova"],
    ]);
  });

  it("promotes the staged edition and names the next recompute", async () => {
    mock.strategies = [strategy];
    mock.editions = [historicalEdition, activeEdition, stagedEdition];

    renderDetail("/strategies/1/edicoes");
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "Promover edição" }));

    const note = await screen.findByTestId("edition-decision-note");
    expect(note.textContent).toContain("próximo recálculo");
    expect(mock.promotions).toEqual([
      { referenceMonth: "2026-10", tickers: ["PETR4", "VALE3", "ITUB4", "EGIE3", "CXSE3"] },
    ]);

    await waitFor(() =>
      expect(timelineRows()).toEqual([
        ["out/26", "ativa", "5 ativos"],
        ["set/26", "anterior", "5 ativos"],
        ["ago/26", "anterior", "2 ativos"],
      ]),
    );
    expect(screen.queryByTestId("staged-edition-review")).toBeNull();
  });

  it("discards without promoting and still names the next recompute", async () => {
    mock.strategies = [strategy];
    mock.editions = [historicalEdition, activeEdition, stagedEdition];

    renderDetail("/strategies/1/edicoes");
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "Descartar" }));

    const note = await screen.findByTestId("edition-decision-note");
    expect(note.textContent).toContain("próximo recálculo");
    expect(mock.promotions).toEqual([]);

    expect(timelineRows()).toEqual([
      ["out/26", "revisão", "5 ativos"],
      ["set/26", "ativa", "5 ativos"],
      ["ago/26", "anterior", "2 ativos"],
    ]);
    expect(screen.getByTestId("staged-edition-review")).toBeDefined();
  });

  it("keeps a manual strategy on Carteira and Fatia, with no Edições tab", async () => {
    mock.strategies = [{ id: 9, name: "Legado", asset_class: "ACOES", kind: "MANUAL" }];

    renderDetail("/strategies/9/edicoes");

    expect(await screen.findByRole("heading", { level: 1, name: "Legado" })).toBeDefined();
    expect(screen.queryByRole("link", { name: "Edições" })).toBeNull();
    expect(screen.queryByRole("link", { name: "Atribuição" })).toBeNull();
    expect(screen.getByRole("link", { name: "Carteira" })).toBeDefined();
    expect(screen.getByRole("link", { name: "Fatia no portfólio" })).toBeDefined();
    expect(
      screen.getByText("A cadeia do mês não carrega ativos para esta estratégia."),
    ).toBeDefined();
  });
});
