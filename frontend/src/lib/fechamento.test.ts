import { describe, expect, it } from "vitest";
import type { OrderList, Snapshot, TransferProposal } from "../api/types.ts";
import { closeButtonLabel, closedMonthSummary, pendingTransfers } from "./fechamento.ts";

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

describe("closeButtonLabel", () => {
  it("invites the close when nothing pends", () => {
    expect(closeButtonLabel(0)).toBe("Fechar mês");
  });

  it("names the pending count in the singular", () => {
    expect(closeButtonLabel(1)).toBe("Bloqueado — 1 transferência pendente");
  });

  it("names the pending count in the plural", () => {
    expect(closeButtonLabel(3)).toBe("Bloqueado — 3 transferências pendentes");
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
