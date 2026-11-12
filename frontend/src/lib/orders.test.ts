import { describe, expect, it } from "vitest";
import type { OrderPlan, Strategy, SuggestedOrder, TransferProposal } from "../api/types.ts";
import {
  fundingSourceLabel,
  groupOrders,
  isBuySide,
  isSellSide,
  orderClipboardText,
  orderContextKey,
  orderContexts,
  orderExecutionState,
  orderExecutionSummary,
  orderProgressLabel,
  orderSideLabel,
  orderStateLabel,
  orderStatusLabel,
  orderTotals,
  ordersCsv,
  planCoversOrder,
  toTradeId,
  transferCopy,
  transferSourceIsBucket,
  transferStatusLabel,
} from "./orders.ts";

function order(overrides: Partial<SuggestedOrder> = {}): SuggestedOrder {
  return {
    id: 1,
    ticker: "ITUB4",
    side: "BUY",
    quantity: 70,
    suggested_price: 10,
    executed_quantity: 70,
    status: "EXECUTED",
    ...overrides,
  };
}

describe("labels", () => {
  it("maps the order side to its pt-BR label", () => {
    expect(orderSideLabel("BUY")).toBe("Compra");
    expect(orderSideLabel("SELL")).toBe("Venda");
    expect(orderSideLabel("NEW_ENTRY")).toBe("Nova entrada");
    expect(orderSideLabel("EXIT")).toBe("Saída total");
  });

  it("maps the lifecycle state to its pt-BR chip label", () => {
    expect(orderStatusLabel("SUGGESTED")).toBe("Sugerido");
    expect(orderStatusLabel("EXECUTED")).toBe("Executado");
    expect(orderStatusLabel("IMPORTED")).toBe("Importado");
  });

  it("maps the transfer decision to its pt-BR label", () => {
    expect(transferStatusLabel("PENDING")).toBe("Pendente");
    expect(transferStatusLabel("APPROVED")).toBe("Aprovada");
    expect(transferStatusLabel("REJECTED")).toBe("Rejeitada");
  });
});

describe("side partition", () => {
  it("treats a new entry as a buy and an exit as a sell", () => {
    expect(isBuySide("BUY")).toBe(true);
    expect(isBuySide("NEW_ENTRY")).toBe(true);
    expect(isSellSide("SELL")).toBe(true);
    expect(isSellSide("EXIT")).toBe(true);
    expect(isBuySide("SELL")).toBe(false);
    expect(isSellSide("NEW_ENTRY")).toBe(false);
  });
});

describe("toTradeId", () => {
  it("accepts a positive integer and rejects anything else", () => {
    expect(toTradeId("42")).toBe(42);
    expect(toTradeId(" 7 ")).toBe(7);
    expect(toTradeId("")).toBeNull();
    expect(toTradeId("0")).toBeNull();
    expect(toTradeId("-3")).toBeNull();
    expect(toTradeId("4.5")).toBeNull();
    expect(toTradeId("abc")).toBeNull();
  });
});

describe("groupOrders", () => {
  it("groups the list in the reference's out-to-in order and skips empty groups", () => {
    const groups = groupOrders([
      order({ id: 1, side: "BUY", ticker: "VALE3" }),
      order({ id: 2, side: "EXIT", ticker: "MXRF11" }),
      order({ id: 3, side: "SELL", ticker: "ITSA4" }),
      order({ id: 4, side: "NEW_ENTRY", ticker: "B3SA3" }),
    ]);

    expect(groups.map((group) => group.label)).toEqual([
      "Saídas totais",
      "Vendas",
      "Compras",
      "Novas entradas",
    ]);
    expect(groups.map((group) => group.orders.map((row) => row.ticker))).toEqual([
      ["MXRF11"],
      ["ITSA4"],
      ["VALE3"],
      ["B3SA3"],
    ]);
  });

  it("omits a side the plan does not emit", () => {
    const groups = groupOrders([order({ side: "BUY" })]);

    expect(groups).toHaveLength(1);
    expect(groups[0].label).toBe("Compras");
  });
});

describe("orderTotals", () => {
  it("sums the buy and sell amounts from quantity times the reference price", () => {
    const totals = orderTotals([
      order({ side: "BUY", quantity: 10, suggested_price: 20 }),
      order({ side: "NEW_ENTRY", quantity: 5, suggested_price: 30 }),
      order({ side: "SELL", quantity: 2, suggested_price: 50 }),
      order({ side: "EXIT", quantity: 3, suggested_price: 10 }),
    ]);

    expect(totals.buys).toBe(350);
    expect(totals.sells).toBe(130);
    expect(totals.cash).toBe(220);
  });

  it("ignores an unpriced order instead of inventing a value", () => {
    const totals = orderTotals([order({ side: "BUY", suggested_price: undefined })]);

    expect(totals).toEqual({ buys: 0, sells: 0, cash: 0 });
  });
});

describe("orderClipboardText", () => {
  it("writes one broker-friendly line per order", () => {
    const text = orderClipboardText([
      order({
        side: "BUY",
        ticker: "ITUB4",
        quantity: 70,
        suggested_price: 10,
      }),
      order({
        id: 2,
        side: "EXIT",
        ticker: "MXRF11",
        quantity: 300,
        suggested_price: 9.44,
      }),
    ]);

    expect(text.split("\n")).toEqual([
      "Compra ITUB4 70 @ R$ 10,00",
      "Saída total MXRF11 300 @ R$ 9,44",
    ]);
  });
});

describe("ordersCsv", () => {
  it("exports a semicolon-delimited table with a header and one row per order", () => {
    const csv = ordersCsv([
      order({
        side: "BUY",
        ticker: "ITUB4",
        quantity: 70,
        suggested_price: 10,
        status: "EXECUTED",
      }),
    ]);

    expect(csv.split("\n")).toEqual([
      "Ticker;Lado;Quantidade;Preco;Valor;Status;Executado",
      "ITUB4;Compra;70;10.00;700.00;Executado;70",
    ]);
  });

  it("quotes a field that carries the delimiter", () => {
    const csv = ordersCsv([order({ ticker: "IT;UB4" })]);

    expect(csv.split("\n")[1]?.startsWith('"IT;UB4"')).toBe(true);
  });
});

describe("transfer copy", () => {
  const strategies: Strategy[] = [
    { id: 1, name: "Top", asset_class: "ACOES", kind: "REPORT_DRIVEN" },
    { id: 2, name: "Legado", asset_class: "ACOES", kind: "MANUAL" },
  ];

  it("resolves the bucket source from the strategies catalog", () => {
    expect(transferSourceIsBucket(transfer({ from_strategy_id: 2 }), strategies)).toBe(true);
    expect(transferSourceIsBucket(transfer({ from_strategy_id: 1 }), strategies)).toBe(false);
    // An unknown source is not the bucket, and an unresolved catalog is not a verdict.
    expect(transferSourceIsBucket(transfer({ from_strategy_id: 99 }), strategies)).toBe(false);
    expect(transferSourceIsBucket(transfer({ from_strategy_id: 2 }), undefined)).toBeUndefined();
  });

  it("keeps 'evita vender' for report-to-report and reads 'move' for the bucket", () => {
    expect(transferCopy(transfer({ amount: 600, from_strategy_id: 1 }), strategies)).toBe(
      "evita vender R$ 600,00",
    );
    expect(transferCopy(transfer({ amount: 100, from_strategy_id: 2 }), strategies)).toBe(
      "move R$ 100,00 do legado",
    );
    expect(transferCopy(transfer({ amount: undefined }), strategies)).toBe("sem cotação");
    // While the catalog loads there is no honest source kind, so no caption is claimed.
    expect(transferCopy(transfer({ amount: 100 }), undefined)).toBeUndefined();
  });
});

function transfer(overrides: Partial<TransferProposal> = {}): TransferProposal {
  return {
    id: 1,
    ticker: "ITUB4",
    from_strategy_id: 1,
    from_strategy: "Top",
    to_strategy_id: 2,
    to_strategy: "Small",
    quantity: 10,
    amount: 100,
    auto_approved: false,
    status: "PENDING",
    ...overrides,
  };
}

describe("order execution state", () => {
  it("labels a partial execution PARCIAL from executed_quantity and leaves the persisted status alone", () => {
    const partial = order({ status: "SUGGESTED", executed_quantity: 4 });

    expect(orderExecutionState(partial)).toBe("PARTIAL");
    expect(orderStateLabel(partial)).toBe("PARCIAL");
    // PARCIAL is display-only: the persisted lifecycle state is never rewritten.
    expect(partial.status).toBe("SUGGESTED");
  });

  it("exports the persisted Sugerido status while the screen shows PARCIAL", () => {
    const partial = order({ status: "SUGGESTED", executed_quantity: 4 });

    expect(orderStateLabel(partial)).toBe("PARCIAL");
    expect(ordersCsv([partial]).split("\n")[1]?.split(";")[5]).toBe("Sugerido");
  });

  it("derives executed and unexecuted states from the executed quantity", () => {
    expect(orderExecutionState(order({ status: "EXECUTED", executed_quantity: 70 }))).toBe(
      "EXECUTED",
    );
    expect(orderExecutionState(order({ status: "IMPORTED", executed_quantity: 70 }))).toBe(
      "EXECUTED",
    );
    expect(orderExecutionState(order({ status: "SUGGESTED", executed_quantity: 0 }))).toBe(
      "UNEXECUTED",
    );
  });

  it("renders the executed ratio over the suggested quantity", () => {
    expect(orderProgressLabel(order({ quantity: 24, executed_quantity: 6 }))).toBe("6/24");
  });

  it("keeps the persisted label for a fully executed or untouched order", () => {
    expect(orderStateLabel(order({ status: "EXECUTED", executed_quantity: 70 }))).toBe("Executado");
    expect(orderStateLabel(order({ status: "IMPORTED", executed_quantity: 70 }))).toBe("Importado");
    expect(orderStateLabel(order({ status: "SUGGESTED", executed_quantity: 0 }))).toBe("Sugerido");
  });

  it("splits a month's orders into executed, partial and unexecuted buckets", () => {
    const executed = order({ id: 1, status: "EXECUTED", executed_quantity: 70 });
    const partial = order({ id: 2, status: "SUGGESTED", executed_quantity: 4 });
    const untouched = order({ id: 3, status: "SUGGESTED", executed_quantity: 0 });

    const summary = orderExecutionSummary([executed, partial, untouched]);

    expect(summary.executed).toEqual([executed]);
    expect(summary.partial).toEqual([partial]);
    expect(summary.unexecuted).toEqual([untouched]);
  });
});

describe("order context", () => {
  it("derives each ticker's strategy moves from the plan's attribution ledger", () => {
    const contexts = orderContexts(
      plan({
        attribution: [
          { ticker: "VALE3", strategy: "Top Dividendos", side: "ALLOCATE", quantity: 16 },
          { ticker: "VALE3", strategy: "Small Caps", side: "ALLOCATE", quantity: 8 },
          { ticker: "ITSA4", strategy: "Top Dividendos", side: "DEALLOCATE", quantity: 90 },
        ],
      }),
    );

    expect(contexts.get(orderContextKey(order({ ticker: "VALE3", side: "BUY" })))).toEqual({
      contributions: [
        { strategy: "Top Dividendos", quantity: 16 },
        { strategy: "Small Caps", quantity: 8 },
      ],
    });
    expect(
      contexts.get(orderContextKey(order({ ticker: "ITSA4", side: "SELL" })))?.contributions,
    ).toEqual([{ strategy: "Top Dividendos", quantity: -90 }]);
  });

  it("reads a funding sell's source from the plan's candidate", () => {
    const contexts = orderContexts(
      plan({
        candidates: [
          {
            ticker: "ITSA4",
            side: "SELL",
            quantity: 90,
            price: 10,
            amount: 900,
            deficit: -100,
            ideal: 0,
            target_quantity: 0,
            position_quantity: 90,
            funding_source: "NEUTRO",
          },
        ],
      }),
    );

    const context = contexts.get(orderContextKey(order({ ticker: "ITSA4", side: "SELL" })));

    expect(context?.fundingSource).toBe("NEUTRO");
    expect(context?.contributions).toEqual([]);
    expect(fundingSourceLabel("NEUTRO")).toBe("Neutro");
    expect(fundingSourceLabel("OUTSIDE_STRATEGY")).toBe("Fora da estratégia");
    expect(fundingSourceLabel("SURPLUS")).toBe("Excedente");
  });

  it("has no context for an order the plan does not name", () => {
    const contexts = orderContexts(plan());

    expect(contexts.get(orderContextKey(order({ ticker: "ZZZZ9" })))).toBeUndefined();
  });

  it("keeps a buy's and a funding sell's context apart on the same ticker", () => {
    const contexts = orderContexts(
      plan({
        attribution: [
          { ticker: "VALE3", strategy: "Top Dividendos", side: "ALLOCATE", quantity: 16 },
          { ticker: "VALE3", strategy: "Dividendos", side: "DEALLOCATE", quantity: 40 },
        ],
        candidates: [
          {
            ticker: "VALE3",
            side: "SELL",
            quantity: 40,
            price: 10,
            amount: 400,
            deficit: -100,
            ideal: 0,
            target_quantity: 0,
            position_quantity: 40,
            funding_source: "NEUTRO",
          },
        ],
      }),
    );

    expect(contexts.get(orderContextKey(order({ ticker: "VALE3", side: "BUY" })))).toEqual({
      contributions: [{ strategy: "Top Dividendos", quantity: 16 }],
    });
    expect(contexts.get(orderContextKey(order({ ticker: "VALE3", side: "EXIT" })))).toEqual({
      contributions: [{ strategy: "Dividendos", quantity: -40 }],
      fundingSource: "NEUTRO",
    });
  });

  it("only covers a persisted order the computed plan still matches", () => {
    const planWithCandidate = plan({
      candidates: [
        {
          ticker: "VALE3",
          side: "BUY",
          quantity: 24,
          price: 10,
          amount: 240,
          deficit: 240,
          ideal: 10_000,
          target_quantity: 40,
          position_quantity: 16,
        },
      ],
    });

    expect(planCoversOrder(planWithCandidate, order({ ticker: "VALE3", quantity: 24 }))).toBe(true);
    expect(planCoversOrder(planWithCandidate, order({ ticker: "VALE3", quantity: 30 }))).toBe(
      false,
    );
    expect(planCoversOrder(planWithCandidate, order({ ticker: "VALE3", side: "SELL" }))).toBe(
      false,
    );
    expect(planCoversOrder(null, order({ ticker: "VALE3" }))).toBe(false);
  });
});

function plan(overrides: Partial<OrderPlan> = {}): OrderPlan {
  return {
    period: "2026-10",
    phase: "FUNDING",
    capital: 100_000,
    floor: 0,
    investable_capital: 100_000,
    classes: [],
    candidates: [],
    outside_strategy: [],
    unclassified: [],
    attribution: [],
    transfers: [],
    ...overrides,
  };
}
