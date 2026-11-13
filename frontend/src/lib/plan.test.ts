import { describe, expect, it } from "vitest";
import type { Month, OrderList, SuggestedOrder, Targets, TransferProposal } from "../api/types.ts";
import {
  PLAN_STEPS_TOTAL,
  chainPreview,
  doneStepCount,
  firstUnfinishedStep,
  planSteps,
} from "./plan.ts";

function month(overrides: Partial<Month> = {}): Month {
  return {
    id: 1,
    period: "2026-11",
    capital: 100_000,
    floor: 0,
    status: "OPEN",
    aportes: 0,
    ...overrides,
  };
}

function targets(overrides: Partial<Targets> = {}): Targets {
  return { classes: [], strategies: [], sub_classes: [], tickers: [], ...overrides };
}

function order(status: SuggestedOrder["status"], executed = 0): SuggestedOrder {
  return {
    id: 1,
    ticker: "ITUB4",
    side: "BUY",
    quantity: 10,
    suggested_price: 10,
    executed_quantity: executed,
    status,
  };
}

function transfer(status: TransferProposal["status"]): TransferProposal {
  return {
    id: 1,
    ticker: "ITUB4",
    from_strategy_id: 1,
    from_strategy: "Legado",
    to_strategy_id: 2,
    to_strategy: "Dividendos",
    quantity: 10,
    auto_approved: false,
    status,
  };
}

function orders(overrides: Partial<OrderList> = {}): OrderList {
  return { period: "2026-11", orders: [], transfers: [], unplanned_trades: [], ...overrides };
}

describe("planSteps", () => {
  it("opens on the first unfinished step once capital and targets exist", () => {
    const steps = planSteps({
      month: month(),
      targets: targets({ classes: [{ asset_class: "ACOES", percentage: 100 }] }),
      orders: null,
    });

    expect(steps.map((step) => step.key)).toEqual([
      "capital",
      "targets",
      "plan",
      "execution",
      "transfer",
      "close",
    ]);
    expect(doneStepCount(steps)).toBe(2);
    expect(firstUnfinishedStep(steps).key).toBe("plan");
  });

  it("opens on capital when the month has no capital yet", () => {
    const steps = planSteps({ month: month({ capital: 0 }), targets: targets(), orders: null });

    expect(doneStepCount(steps)).toBe(0);
    expect(firstUnfinishedStep(steps).key).toBe("capital");
  });

  it("counts a step only when its artefact exists", () => {
    const withTargets = targets({ classes: [{ asset_class: "ACOES", percentage: 100 }] });
    const persisted = orders({
      orders: [order("EXECUTED", 10)],
      transfers: [transfer("PENDING")],
    });
    const noPending = orders({
      orders: [order("EXECUTED", 10)],
      transfers: [transfer("REJECTED")],
    });

    expect(doneStepCount(planSteps({ month: month(), targets: targets(), orders: null }))).toBe(1);
    expect(doneStepCount(planSteps({ month: month(), targets: withTargets, orders: null }))).toBe(
      2,
    );
    expect(
      doneStepCount(planSteps({ month: month(), targets: withTargets, orders: persisted })),
    ).toBe(4);
    expect(
      doneStepCount(planSteps({ month: month(), targets: withTargets, orders: noPending })),
    ).toBe(5);
    expect(
      doneStepCount(
        planSteps({ month: month({ status: "CLOSED" }), targets: withTargets, orders: noPending }),
      ),
    ).toBe(6);
  });

  it("does not count an unplanned trade as a calculated plan", () => {
    const steps = planSteps({
      month: month(),
      targets: targets({ classes: [{ asset_class: "ACOES", percentage: 100 }] }),
      orders: orders({
        unplanned_trades: [{ id: 9, ticker: "ZZZZ9", side: "BUY", quantity: 10 }],
      }),
    });

    expect(doneStepCount(steps)).toBe(2);
    expect(firstUnfinishedStep(steps).key).toBe("plan");
  });

  it("treats a rejected transfer as a decision and releases the close", () => {
    const steps = planSteps({
      month: month(),
      targets: targets({ classes: [{ asset_class: "ACOES", percentage: 100 }] }),
      orders: orders({
        orders: [order("EXECUTED", 10)],
        transfers: [transfer("REJECTED")],
      }),
    });

    const transferStep = steps.find((step) => step.key === "transfer");
    expect(transferStep?.done).toBe(true);
    expect(transferStep?.blocked).toBe(false);
    expect(firstUnfinishedStep(steps).key).toBe("close");
  });

  it("marks the transfer step blocked while a proposal is pending", () => {
    const steps = planSteps({
      month: month(),
      targets: targets({ classes: [{ asset_class: "ACOES", percentage: 100 }] }),
      orders: orders({ orders: [order("EXECUTED", 10)], transfers: [transfer("PENDING")] }),
    });

    const transferStep = steps.find((step) => step.key === "transfer");
    expect(transferStep?.done).toBe(false);
    expect(transferStep?.blocked).toBe(true);
    expect(transferStep?.meta).toBe("1 pendente · trava o fechamento");
    expect(doneStepCount(steps)).toBe(4);
  });

  it("reports the stored plan as stale until a refresh", () => {
    const input = {
      month: month(),
      targets: targets({ classes: [{ asset_class: "ACOES", percentage: 100 }] }),
      orders: orders({ orders: [order("SUGGESTED")] }),
    };

    const fresh = planSteps(input).find((step) => step.key === "plan");
    const stale = planSteps({ ...input, planStale: true }).find((step) => step.key === "plan");

    expect(fresh?.meta).toBe("plano persistido · 1 ordem");
    expect(stale?.meta).toBe("plano desatualizado — recalcule");
  });

  it("summarises execution progress with the partial count", () => {
    const steps = planSteps({
      month: month(),
      targets: targets({ classes: [{ asset_class: "ACOES", percentage: 100 }] }),
      orders: orders({ orders: [order("EXECUTED", 10), order("SUGGESTED", 4)] }),
    });

    expect(steps.find((step) => step.key === "execution")?.meta).toBe("1/2 executadas · 1 parcial");
  });

  it("marks every step done for a closed month even when its targets are not in the snapshot", () => {
    const steps = planSteps({
      month: month({ status: "CLOSED" }),
      targets: targets(),
      orders: orders({ orders: [order("IMPORTED", 10)] }),
    });

    expect(doneStepCount(steps)).toBe(PLAN_STEPS_TOTAL);
    expect(steps.find((step) => step.key === "targets")?.meta).toBe("congelado no snapshot");
    expect(steps.find((step) => step.key === "close")?.meta).toBe("snapshot gravado");
  });

  it("returns the last step when the whole month is done", () => {
    const steps = planSteps({
      month: month({ status: "CLOSED" }),
      targets: targets({ classes: [{ asset_class: "ACOES", percentage: 100 }] }),
      orders: orders({ orders: [order("EXECUTED", 10)] }),
    });

    expect(doneStepCount(steps)).toBe(PLAN_STEPS_TOTAL);
    expect(firstUnfinishedStep(steps).key).toBe("close");
  });
});

describe("chainPreview", () => {
  it("derives each class ideal from the typed capital and its target", () => {
    const rows = chainPreview(
      "250000",
      targets({
        classes: [
          { asset_class: "ACOES", percentage: 40 },
          { asset_class: "FIIS", percentage: 25 },
        ],
      }),
    );

    expect(rows).toEqual([
      { assetClass: "ACOES", target: 40, ideal: 100_000 },
      { assetClass: "FIIS", target: 25, ideal: 62_500 },
    ]);
  });

  it("renders no preview while the capital is blank or invalid", () => {
    const withTargets = targets({ classes: [{ asset_class: "ACOES", percentage: 40 }] });

    expect(chainPreview("", withTargets)).toEqual([]);
    expect(chainPreview("abc", withTargets)).toEqual([]);
    expect(chainPreview("-1", withTargets)).toEqual([]);
  });
});
