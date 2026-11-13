// @vitest-environment jsdom
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type {
  CorporateAction,
  Gap,
  Income,
  StaleQuote,
  SuggestedOrder,
  TransferProposal,
} from "../api/types.ts";
import { Fechamento } from "./Fechamento.tsx";

// Pin "today" so the month gates are deterministic: the page judges the open month backdated by
// comparing it with the wall clock's month, which would turn these specs red on the first of a month.
vi.mock("../lib/dates.ts", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../lib/dates.ts")>();
  return { ...actual, todayIso: () => "2026-10-15" };
});

const mutate = vi.fn();
const mock = vi.hoisted(() => ({
  period: "2026-10",
  hasOpenMonth: true,
  transfers: [] as TransferProposal[],
  orders: [] as SuggestedOrder[],
  gaps: [] as Gap[],
  corporateActions: [] as CorporateAction[],
  staleQuotes: [] as StaleQuote[],
  incomes: [] as Income[],
  open: vi.fn(),
}));

vi.mock("../api/queries.ts", () => ({
  useCurrentMonth: () => ({
    isLoading: false,
    isError: false,
    data: mock.hasOpenMonth
      ? {
          month: {
            id: 10,
            period: mock.period,
            capital: 250000,
            floor: 20000,
            status: "OPEN",
            aportes: 0,
          },
          targets: { classes: [], strategies: [], sub_classes: [], tickers: [] },
        }
      : null,
  }),
  useMonthPeriodRange: () => ({
    isLoading: false,
    isError: false,
    data: { min: "2020-03", max: "2026-10", default: "2026-10" },
  }),
  useOpenMonth: () => ({ mutate: mock.open, isPending: false, error: null }),
  useOrders: () => ({
    isLoading: false,
    isError: false,
    data: {
      period: "2026-10",
      orders: mock.orders,
      transfers: mock.transfers,
      unplanned_trades: [],
    },
  }),
  useClosedMonths: () => ({
    isLoading: false,
    isError: false,
    data: [
      {
        id: 9,
        period: "2026-09",
        capital: 240000,
        floor: 20000,
        status: "CLOSED",
        aportes: 2000,
      },
    ],
  }),
  useMonthSnapshot: () => ({
    isLoading: false,
    isError: false,
    data: {
      id: 3,
      month_id: 9,
      created_at: "2026-09-30T18:05:00",
      state: {
        period: "2026-09",
        capital: 240000,
        floor: 20000,
        aportes: 2000,
        caixa: 45000,
        holdings: [],
        attributions: [],
        classes: [],
        income: { gross: 100, retained: 15, net: 85 },
        orders: {
          orders: [
            {
              id: 1,
              ticker: "ITUB4",
              side: "BUY",
              quantity: 10,
              executed_quantity: 10,
              status: "IMPORTED",
            },
            {
              id: 2,
              ticker: "B3SA3",
              side: "SELL",
              quantity: 10,
              executed_quantity: 5,
              status: "EXECUTED",
            },
          ],
          transfers: [],
          unplanned_trades: [],
        },
        tax: {
          carry: [],
          meter: {
            gross_sales: 0,
            exemption_limit: 20000,
            exceeded: false,
            realized_gain: 0,
            taxable_gain: 0,
            rate: 15,
            tax: 0,
          },
        },
      },
    },
  }),
  useCloseMonth: () => ({
    mutate,
    isPending: false,
    error: null,
  }),
  useGaps: () => ({ data: mock.gaps, isLoading: false, isError: false }),
  useCorporateActions: () => ({ data: mock.corporateActions, isLoading: false, isError: false }),
  useIncome: () => ({ data: mock.incomes, isLoading: false, isError: false }),
  usePortfolioStatus: () => ({
    data: { data: { stale_quotes: mock.staleQuotes } },
    isLoading: false,
    isError: false,
  }),
}));

function transfer(overrides: Partial<TransferProposal> = {}): TransferProposal {
  return {
    id: 7,
    ticker: "ITUB4",
    from_strategy_id: 1,
    from_strategy: "Dividendos",
    to_strategy_id: 2,
    to_strategy: "Small Caps",
    quantity: 20,
    amount: 600,
    auto_approved: false,
    status: "PENDING",
    ...overrides,
  };
}

beforeEach(() => {
  mock.period = "2026-10";
  mock.hasOpenMonth = true;
  mock.transfers = [transfer()];
  mock.orders = [];
  mock.gaps = [];
  mock.corporateActions = [];
  mock.staleQuotes = [];
  mock.incomes = [];
});

afterEach(() => {
  cleanup();
  mutate.mockReset();
});

function renderPage() {
  return render(
    <MemoryRouter>
      <Fechamento />
    </MemoryRouter>,
  );
}

describe("Fechamento", () => {
  it("states a pending transfer and lets the refresh-first close attempt run", async () => {
    const user = userEvent.setup();
    renderPage();

    expect(screen.getByText("Fechamento bloqueado: transferência pendente")).toBeDefined();
    expect(screen.getByText(/Se sobrar uma pendente, o fechamento é recusado/)).toBeDefined();
    const close = screen.getByTestId("close-month") as HTMLButtonElement;
    // The close's own refresh finalises the transfers, so the attempt is not pre-disabled on the
    // stale inbox; the server's 409 is the authority when a proposal remains pending.
    expect(close.disabled).toBe(false);
    expect(close.textContent).toBe("Fechar mês");
    expect(screen.getByRole("link", { name: "resolver" }).getAttribute("href")).toBe(
      "/plan/transfers",
    );

    await user.click(close);
    expect(mutate).toHaveBeenCalledTimes(1);
  });

  it("hosts the month opener when no month is open", () => {
    mock.hasOpenMonth = false;
    mock.transfers = [];
    renderPage();

    expect(screen.getByTestId("month-opener")).toBeDefined();
    expect(screen.queryByTestId("close-month")).toBeNull();
    expect(screen.queryByTestId("non-blocking-orders")).toBeNull();
  });

  it("releases the close when the only proposal was rejected", () => {
    mock.transfers = [transfer({ status: "REJECTED" })];
    renderPage();

    expect(screen.getByText("Pronto para fechar")).toBeDefined();
    const close = screen.getByTestId("close-month") as HTMLButtonElement;
    expect(close.disabled).toBe(false);
    expect(close.textContent).toBe("Fechar mês");
  });

  it("disables the close for a backdated plan-only month", () => {
    mock.transfers = [];
    mock.period = "2020-12";
    renderPage();

    const close = screen.getByTestId("close-month") as HTMLButtonElement;
    expect(close.disabled).toBe(true);
    expect(close.textContent).toContain("Só o mês atual pode ser fechado");
    expect(screen.getByText("histórico — sem fechamento")).toBeDefined();
  });

  it("lists the non-blocking data quality as frozen, each labelled não trava", () => {
    mock.orders = [
      {
        id: 1,
        ticker: "PETR4",
        side: "BUY",
        quantity: 10,
        suggested_price: 10,
        executed_quantity: 4,
        status: "SUGGESTED",
      },
    ];
    mock.gaps = [
      {
        id: 1,
        ticker: "PETR4",
        date: "2026-10-20",
        quantity: 40,
        status: "OPEN",
        trade_watermark: 0,
        action_watermark: 0,
      },
    ];
    mock.corporateActions = [
      {
        id: 1,
        ticker: "ITUB4",
        kind: "SPLIT",
        ex_date: "2026-10-15",
        source: "B3_MOVEMENTS",
        ordinal: 1,
        status: "NEEDS_REVIEW",
        is_applied: false,
      },
    ];
    mock.staleQuotes = [{ ticker: "VALE3", date: "2026-09-30", close: 10, days_old: 30 }];
    mock.incomes = [
      {
        id: 1,
        ticker: "PETR4",
        date: "2026-10-20",
        type: "DIVIDEND",
        gross_amount: 100,
        retained_amount: 0,
        net_amount: 100,
        tax_nature: "INCOME",
        provenance: "B3",
        status: "ANNOUNCED",
      },
    ];

    renderPage();

    expect(screen.getByTestId("non-blocking-orders").textContent).toContain(
      "Ordens não executadas ou parciais",
    );
    expect(screen.getByTestId("non-blocking-orders").textContent).toContain("1 ordem · 1 parcial");
    expect(screen.getByTestId("non-blocking-gaps").textContent).toContain("1 aberta");
    expect(screen.getByTestId("non-blocking-corporate-actions").textContent).toContain(
      "1 em revisão",
    );
    expect(screen.getByTestId("non-blocking-stale-quotes").textContent).toContain(
      "1 desatualizada",
    );
    expect(screen.getByTestId("non-blocking-receivable-income").textContent).toContain(
      "1 a receber",
    );
    expect(screen.getAllByText("não trava")).toHaveLength(5);
    expect(screen.getByText(/nenhum deles recusa o fechamento/)).toBeDefined();
  });

  it("shows the selected closed month's frozen summary and its frozen orders", () => {
    renderPage();

    expect(screen.getByText("R$ 45.000,00")).toBeTruthy();
    expect(screen.getByText("R$ 85,00")).toBeTruthy();
    expect(screen.getByText("30/09/2026 18:05")).toBeTruthy();
    expect(screen.getByText("PARCIAL")).toBeTruthy();
    const frozen = screen.getByTestId("frozen-orders");
    expect(within(frozen).getByText("ITUB4")).toBeDefined();
    expect(within(frozen).getByText("B3SA3")).toBeDefined();
    expect(screen.getByRole("link", { name: /Abrir mês congelado/ }).getAttribute("href")).toBe(
      "/portfolio/history/2026-09",
    );
  });

  it("keeps the recent closes reachable from the screen", () => {
    renderPage();

    const recent = screen.getByTestId("recent-closes");
    expect(within(recent).getByText("set/2026")).toBeDefined();
    expect(screen.getByRole("link", { name: /Ver todos/ }).getAttribute("href")).toBe(
      "/portfolio/history",
    );
  });

  it("closes the month from the enabled button", async () => {
    mock.transfers = [];
    const user = userEvent.setup();
    renderPage();

    const close = screen.getByTestId("close-month") as HTMLButtonElement;
    expect(close.disabled).toBe(false);

    await user.click(close);

    expect(mutate).toHaveBeenCalledTimes(1);
  });
});
