// @vitest-environment jsdom
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { Cockpit } from "./Cockpit.tsx";

const mock = vi.hoisted(() => ({
  openMonth: true,
  manualBucket: false,
  outsideCustody: 1_000,
  unclassified: 0,
}));

vi.mock("../api/queries.ts", () => {
  const month = {
    id: 10,
    period: "2026-10",
    capital: 100_000,
    floor: 20_000,
    status: "OPEN",
    aportes: 0,
  };

  const classRung = (assetClass: string, actual: number, drift: number, target: number) => ({
    asset_class: assetClass,
    target,
    ideal: (target / 100) * 100_000,
    actual,
    deficit: (target / 100) * 100_000 - actual,
    drift,
    unattributed: assetClass === "ACOES" ? mock.outsideCustody : 0,
    strategies: [],
    manual:
      mock.manualBucket && assetClass === "ACOES"
        ? {
            strategy: "Legado",
            actual: 5_000,
            tickers: [{ ticker: "ITUB4", actual: 5_000 }],
          }
        : undefined,
    outside:
      assetClass === "ACOES" && mock.outsideCustody > 0
        ? {
            actual: mock.outsideCustody,
            tickers: [{ ticker: "ORVR3", actual: mock.outsideCustody }],
          }
        : { actual: 0, tickers: [] },
    sub_classes: [],
  });

  const query = (data: unknown) => ({ isLoading: false, isError: false, data });

  return {
    useCurrentMonth: () =>
      query(
        mock.openMonth
          ? {
              month,
              targets: {
                classes: [],
                strategies: [],
                sub_classes: [],
                tickers: [],
              },
            }
          : null,
      ),
    useCurrentAllocation: () =>
      query({
        period: "2026-10",
        capital: 100_000,
        floor: 20_000,
        ideal: 100_000,
        actual: 80_000,
        deficit: 20_000,
        drift: 0,
        unattributed: mock.outsideCustody + mock.unclassified,
        unclassified:
          mock.unclassified > 0
            ? {
                actual: mock.unclassified * 500,
                tickers: Array.from({ length: mock.unclassified }, (_, index) => ({
                  ticker: `ZZZ${index + 1}3`,
                  actual: 500,
                })),
              }
            : { actual: 0, tickers: [] },
        classes: [
          classRung("ACOES", 55_000, 5, 50),
          classRung("FIIS", 20_000, -10, 30),
          classRung("RENDA_FIXA", 5_000, -15, 20),
        ],
      }),
    useOrders: () =>
      query({
        period: "2026-10",
        orders: [
          {
            id: 1,
            ticker: "ITUB4",
            side: "BUY",
            quantity: 10,
            executed_quantity: 0,
            status: "SUGGESTED",
          },
        ],
        transfers: [
          {
            id: 7,
            ticker: "ITUB4",
            from_strategy_id: 1,
            from_strategy: "Top Dividendos",
            to_strategy_id: 2,
            to_strategy: "Small Caps",
            quantity: 10,
            auto_approved: false,
            status: "PENDING",
          },
        ],
        unplanned_trades: [],
      }),
    useOrderPlan: () =>
      query({
        period: "2026-10",
        phase: "FULL",
        capital: 100_000,
        floor: 20_000,
        investable_capital: 80_000,
        classes: [],
        candidates: [],
        funding: {
          buy_amount: 10_000,
          sell_amount: 7_500,
          unfunded_amount: 2_500,
          neutro_deficit: 0,
        },
        outside_strategy: [],
        unclassified: [],
        attribution: [],
        transfers: [],
      }),
    useTrades: () =>
      query([
        {
          id: 1,
          ticker: "ITUB4",
          date: "2026-10-03",
          venue: "B3",
          side: "BUY",
          quantity: 10,
          unit_price: 30,
          institution_id: 1,
          source: "B3_IMPORT",
          confirmed_by_b3: true,
          ordinal: 1,
        },
      ]),
    useStrategies: () =>
      query([
        {
          id: 1,
          name: "Top Dividendos",
          asset_class: "ACOES",
          kind: "REPORT_DRIVEN",
        },
        ...(mock.manualBucket
          ? [{ id: 9, name: "Legado", asset_class: "ACOES", kind: "MANUAL" }]
          : []),
      ]),
    useEditionCoverage: () => [{ data: [{ reference_month: "2026-10", status: "ACTIVE" }] }],
    useIncome: () => query([]),
    useUpcomingIncome: () => query([]),
    useClosedMonths: () => query([]),
    useBonds: () =>
      query([
        {
          id: 1,
          name: "CDB Teste",
          rate: 10,
          maturity_date: "2000-01-01",
          sub_class: "FIXED_RATE",
        },
      ]),
  };
});

beforeEach(() => {
  localStorage.clear();
  mock.openMonth = true;
  mock.manualBucket = false;
  mock.outsideCustody = 1_000;
  mock.unclassified = 0;
});

afterEach(cleanup);

describe("Cockpit", () => {
  it("renders the month KPIs", () => {
    render(
      <MemoryRouter>
        <Cockpit />
      </MemoryRouter>,
    );

    expect(screen.getByText("Cockpit de out/2026")).toBeDefined();
    expect(screen.getByTestId("cockpit-capital").textContent).toContain("100.000,00");
    expect(screen.getByTestId("cockpit-caixa").textContent).toContain("20.000,00");
    expect(screen.getByTestId("cockpit-investable").textContent).toContain("80.000,00");
    expect(screen.getByTestId("cockpit-status").textContent).toBe("Aberto");
  });

  it("renders the seven ritual steps with their live status", () => {
    render(
      <MemoryRouter>
        <Cockpit />
      </MemoryRouter>,
    );

    for (const key of [
      "editions",
      "recalculate",
      "transfers",
      "orders",
      "import",
      "income",
      "close",
    ]) {
      expect(screen.getByTestId(`ritual-step-${key}`)).toBeDefined();
    }
    expect(screen.getByTestId("ritual-step-close").textContent).toContain("Bloqueado");
    expect(screen.getByTestId("ritual-step-transfers").textContent).toContain("1 transferências");
  });

  it("keeps the editions step counting report-driven strategies only", () => {
    mock.manualBucket = true;
    render(
      <MemoryRouter>
        <Cockpit />
      </MemoryRouter>,
    );

    expect(screen.getByTestId("ritual-step-editions").textContent).toContain("1 de 1");
  });

  it("renders outside custody as an info card with its own action", () => {
    render(
      <MemoryRouter>
        <Cockpit />
      </MemoryRouter>,
    );

    expect(screen.getByText(/Cotas fora da estratégia/)).toBeDefined();
    expect(screen.getByRole("link", { name: "Atribuir" })).toBeDefined();
    expect(screen.getByText(/segue sem funding/)).toBeDefined();
    expect(screen.getByText("55,0%")).toBeDefined();
  });

  it("renders unclassified tickers as a critical card with a classify action", () => {
    mock.outsideCustody = 0;
    mock.unclassified = 2;
    render(
      <MemoryRouter>
        <Cockpit />
      </MemoryRouter>,
    );

    expect(screen.getByText(/2 tickers sem classe — classificar/)).toBeDefined();
    expect(screen.getByRole("link", { name: "Classificar" })).toBeDefined();
    expect(screen.queryByText(/Cotas fora da estratégia/)).toBeNull();
  });

  it("raises no attention card for the manual bucket", () => {
    mock.manualBucket = true;
    mock.outsideCustody = 0;
    render(
      <MemoryRouter>
        <Cockpit />
      </MemoryRouter>,
    );

    expect(screen.queryByText(/legado/i)).toBeNull();
  });

  it("removes an agenda item once it is marked read", async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <Cockpit />
      </MemoryRouter>,
    );

    expect(screen.getByText(/Vencimento de CDB Teste/)).toBeDefined();
    await user.click(screen.getByRole("button", { name: "Marcar como lida" }));

    expect(screen.queryByText(/Vencimento de CDB Teste/)).toBeNull();
    expect(screen.getByText("Nada na agenda.")).toBeDefined();
  });

  it("points at the month opener when no month is open", () => {
    mock.openMonth = false;
    render(
      <MemoryRouter>
        <Cockpit />
      </MemoryRouter>,
    );

    expect(screen.getByText("Nenhum mês aberto")).toBeDefined();
    expect(screen.getByRole("link", { name: "Abrir mês" })).toBeDefined();
  });
});
