import { describe, expect, it } from "vitest";
import type { Allocation, Income, Month, OrderList, OrderPlan, Trade } from "../api/types.ts";
import {
  type RitualInput,
  allocationStrip,
  attentionItems,
  cockpitKpis,
  editionCoverage,
  ritualSteps,
  upcomingIncome,
} from "./cockpit.ts";

function month(overrides: Partial<Month> = {}): Month {
  return {
    id: 1,
    period: "2026-10",
    capital: 100_000,
    floor: 20_000,
    status: "OPEN",
    aportes: 0,
    ...overrides,
  };
}

function allocation(overrides: Partial<Allocation> = {}): Allocation {
  return {
    period: "2026-10",
    capital: 100_000,
    floor: 20_000,
    ideal: 100_000,
    actual: 80_000,
    deficit: 20_000,
    drift: 0,
    unattributed: 0,
    classes: [
      {
        asset_class: "ACOES",
        target: 50,
        ideal: 50_000,
        actual: 55_000,
        deficit: -5_000,
        drift: 5,
        unattributed: 0,
        strategies: [],
        sub_classes: [],
      },
      {
        asset_class: "FIIS",
        target: 30,
        ideal: 30_000,
        actual: 20_000,
        deficit: 10_000,
        drift: -10,
        unattributed: 0,
        strategies: [],
        sub_classes: [],
      },
      {
        asset_class: "RENDA_FIXA",
        target: 20,
        ideal: 20_000,
        actual: 5_000,
        deficit: 15_000,
        drift: -15,
        unattributed: 0,
        strategies: [],
        sub_classes: [],
      },
    ],
    ...overrides,
  };
}

function orderPlan(overrides: Partial<OrderPlan> = {}): OrderPlan {
  return {
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
      unfunded_amount: 0,
      neutro_deficit: 0,
    },
    outside_strategy: [],
    unclassified: [],
    attribution: [],
    transfers: [],
    ...overrides,
  };
}

function orderList(overrides: Partial<OrderList> = {}): OrderList {
  return {
    period: "2026-10",
    orders: [],
    transfers: [],
    unplanned_trades: [],
    ...overrides,
  };
}

function suggestedOrder(): OrderList["orders"][number] {
  return {
    id: 1,
    ticker: "ITUB4",
    side: "BUY",
    quantity: 10,
    executed_quantity: 0,
    status: "SUGGESTED",
  };
}

function trade(date: string): Trade {
  return {
    id: 1,
    ticker: "ITUB4",
    date,
    venue: "B3",
    side: "BUY",
    quantity: 10,
    unit_price: 30,
    institution_id: 1,
    source: "B3_IMPORT",
    confirmed_by_b3: true,
    ordinal: 1,
  };
}

function income(date: string, status: Income["status"] = "RECONCILED"): Income {
  return {
    id: 1,
    ticker: "ITUB4",
    date,
    type: "DIVIDEND",
    gross_amount: 100,
    retained_amount: 0,
    net_amount: 100,
    tax_nature: "INCOME",
    provenance: "B3",
    status,
  };
}

function ritualInput(overrides: Partial<RitualInput> = {}): RitualInput {
  return {
    month: month(),
    orders: orderList({ orders: [suggestedOrder()] }),
    trades: [trade("2026-10-03")],
    coverage: [
      { name: "Top Dividendos", hasEdition: true },
      { name: "Small Caps", hasEdition: true },
    ],
    income: [income("2026-10-15")],
    upcoming: [income("2026-11-03", "ANNOUNCED")],
    ...overrides,
  };
}

describe("cockpitKpis", () => {
  it("reads Capital, the residual Caixa and Investable Capital off the month", () => {
    expect(cockpitKpis(month(), allocation())).toEqual({
      capital: 100_000,
      caixa: 20_000,
      investable: 80_000,
      status: "OPEN",
    });
  });

  it("leaves Caixa undefined until the allocation is readable", () => {
    expect(cockpitKpis(month(), null).caixa).toBeUndefined();
  });

  it("rounds the residual to cents", () => {
    const result = cockpitKpis(month({ capital: 100_000.1 }), allocation({ actual: 80_000.02 }));
    expect(result.caixa).toBe(20_000.08);
  });
});

describe("ritualSteps", () => {
  it("always returns the seven steps in ritual order", () => {
    const steps = ritualSteps(ritualInput());
    expect(steps.map((step) => step.number)).toEqual([1, 2, 3, 4, 5, 6, 7]);
    expect(steps[0]?.title).toBe("Registrar edições da XP");
    expect(steps[6]?.title).toBe("Fechar o mês");
  });

  it("marks the month ready to close when every earlier step is complete", () => {
    const steps = ritualSteps(ritualInput());
    expect(steps.map((step) => step.status)).toEqual([
      "complete",
      "complete",
      "complete",
      "complete",
      "complete",
      "complete",
      "pending",
    ]);
  });

  it("keeps step 1 pending until every strategy has an edition", () => {
    const steps = ritualSteps(
      ritualInput({
        coverage: [
          { name: "Top Dividendos", hasEdition: true },
          { name: "Small Caps", hasEdition: false },
        ],
      }),
    );
    expect(steps[0]?.status).toBe("pending");
    expect(steps[0]?.detail).toContain("1 de 2");
  });

  it("completes step 1 when there is no report-driven strategy", () => {
    const steps = ritualSteps(ritualInput({ coverage: [] }));

    expect(steps[0]?.status).toBe("complete");
    expect(steps[0]?.detail).toBe("Nenhuma estratégia com relatório");
  });

  it("keeps step 2 pending until a plan is persisted for the month", () => {
    const steps = ritualSteps(ritualInput({ orders: null }));
    expect(steps[1]?.status).toBe("pending");
    expect(steps[3]?.status).toBe("pending");
  });

  it("does not take the empty order read for a persisted plan", () => {
    const steps = ritualSteps(ritualInput({ orders: orderList() }));
    expect(steps[1]?.status).toBe("pending");
    expect(steps[2]?.status).toBe("pending");
    expect(steps[3]?.status).toBe("pending");
    expect(steps[1]?.detail).toContain("não recalculado");
  });

  it("counts a transfer-only plan as persisted", () => {
    const steps = ritualSteps(
      ritualInput({
        orders: orderList({
          transfers: [
            {
              id: 1,
              ticker: "ITUB4",
              from_strategy_id: 1,
              from_strategy: "Top Dividendos",
              to_strategy_id: 2,
              to_strategy: "Small Caps",
              quantity: 10,
              auto_approved: true,
              status: "APPROVED",
            },
          ],
        }),
      }),
    );
    expect(steps[1]?.status).toBe("complete");
    expect(steps[3]?.status).toBe("complete");
  });

  it("calls attention to the transfer inbox and blocks the close", () => {
    const steps = ritualSteps(
      ritualInput({
        orders: orderList({
          transfers: [
            {
              id: 1,
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
        }),
      }),
    );
    expect(steps[2]?.status).toBe("attention");
    expect(steps[2]?.detail).toContain("1");
    expect(steps[3]?.status).toBe("pending");
    expect(steps[6]?.status).toBe("attention");
    expect(steps[6]?.blocking).toBe(true);
    expect(steps[6]?.detail).toContain("Bloqueado");
  });

  it("keeps step 5 pending when no B3 trade landed in the month", () => {
    const steps = ritualSteps(ritualInput({ trades: [trade("2026-09-28")] }));
    expect(steps[4]?.status).toBe("pending");
    expect(steps[4]?.detail).toContain("Nenhum");
  });

  it("keeps step 6 pending while an income event is not reconciled", () => {
    const steps = ritualSteps(
      ritualInput({
        income: [income("2026-10-15", "RECEIVED"), income("2026-10-20")],
      }),
    );
    expect(steps[5]?.status).toBe("pending");
    expect(steps[5]?.detail).toContain("1 de 2");
  });

  it("marks the close complete once the month is closed", () => {
    const steps = ritualSteps(ritualInput({ month: month({ status: "CLOSED" }) }));
    expect(steps[6]?.status).toBe("complete");
  });
});

describe("attentionItems", () => {
  it("reports nothing when the book is clean", () => {
    const clean = allocation({
      classes: allocation().classes.map((rung) => ({
        ...rung,
        drift: 0,
        unattributed: 0,
      })),
    });
    expect(attentionItems({ allocation: clean, plan: orderPlan() })).toEqual([]);
  });

  it("flags unattributed units as critical and names the classes", () => {
    const classes = allocation().classes.map((rung) =>
      rung.asset_class === "ACOES" ? { ...rung, unattributed: 1_500 } : rung,
    );
    const items = attentionItems({
      allocation: allocation({ classes }),
      plan: null,
    });
    const critical = items.find((item) => item.severity === "critical");
    expect(critical?.text).toContain("Ações");
    expect(critical?.to).toBe("/trades");
  });

  it("flags a root-level unattributed holding with no class rung", () => {
    const classes = allocation().classes.map((rung) => ({
      ...rung,
      unattributed: 0,
    }));
    const items = attentionItems({
      allocation: allocation({ classes, unattributed: 500 }),
      plan: null,
    });
    expect(items.find((item) => item.key === "unattributed")?.text).toContain("sem classe");
  });

  it("reports the largest class drift above one point", () => {
    const items = attentionItems({
      allocation: allocation(),
      plan: orderPlan(),
    });
    const drift = items.find((item) => item.key === "drift");
    expect(drift?.severity).toBe("warning");
    expect(drift?.text).toContain("Renda fixa");
  });

  it("reports unfunded and NEUTRO funding gaps from the plan", () => {
    const items = attentionItems({
      allocation: allocation(),
      plan: orderPlan({
        funding: {
          buy_amount: 10_000,
          sell_amount: 5_000,
          unfunded_amount: 5_000,
          neutro_deficit: 2_000,
        },
      }),
    });
    expect(items.find((item) => item.key === "unfunded")?.severity).toBe("warning");
    expect(items.find((item) => item.key === "neutro")?.severity).toBe("warning");
  });

  it("notes an outside-strategy holding with no quote", () => {
    const items = attentionItems({
      allocation: allocation(),
      plan: orderPlan({
        outside_strategy: [{ ticker: "ORVR3", quantity: 10, suggested_exit_quantity: 10 }],
      }),
    });
    expect(items.find((item) => item.key === "outside:ORVR3")?.text).toContain("ORVR3");
  });
});

describe("editionCoverage", () => {
  it("pairs each strategy with its editions for the open month", () => {
    expect(
      editionCoverage(
        [{ name: "Top Dividendos" }, { name: "Small Caps" }],
        [[{ reference_month: "2026-10" }], [{ reference_month: "2026-09" }]],
        "2026-10",
      ),
    ).toEqual([
      { name: "Top Dividendos", hasEdition: true },
      { name: "Small Caps", hasEdition: false },
    ]);
  });

  it("treats an unloaded or empty editions list as no edition", () => {
    expect(editionCoverage([{ name: "Small Caps" }], [], "2026-10")).toEqual([
      { name: "Small Caps", hasEdition: false },
    ]);
  });
});

describe("allocationStrip", () => {
  it("sizes each class by its share of Capital and marks the cumulative target", () => {
    const strip = allocationStrip(allocation());
    expect(strip.map((segment) => segment.actualPercent)).toEqual([55, 20, 5]);
    expect(strip.map((segment) => segment.idealPercent)).toEqual([50, 80, 100]);
    expect(strip.map((segment) => segment.assetClass)).toEqual(["ACOES", "FIIS", "RENDA_FIXA"]);
  });

  it("caps a book that is worth more than its Capital at 100%", () => {
    const strip = allocationStrip(
      allocation({
        actual: 120_000,
        classes: allocation().classes.map((rung) => ({
          ...rung,
          actual: 40_000,
        })),
      }),
    );
    expect(strip.reduce((sum, segment) => sum + segment.actualPercent, 0)).toBe(100);
  });

  it("returns an empty strip without an allocation", () => {
    expect(allocationStrip(null)).toEqual([]);
  });
});

describe("upcomingIncome", () => {
  it("keeps only payments inside the window, soonest first", () => {
    const rows = upcomingIncome(
      [
        income("2026-11-20"),
        income("2026-10-24"),
        income("2026-10-23"),
        income("2026-12-01"),
        income("2026-11-02"),
      ],
      "2026-10-24",
    );
    expect(rows.map((row) => row.date)).toEqual(["2026-10-24", "2026-11-02", "2026-11-20"]);
  });

  it("honours a custom window", () => {
    const rows = upcomingIncome([income("2026-10-30")], "2026-10-24", 7);
    expect(rows.map((row) => row.date)).toEqual(["2026-10-30"]);
  });
});
