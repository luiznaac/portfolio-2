import { describe, expect, it } from "vitest";
import type {
  CorporateAction,
  Gap,
  Income,
  OrderList,
  Snapshot,
  StaleQuote,
  SuggestedOrder,
  TransferProposal,
} from "../api/types.ts";
import {
  closeGateView,
  closeNonBlockingItems,
  closedMonthSummary,
  decidedTransfers,
  pendingTransfers,
  snapshotOrderList,
} from "./fechamento.ts";

const pending: TransferProposal = {
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
};

function orderList(transfers: TransferProposal[]): OrderList {
  return { period: "2026-10", orders: [], transfers, unplanned_trades: [] };
}

describe("pendingTransfers", () => {
  it("keeps only the undecided proposals", () => {
    const approved = { ...pending, id: 8, status: "APPROVED" as const };
    const rejected = { ...pending, id: 9, status: "REJECTED" as const };

    expect(pendingTransfers(orderList([pending, approved, rejected]))).toEqual([pending]);
  });

  it("treats a month with no persisted plan as having nothing pending", () => {
    expect(pendingTransfers(null)).toEqual([]);
    expect(pendingTransfers(undefined)).toEqual([]);
  });
});

describe("decidedTransfers", () => {
  it("keeps only the proposals that carry a decision", () => {
    const approved = { ...pending, id: 8, status: "APPROVED" as const };
    const rejected = { ...pending, id: 9, status: "REJECTED" as const };

    expect(decidedTransfers(orderList([pending, approved, rejected]))).toEqual([
      approved,
      rejected,
    ]);
  });

  it("treats a month with no persisted plan as having nothing decided", () => {
    expect(decidedTransfers(null)).toEqual([]);
    expect(decidedTransfers(undefined)).toEqual([]);
  });
});

describe("closedMonthSummary", () => {
  it("reads the frozen capital, floor, caixa, income and executed-order count", () => {
    const snapshot = {
      id: 3,
      month_id: 9,
      created_at: "2026-09-30T18:05:00",
      state: {
        period: "2026-09",
        capital: 240_000,
        floor: 20_000,
        aportes: 2_000,
        caixa: 45_000,
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
            {
              id: 3,
              ticker: "VALE3",
              side: "BUY",
              quantity: 10,
              executed_quantity: 0,
              status: "SUGGESTED",
            },
          ],
          transfers: [],
          unplanned_trades: [],
        },
        tax: {
          carry: [],
          meter: {
            gross_sales: 0,
            exemption_limit: 20_000,
            exceeded: false,
            realized_gain: 0,
            taxable_gain: 0,
            rate: 15,
            tax: 0,
          },
        },
      },
    } satisfies Snapshot;

    expect(closedMonthSummary(snapshot)).toEqual({
      monthId: 9,
      period: "2026-09",
      capital: 240_000,
      floor: 20_000,
      caixa: 45_000,
      incomeNet: 85,
      executedOrders: 2,
      closedAt: "30/09/2026 18:05",
    });
  });
});

function order(overrides: Partial<SuggestedOrder> = {}): SuggestedOrder {
  return {
    id: 1,
    ticker: "PETR4",
    side: "BUY",
    quantity: 10,
    suggested_price: 10,
    executed_quantity: 0,
    status: "SUGGESTED",
    ...overrides,
  };
}

function gap(status: Gap["status"]): Gap {
  return {
    id: 1,
    ticker: "PETR4",
    date: "2026-10-20",
    quantity: 40,
    status,
    trade_watermark: 0,
    action_watermark: 0,
  };
}

function corporateAction(status: CorporateAction["status"]): CorporateAction {
  return {
    id: 1,
    ticker: "ITUB4",
    kind: "SPLIT",
    ex_date: "2026-10-15",
    source: "B3_MOVEMENTS",
    ordinal: 1,
    status,
    is_applied: status === "APPLIED",
  };
}

function income(overrides: Partial<Income> = {}): Income {
  return {
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
    ...overrides,
  };
}

function stale(ticker: string): StaleQuote {
  return { ticker, date: "2026-09-30", close: 10, days_old: 30 };
}

describe("closeGateView", () => {
  const open = { hasOpenMonth: true, backdated: false, gateReady: true, pendingCount: 0 };

  it("states a pending transfer while letting the refresh-first attempt run", () => {
    const view = closeGateView({ ...open, pendingCount: 2 });

    expect(view.state).toBe("pending");
    expect(view.blocked).toBe(true);
    // The attempt must reach the server: its refresh is what finalises the transfers before the
    // gate decides, so a stale pending row must not disable the button.
    expect(view.canClose).toBe(true);
    expect(view.title).toBe("Fechamento bloqueado: transferência pendente");
    expect(view.detail).toContain("recalcula o plano antes de decidir");
    expect(view.detail).toContain("autoaprovando as abaixo de R$ 500");
    expect(view.detail).toContain("Se sobrar uma pendente, o fechamento é recusado");
  });

  it("releases the gate when no proposal is pending — a rejection is a decision", () => {
    const view = closeGateView({ ...open, pendingCount: 0 });

    expect(view.state).toBe("ready");
    expect(view.blocked).toBe(false);
    expect(view.canClose).toBe(true);
    expect(view.title).toBe("Pronto para fechar");
  });

  it("refuses a backdated month before the close writes", () => {
    const view = closeGateView({ ...open, backdated: true });

    expect(view.state).toBe("backdated");
    expect(view.blocked).toBe(true);
    expect(view.canClose).toBe(false);
    expect(view.title).toBe("Só o mês atual pode ser fechado");
  });

  it("waits for the transfer inbox read before enabling the close", () => {
    const view = closeGateView({ ...open, gateReady: false });

    expect(view.state).toBe("checking");
    expect(view.blocked).toBe(true);
    expect(view.canClose).toBe(false);
  });

  it("offers the opener when no month is open", () => {
    const view = closeGateView({ ...open, hasOpenMonth: false });

    expect(view.state).toBe("no-month");
    expect(view.blocked).toBe(true);
    expect(view.canClose).toBe(false);
    expect(view.title).toBe("Nenhum mês aberto");
  });
});

describe("closeNonBlockingItems", () => {
  it("lists the five frozen states with their counts and the non-blocking label", () => {
    const items = closeNonBlockingItems({
      period: "2026-10",
      orders: {
        period: "2026-10",
        orders: [
          order({ id: 1, status: "EXECUTED", executed_quantity: 10 }),
          order({ id: 2, executed_quantity: 4 }),
          order({ id: 3 }),
        ],
        transfers: [],
        unplanned_trades: [],
      },
      gaps: [gap("OPEN"), gap("REPLACED")],
      corporateActions: [corporateAction("NEEDS_REVIEW"), corporateAction("APPLIED")],
      staleQuotes: [stale("VALE3")],
      incomes: [
        income({ id: 1, status: "ANNOUNCED" }),
        income({ id: 2, status: "PAYABLE" }),
        income({ id: 3, status: "RECEIVED" }),
        income({ id: 4, status: "ANNOUNCED", date: "2026-11-05" }),
      ],
    });

    expect(items.map((item) => item.key)).toEqual([
      "orders",
      "gaps",
      "corporate-actions",
      "stale-quotes",
      "receivable-income",
    ]);
    expect(items.map((item) => item.count)).toEqual([2, 1, 1, 1, 2]);
    expect(items.every((item) => item.badge === "não trava")).toBe(true);
    expect(items.map((item) => item.to)).toEqual([
      "/plan/orders",
      "/ativos/gaps",
      "/imports/corporate",
      "/ativos/quotes",
      "/analysis/income",
    ]);
    expect(items[0].detail).toBe("2 ordens · 1 parcial");
    expect(items[1].detail).toBe("1 aberta");
    expect(items[4].detail).toBe("2 a receber");
  });

  it("still lists every state as non-blocking when nothing is frozen", () => {
    const items = closeNonBlockingItems({
      period: "2026-10",
      orders: orderList([]),
      gaps: [],
      corporateActions: [],
      staleQuotes: [],
      incomes: [],
    });

    expect(items).toHaveLength(5);
    expect(items.every((item) => item.count === 0)).toBe(true);
    expect(items.map((item) => item.detail)).toEqual([
      "nenhuma",
      "nenhuma aberta",
      "nenhuma em revisão",
      "nenhuma desatualizada",
      "nenhum a receber",
    ]);
  });
});

describe("snapshotOrderList", () => {
  it("reads the frozen orders, transfers and unplanned trades from the snapshot", () => {
    const snapshot = {
      id: 1,
      month_id: 9,
      created_at: "2026-10-31T18:00:00",
      state: {
        period: "2026-10",
        capital: 100_000,
        floor: 0,
        aportes: 0,
        caixa: 0,
        holdings: [],
        attributions: [],
        classes: [],
        income: { gross: 0, retained: 0, net: 0 },
        orders: {
          orders: [order({ id: 1, status: "IMPORTED", executed_quantity: 10 })],
          transfers: [pending],
          unplanned_trades: [{ id: 9, ticker: "ZZZZ9", side: "BUY", quantity: 5 }],
        },
        tax: {
          carry: [],
          meter: {
            gross_sales: 0,
            exemption_limit: 20_000,
            exceeded: false,
            realized_gain: 0,
            taxable_gain: 0,
            rate: 15,
            tax: 0,
          },
        },
      },
    } satisfies Snapshot;

    expect(snapshotOrderList(snapshot)).toEqual({
      period: "2026-10",
      orders: snapshot.state.orders.orders,
      transfers: [pending],
      unplanned_trades: snapshot.state.orders.unplanned_trades,
    });
  });
});
