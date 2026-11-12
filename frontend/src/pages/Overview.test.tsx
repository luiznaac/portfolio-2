// @vitest-environment jsdom
import { cleanup, render, screen, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { Overview } from "./Overview.tsx";

const mock = vi.hoisted(() => ({ resolved: false, empty: false }));

vi.mock("../api/queries.ts", () => {
  const transfer = {
    kind: "PENDING_TRANSFER",
    object: "MONTH",
    severity: "ACTION",
    ref: { type: "TRANSFER", id: 1, ticker: "WEGE3" },
    params: { quantity: 50, amount: 1_500 },
  };
  const stagedImport = {
    kind: "STAGED_IMPORT",
    object: "IMPORT",
    severity: "ACTION",
    ref: { type: "IMPORT", id: 9 },
    params: { age_days: 2 },
  };
  const staleQuote = {
    kind: "STALE_QUOTE",
    object: "ASSET",
    severity: "WATCH",
    ref: { type: "QUOTE", ticker: "VALE3" },
    params: { days_old: 6, close: 60 },
  };

  const status = () => ({
    as_of: "2026-11-02",
    open_month: mock.empty ? undefined : { id: 4, period: "2026-11" },
    last_close: mock.empty ? undefined : { id: 3, period: "2026-10" },
    capital: 100_000,
    invested: 70_000,
    uninvested_balance: 30_000,
    deficit: 20_000,
    drift: -2.5,
    month: mock.empty
      ? { open: false, steps_done: 0, steps_total: 6, suggested_orders: 0, pending_transfers: 0 }
      : { open: true, steps_done: 2, steps_total: 6, suggested_orders: 3, pending_transfers: 1 },
    data: mock.empty
      ? { quotes_as_of: undefined, stale_quotes: [], open_gaps: 0 }
      : {
          quotes_as_of: "2026-10-30",
          stale_quotes: [{ ticker: "VALE3", date: "2026-10-24", close: 60, days_old: 6 }],
          open_gaps: 1,
          last_snapshot: {
            month_id: 3,
            period: "2026-10",
            created_at: "2026-10-31T18:12:00",
          },
        },
    attention: mock.resolved || mock.empty ? [] : [transfer, stagedImport, staleQuote],
  });

  const rung = (
    assetClass: string,
    target: number,
    ideal: number,
    actual: number,
    drift: number,
  ) => ({
    asset_class: assetClass,
    target,
    ideal,
    actual,
    deficit: ideal - actual,
    drift,
    unattributed: 0,
    partial: false,
    strategies: [],
    outside: { actual: 0, tickers: [] },
    sub_classes: [],
  });

  const allocation = {
    period: "2026-11",
    capital: 100_000,
    floor: 0,
    ideal: 100_000,
    actual: 80_000,
    deficit: 20_000,
    drift: -2.5,
    unattributed: 0,
    unclassified: { actual: 0, tickers: [] },
    classes: [
      {
        ...rung("ACOES", 50, 50_000, 55_000, 5),
        // A strategy and ticker the landing must never render: only the three class rungs belong
        // above the fold.
        strategies: [
          {
            strategy: "Top Dividendos",
            target: 30,
            ideal: 30_000,
            actual: 35_000,
            deficit: -5_000,
            drift: 5,
            partial: false,
            tickers: [
              {
                ticker: "PETR4",
                target: 10,
                ideal: 10_000,
                actual: 12_000,
                deficit: -2_000,
                drift: 2,
                quantity: 100,
              },
            ],
          },
        ],
      },
      rung("FIIS", 30, 30_000, 20_000, -10),
      rung("RENDA_FIXA", 20, 20_000, 5_000, -15),
    ],
  };

  const series = {
    months: [
      {
        period: "2026-09",
        source: "snapshot",
        decomposition: {
          value_end: 100_000,
          net_aportes: 0,
          net_income: 3_000,
          return_percentage: 0.2,
        },
        benchmark_return: 1.1,
      },
      {
        period: "2026-10",
        source: "snapshot",
        decomposition: {
          value_end: 100_000,
          net_aportes: 0,
          net_income: 3_200,
          return_percentage: 0.12,
        },
        benchmark_return: 1.75,
      },
    ],
    xirr: 4.1,
  };

  const tax = {
    period: "2026-10",
    meter: {
      gross_sales: 12_000,
      exemption_limit: 20_000,
      exceeded: false,
      realized_gain: 1_700,
      taxable_gain: 1_700,
      rate: 15,
      tax: 255,
    },
    average_prices: [],
    realized: [],
    carry: [],
    day_trades: [],
    pending_gaps: [],
    unclassified: [],
    darf_due_date: "2026-11-30",
  };

  const query = (data: unknown) => ({ isLoading: false, isError: false, data });

  return {
    usePortfolioStatus: () => query(status()),
    useCurrentAllocation: () => query(allocation),
    usePerformanceSeries: () => query(series),
    useStockTax: () => query(tax),
  };
});

function renderOverview() {
  return render(
    <MemoryRouter>
      <Overview />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  mock.resolved = false;
  mock.empty = false;
});

afterEach(cleanup);

describe("Carteira · Visão geral", () => {
  it("renders exactly the six status cells, the attention cell carrying the action count", () => {
    renderOverview();
    expect(screen.getAllByTestId(/^strip-cell-/)).toHaveLength(6);
    expect(screen.getByTestId("strip-attention-value").textContent).toBe("2");
  });

  it("states the month on one line and renders no month card", () => {
    renderOverview();
    const line = screen.getByTestId("month-line");
    expect(line.textContent).toContain("nov/2026");
    expect(line.textContent).toContain("plano aberto");
    expect(line.textContent).toContain("2 de 6 etapas");
    expect(line.textContent).toContain("3 ordens sugeridas");
    expect(line.textContent).toContain("1 transferência pendente");
    expect(screen.getByRole("link", { name: "abrir plano →" }).getAttribute("href")).toBe("/plan");
    expect(screen.queryByText(/Fechamento de/)).toBeNull();
  });

  it("renders the month standfirst as one written paragraph with values inline", () => {
    renderOverview();
    const standfirst = screen.getByTestId("standfirst");
    expect(standfirst.tagName).toBe("P");
    expect(standfirst.textContent).toContain("out/2026 fechou em +0,12%");
    expect(standfirst.textContent).toContain("nov/2026 abre com R$ 30.000,00 de caixa");
    expect(standfirst.textContent).toContain("a transferência de WEGE3 espera decisão");
    expect(standfirst.textContent?.endsWith(".")).toBe(true);
  });

  it("renders three class rows only, worded, never a strategy or a ticker", () => {
    renderOverview();
    const rows = within(screen.getByTestId("overview-allocation")).getAllByRole("row").slice(1);
    expect(rows).toHaveLength(3);
    expect(rows[0]?.textContent).toContain("Ações");
    expect(rows[0]?.textContent).toContain("sobram R$ 5.000,00");
    expect(rows[1]?.textContent).toContain("faltam R$ 10.000,00");
    expect(screen.queryByText("Top Dividendos")).toBeNull();
    expect(screen.queryByText("PETR4")).toBeNull();
  });

  it("groups attention AÇÃO before ACOMPANHAR and deep-links every row", () => {
    renderOverview();
    const groups = screen.getAllByTestId(/^attention-group-/);
    expect(groups.map((group) => group.textContent)).toEqual(["AÇÃO · 2", "ACOMPANHAR · 1"]);
    expect(screen.getByTestId("attention-PENDING_TRANSFER:TRANSFER:1").getAttribute("href")).toBe(
      "/plan/transfers",
    );
    expect(screen.getByTestId("attention-STAGED_IMPORT:IMPORT:9").getAttribute("href")).toBe(
      "/imports",
    );
    expect(screen.getByTestId("attention-STALE_QUOTE:QUOTE:VALE3").getAttribute("href")).toBe(
      "/ativos/quotes",
    );
  });

  it("shows the data-state panel's five reads", () => {
    renderOverview();
    const panel = screen.getByTestId("data-state-panel");
    expect(panel.textContent).toContain("Cotações");
    expect(panel.textContent).toContain("30/10/2026");
    expect(panel.textContent).toContain("VALE3");
    expect(panel.textContent).toContain("out/2026 · congelado");
    expect(panel.textContent).toContain("1 aberta");
    expect(panel.textContent).toContain("Último fechamento");
    expect(panel.textContent).toContain("out/2026");
  });

  it("renders an empty state for both the queue and the data panel", () => {
    mock.empty = true;
    renderOverview();
    expect(screen.getByText("Nada precisa de você.")).toBeDefined();
    expect(screen.getByText("Sem cotações, lacunas ou fechamentos registrados.")).toBeDefined();
  });

  it("drops an attention row when its finding is resolved, without a reload", () => {
    const view = renderOverview();
    expect(screen.getByTestId("attention-PENDING_TRANSFER:TRANSFER:1")).toBeDefined();

    mock.resolved = true;
    view.rerender(
      <MemoryRouter>
        <Overview />
      </MemoryRouter>,
    );

    expect(screen.queryByTestId("attention-PENDING_TRANSFER:TRANSFER:1")).toBeNull();
    expect(screen.getByText("Nada precisa de você.")).toBeDefined();
  });
});
