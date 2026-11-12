import { describe, expect, it } from "vitest";
import type {
  AttentionItem,
  PerformanceSeries,
  PortfolioStatus,
  StockTaxReport,
} from "../api/types.ts";
import {
  attentionQueue,
  dataStatePanel,
  monthLine,
  monthStandfirst,
  resultsPanel,
  statusCells,
} from "./overview.ts";
import { ROUTES } from "./routes.ts";

function attention(overrides: Partial<AttentionItem>): AttentionItem {
  return {
    kind: "PENDING_TRANSFER",
    object: "MONTH",
    severity: "ACTION",
    ref: { type: "TRANSFER", id: 1, ticker: "WEGE3" },
    params: { quantity: 50, amount: 1_500 },
    ...overrides,
  };
}

function status(overrides: Partial<PortfolioStatus> = {}): PortfolioStatus {
  return {
    as_of: "2026-11-02",
    open_month: { id: 4, period: "2026-11" },
    last_close: { id: 3, period: "2026-10" },
    capital: 100_000,
    invested: 70_000,
    uninvested_balance: 30_000,
    deficit: 20_000,
    drift: -2.5,
    month: {
      open: true,
      steps_done: 2,
      steps_total: 6,
      suggested_orders: 3,
      pending_transfers: 1,
    },
    data: {
      quotes_as_of: "2026-10-30",
      stale_quotes: [{ ticker: "VALE3", date: "2026-10-24", close: 60, days_old: 6 }],
      open_gaps: 1,
      last_snapshot: {
        month_id: 3,
        period: "2026-10",
        created_at: "2026-10-31T18:12:00",
      },
    },
    attention: [
      attention({}),
      attention({
        kind: "UNCLASSIFIED_TICKER",
        object: "ASSET",
        ref: { type: "TICKER", ticker: "XPTY3" },
        params: { quantity: 20 },
      }),
      attention({
        kind: "STALE_QUOTE",
        object: "ASSET",
        severity: "WATCH",
        ref: { type: "QUOTE", ticker: "VALE3" },
        params: { days_old: 6, close: 60 },
      }),
    ],
    ...overrides,
  };
}

function monthPoint(period: string, returnPercentage: number, benchmark?: number) {
  return {
    period,
    source: "snapshot" as const,
    decomposition: {
      value_end: 100_000,
      net_aportes: 0,
      net_income: 3_200,
      return_percentage: returnPercentage,
    },
    benchmark_return: benchmark,
    xirr: 4.1,
  };
}

// index 0 is 2025-10, so twelve months before 2026-10 start at 2025-11.
function periodAt(index: number): string {
  const total = 9 + index;
  return `${2025 + Math.floor(total / 12)}-${String((total % 12) + 1).padStart(2, "0")}`;
}

function series(
  months = [monthPoint("2026-09", 0.2, 1.1), monthPoint("2026-10", 0.12, 1.75)],
): PerformanceSeries {
  return { months, xirr: 4.1 };
}

function tax(overrides: Partial<StockTaxReport> = {}): StockTaxReport {
  return {
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
    ...overrides,
  };
}

describe("statusCells", () => {
  it("renders exactly the six cells, in order", () => {
    const cells = statusCells(status());
    expect(cells.map((cell) => cell.key)).toEqual([
      "capital",
      "invested",
      "cash",
      "drift",
      "deficit",
      "attention",
    ]);
  });

  it("carries the action count in the attention cell, with the whole queue as its hint", () => {
    const cells = statusCells(status());
    const cell = cells.find((candidate) => candidate.key === "attention");
    expect(cell?.value).toBe("2");
    expect(cell?.hint).toBe("3 na fila");
  });

  it("renders an unknown Uninvested Balance as a dash, never as zero", () => {
    const cells = statusCells(status({ uninvested_balance: undefined }));
    expect(cells.find((cell) => cell.key === "cash")?.value).toBe("—");
  });
});

describe("monthLine", () => {
  it("states the open month, plan, step fraction, orders and transfer state", () => {
    const line = monthLine(status());
    expect(line.period).toBe("nov/2026");
    expect(line.planState).toBe("plano aberto");
    expect(line.steps).toBe("2 de 6 etapas");
    expect(line.orders).toBe("3 ordens sugeridas");
    expect(line.transfers).toBe("1 transferência pendente");
    expect(line.to).toBe(ROUTES.plan);
  });

  it("states that no month is open when the block is closed", () => {
    const line = monthLine(
      status({
        open_month: undefined,
        month: {
          open: false,
          steps_done: 0,
          steps_total: 6,
          suggested_orders: 0,
          pending_transfers: 0,
        },
      }),
    );
    expect(line.period).toBe("Nenhum mês aberto");
    expect(line.planState).toBe("sem mês aberto");
    expect(line.transfers).toBe("sem mês aberto");
  });
});

describe("attentionQueue", () => {
  it("splits the queue into action and watch, action first", () => {
    const queue = attentionQueue(status().attention);
    expect(queue.action).toHaveLength(2);
    expect(queue.watch).toHaveLength(1);
    expect(queue.total).toBe(3);
    expect(queue.action.every((row) => row.severity === "ACTION")).toBe(true);
    expect(queue.watch.every((row) => row.severity === "WATCH")).toBe(true);
  });

  it("deep-links each finding to the surface that owns it", () => {
    const queue = attentionQueue([
      attention({ kind: "PENDING_TRANSFER" }),
      attention({ kind: "STAGED_IMPORT", object: "IMPORT", params: { age_days: 2 } }),
      attention({ kind: "STAGED_EDITION", object: "STRATEGY", params: { entry_count: 12 } }),
      attention({
        kind: "CORPORATE_ACTION_REVIEW",
        object: "CORPORATE_ACTION",
        params: { age_days: 4 },
      }),
      attention({
        kind: "UNCLASSIFIED_TICKER",
        object: "ASSET",
        ref: { type: "TICKER", ticker: "XPTY3" },
        params: { quantity: 20 },
      }),
      attention({ kind: "OPEN_GAP", object: "ASSET", params: { quantity: 50, age_days: 9 } }),
      attention({
        kind: "STALE_QUOTE",
        object: "ASSET",
        severity: "WATCH",
        params: { days_old: 6, close: 60 },
      }),
    ]);
    expect(queue.action.map((row) => row.to)).toEqual([
      ROUTES.planTransfers,
      ROUTES.imports,
      ROUTES.strategies,
      ROUTES.corporateActions,
      "/ativos/positions/XPTY3",
      ROUTES.gaps,
    ]);
    expect(queue.watch[0]?.to).toBe(ROUTES.quotes);
  });

  it("writes the pending transfer sentence from the typed params", () => {
    const row = attentionQueue([attention({})]).action[0];
    expect(row?.title).toBe("Transferência pendente");
    expect(row?.detail).toContain("WEGE3");
    expect(row?.detail).toContain("50");
    expect(row?.detail).toContain("R$ 1.500,00");
  });

  it("returns empty groups for a resolved queue", () => {
    expect(attentionQueue([])).toEqual({ action: [], watch: [], total: 0 });
  });
});

describe("dataStatePanel", () => {
  it("lists quotes as-of, each stale ticker, the snapshot, open gaps and the last close", () => {
    const panel = dataStatePanel(status());
    expect(panel.empty).toBe(false);
    expect(panel.rows.map((row) => row.label)).toEqual([
      "Cotações",
      "VALE3",
      "Snapshot",
      "Lacunas",
      "Último fechamento",
    ]);
    expect(panel.rows[0]?.value).toBe("30/10/2026");
    expect(panel.rows[1]?.value).toContain("6 dias");
    expect(panel.rows[1]?.tone).toBe("attention");
    expect(panel.rows[2]?.value).toBe("out/2026 · congelado");
    expect(panel.rows[3]?.value).toBe("1 aberta");
    expect(panel.rows[4]?.value).toBe("out/2026");
  });

  it("is empty when the aggregate carries no data state", () => {
    const panel = dataStatePanel(
      status({
        last_close: undefined,
        data: { quotes_as_of: undefined, stale_quotes: [], open_gaps: 0 },
      }),
    );
    expect(panel.empty).toBe(true);
  });
});

describe("resultsPanel", () => {
  it("keeps only the last twelve months of the sparkline", () => {
    const months = Array.from({ length: 13 }, (_, index) =>
      monthPoint(periodAt(index), index / 10),
    );
    const panel = resultsPanel(series(months), undefined);
    expect(panel.bars).toHaveLength(12);
    expect(panel.bars[0]?.period).toBe("2025-11");
  });

  it("states the last month against the benchmark, pro Ventos, DARF and XIRR", () => {
    const panel = resultsPanel(series(), tax());
    expect(panel.period).toBe("out/2026");
    const byKey = Object.fromEntries(panel.rows.map((row) => [row.key, row]));
    expect(byKey.last?.value).toBe("+0,12%");
    expect(byKey.last?.hint).toBe("vs +1,75%");
    expect(byKey.income?.value).toBe("R$ 3.200,00");
    expect(byKey.darf?.value).toBe("R$ 255,00");
    expect(byKey.darf?.hint).toBe("vence 30/11/2026");
    expect(byKey.xirr?.value).toBe("+4,10%");
  });

  it("is empty before the first close", () => {
    expect(resultsPanel(series([]), undefined).empty).toBe(true);
  });
});

describe("monthStandfirst", () => {
  it("writes one sentence with the month, benchmark, cash, deficit, drift and transfer inline", () => {
    const parts = monthStandfirst(status(), series());
    const text = parts.map((part) => part.text).join("");
    expect(text).toContain("out/2026 fechou em ");
    expect(text).toContain("+0,12%");
    expect(text).toContain("atrás do benchmark de +1,75%");
    expect(text).toContain("nov/2026 abre com R$ 30.000,00 de caixa e R$ 20.000,00 a alocar");
    expect(text).toContain("2,5 p.p. abaixo dos alvos");
    expect(text).toContain("a transferência de WEGE3 espera decisão");
    expect(text.endsWith(".")).toBe(true);
  });

  it("states the empty portfolio without inventing a month", () => {
    const text = monthStandfirst(
      status({
        open_month: undefined,
        month: {
          open: false,
          steps_done: 0,
          steps_total: 6,
          suggested_orders: 0,
          pending_transfers: 0,
        },
        attention: [],
      }),
      series([]),
    );
    expect(text.map((part) => part.text).join("")).toBe(
      "Nenhum mês foi fechado e nenhum mês está aberto.",
    );
  });
});
