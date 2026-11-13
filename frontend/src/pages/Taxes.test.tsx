// @vitest-environment jsdom
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type {
  CarryBucket,
  CurrentMonth,
  HarvestReport,
  Month,
  MonthPeriodRange,
  StockTaxReport,
} from "../api/types.ts";
import { AnalysisWindowProvider } from "../hooks/useAnalysisWindow.tsx";
import { formatCurrency } from "../lib/format.ts";
import { Taxes } from "./Taxes.tsx";

// Pin "today" so the apuração state chips are deterministic: the page judges a read month
// historical by comparing it with the wall clock's month.
vi.mock("../lib/dates.ts", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../lib/dates.ts")>();
  return { ...actual, todayIso: () => "2026-11-15" };
});

const recorded = vi.hoisted(() => ({
  taxPeriods: [] as string[],
  harvestEnabled: [] as boolean[],
}));

const mock = vi.hoisted(() => ({
  current: null as CurrentMonth | null,
  range: { min: "2025-11", max: "2026-11", default: "2026-10" } as MonthPeriodRange,
  closed: [] as Month[],
  reports: {} as Record<string, StockTaxReport>,
  harvest: undefined as HarvestReport | undefined,
}));

vi.mock("../api/queries.ts", () => ({
  useMonthPeriodRange: () => ({ isLoading: false, isError: false, data: mock.range }),
  useCurrentMonth: () => ({ isLoading: false, isError: false, data: mock.current }),
  useClosedMonths: () => ({ isLoading: false, isError: false, data: mock.closed }),
  useStockTax: (period: string) => {
    recorded.taxPeriods.push(period);
    return {
      isLoading: false,
      isError: false,
      data: period.length === 0 ? undefined : (mock.reports[period] ?? taxReport({ period })),
    };
  },
  useHarvest: (enabled = true) => {
    recorded.harvestEnabled.push(enabled);
    return {
      isLoading: false,
      isError: false,
      data: enabled ? mock.harvest : undefined,
      error: null,
    };
  },
  useAcceptHarvestPair: () => ({ mutate: vi.fn(), isPending: false, error: null }),
  useSetHarvestOptOut: () => ({ mutate: vi.fn(), isPending: false }),
}));

const carry: CarryBucket[] = [
  {
    kind: "SWING",
    available: false,
    opening_balance: null,
    loss_banked: null,
    loss_applied: null,
    closing_balance: null,
  },
];

function taxReport(overrides: Partial<StockTaxReport> = {}): StockTaxReport {
  return {
    period: "2026-10",
    meter: {
      gross_sales: 0,
      exemption_limit: 20000,
      exceeded: false,
      realized_gain: 0,
      taxable_gain: 0,
      rate: 15,
      tax: 0,
    },
    average_prices: [],
    realized: [
      { kind: "SWING", gross_sales: 0, gains: 0, losses: 0, result: 0 },
      { kind: "DAY_TRADE", gross_sales: 0, gains: 0, losses: 0, result: 0 },
      { kind: "FII", gross_sales: 0, gains: 0, losses: 0, result: 0 },
    ],
    carry,
    day_trades: [],
    pending_gaps: [],
    unclassified: [],
    darf_due_date: "2026-11-30",
    ...overrides,
  };
}

const harvest: HarvestReport = {
  period: "2026-11",
  headroom: 20000,
  minimum_loss: 500,
  suggestions: [],
  ignored: [],
  fii_report: [],
  pairs: [],
};

function month(period: string, status: "OPEN" | "CLOSED"): Month {
  return {
    id: Number(period.replace("-", "")),
    period,
    capital: 1000,
    floor: 0,
    status,
    aportes: 0,
  };
}

function currentMonth(period: string): CurrentMonth {
  return {
    month: month(period, "OPEN"),
    targets: { classes: [], strategies: [], sub_classes: [], tickers: [] },
  };
}

function renderTaxes() {
  return render(
    <AnalysisWindowProvider>
      <Taxes />
    </AnalysisWindowProvider>,
  );
}

function apuracaoRows() {
  return within(screen.getByTestId("tax-apuracoes-list")).getAllByRole("row");
}

beforeEach(() => {
  mock.range = { min: "2025-11", max: "2026-11", default: "2026-10" };
  mock.current = null;
  mock.closed = [month("2026-10", "CLOSED"), month("2026-09", "CLOSED")];
  mock.reports = {};
  mock.harvest = harvest;
  recorded.taxPeriods = [];
  recorded.harvestEnabled = [];
});

afterEach(cleanup);

describe("Análise · Impostos", () => {
  it("reads the anchored month and only filters the apuração list with the window", async () => {
    const user = userEvent.setup();
    renderTaxes();

    expect(screen.getByTestId("rail-window").textContent).toBe("nov/2025 – out/2026");
    expect(screen.getByText(/apuração mensal; a janela filtra a lista/).textContent).toContain(
      "mês-âncora out/2026",
    );
    expect(recorded.taxPeriods.at(-1)).toBe("2026-10");
    expect(screen.getByText("Apuração de out/2026")).toBeDefined();
    expect(apuracaoRows()).toHaveLength(13);

    await user.click(screen.getByTestId("rail-preset-1m"));

    expect(screen.getByTestId("rail-window").textContent).toBe("out/2026 – out/2026");
    expect(recorded.taxPeriods.at(-1)).toBe("2026-10");
    expect(apuracaoRows()).toHaveLength(2);
  });

  it("selects the apuração from the window list and re-reads the anchored month", async () => {
    const user = userEvent.setup();
    renderTaxes();

    await user.click(screen.getByTestId("rail-preset-3m"));
    expect(apuracaoRows()).toHaveLength(4);

    const row = document.getElementById("tax-apuracao-2026-09");
    expect(row).not.toBeNull();
    await user.click(row as HTMLElement);

    expect(recorded.taxPeriods.at(-1)).toBe("2026-09");
    expect(screen.getByText("Apuração de set/2026")).toBeDefined();
    expect(screen.getByTestId("rail-window").textContent).toBe("jul/2026 – set/2026");
  });

  it("renders the meter, realized result, carry, DARF and pendências for the anchored month", () => {
    mock.reports["2026-10"] = taxReport({
      meter: {
        gross_sales: 26000,
        exemption_limit: 20000,
        exceeded: true,
        realized_gain: 3000,
        taxable_gain: 2500,
        rate: 15,
        tax: 375,
      },
      realized: [
        { kind: "SWING", gross_sales: 26000, gains: 3200, losses: 200, result: 3000 },
        { kind: "DAY_TRADE", gross_sales: 0, gains: 0, losses: 0, result: 0 },
        { kind: "FII", gross_sales: 0, gains: 0, losses: 100, result: -100 },
      ],
      carry: [
        {
          kind: "SWING",
          available: true,
          opening_balance: 1000,
          loss_banked: 0,
          loss_applied: 200,
          closing_balance: 800,
        },
        {
          kind: "FII",
          available: true,
          opening_balance: 400,
          loss_banked: 0,
          loss_applied: 0,
          closing_balance: 400,
        },
      ],
      pending_gaps: [{ id: 7, ticker: "PETR4", date: "2026-10-10", quantity: 100 }],
      unclassified: [{ ticker: "MYST3", status: "HELD_AND_SOLD" }],
      day_trades: [{ ticker: "VALE3", institution_id: 1, date: "2026-10-15", quantity: 50 }],
    });

    renderTaxes();

    expect(screen.getAllByText("Teto estourado").length).toBeGreaterThan(0);
    expect(screen.getByTestId("tax-darf-kpi").textContent).toBe(formatCurrency(375));
    expect(screen.getByTestId("tax-carry-swing").textContent).toBe(formatCurrency(800));
    expect(screen.getByTestId("tax-carry-fii").textContent).toBe(formatCurrency(400));
    expect(screen.getByText("Apuração de out/2026")).toBeDefined();
    expect(screen.getAllByText("Swing").length).toBeGreaterThan(0);
    expect(screen.getByText("Compensação")).toBeDefined();
    expect(screen.getByText("Resultado pendente")).toBeDefined();
    expect(screen.getByText(/PETR4 · 10\/10\/2026/)).toBeDefined();
    expect(screen.getByText("Sem classe")).toBeDefined();
    expect(screen.getByText(/VALE3 · 15\/10\/2026/)).toBeDefined();
  });

  it("reaches the harvest pass only on the open month", async () => {
    mock.current = currentMonth("2026-11");
    const user = userEvent.setup();
    renderTaxes();

    expect(screen.queryByText("Harvest · step-up")).toBeNull();
    expect(screen.getByText(/só existe para o mês aberto/)).toBeDefined();

    await user.click(screen.getByText("Ir para o mês aberto · nov/2026"));

    expect(recorded.taxPeriods.at(-1)).toBe("2026-11");
    expect(screen.getByText("Harvest · step-up")).toBeDefined();
  });
});
