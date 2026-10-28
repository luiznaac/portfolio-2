import { describe, expect, it } from "vitest";
import type {
  AttributionSlice,
  Edition,
  EditionStatus,
  OrderPlan,
  Strategy,
} from "../api/types.ts";
import {
  attributionPercent,
  defaultTransferQuantity,
  isValidTransferQuantity,
  remainingAttributable,
  transferDestinations,
} from "./attribution.ts";

function slice(strategyId: number, quantity: number): AttributionSlice {
  return { strategy_id: strategyId, strategy_name: `Strategy ${strategyId}`, quantity };
}

describe("attributionPercent", () => {
  it("returns the slice's share of custody", () => {
    expect(attributionPercent(60, 100)).toBe(60);
  });

  it("is zero when custody is zero", () => {
    expect(attributionPercent(10, 0)).toBe(0);
  });
});

describe("remainingAttributable", () => {
  it("is the custody remainder for a new strategy", () => {
    expect(remainingAttributable(100, [slice(1, 60)])).toBe(40);
  });

  it("excludes the slice being replaced", () => {
    expect(remainingAttributable(100, [slice(1, 60), slice(2, 10)], 1)).toBe(90);
  });

  it("floors at zero when the position shrank below the attributed total", () => {
    expect(remainingAttributable(50, [slice(1, 60)])).toBe(0);
  });
});

describe("transferDestinations", () => {
  const strategies: Strategy[] = [
    strategy(1, "Top Dividendos", "ACOES", "REPORT_DRIVEN"),
    strategy(2, "Sem edição", "ACOES", "REPORT_DRIVEN"),
    strategy(3, "Edição sem peso", "ACOES", "REPORT_DRIVEN"),
    strategy(4, "Edição em revisão", "ACOES", "REPORT_DRIVEN"),
    strategy(5, "Legado Ações", "ACOES", "MANUAL"),
    strategy(6, "Top FIIs", "FIIS", "REPORT_DRIVEN"),
    strategy(7, "Edição antiga", "ACOES", "REPORT_DRIVEN"),
  ];
  const editions = new Map<number, Edition[]>([
    [1, [edition(1, "2026-10", "ACTIVE", [{ ticker: "PETR4", weight: 20, do_not_trade: false }])]],
    [3, [edition(3, "2026-10", "ACTIVE", [{ ticker: "PETR4", weight: 0, do_not_trade: false }])]],
    [4, [edition(4, "2026-10", "STAGED", [{ ticker: "PETR4", weight: 20, do_not_trade: false }])]],
    [5, [edition(5, "2026-10", "ACTIVE", [{ ticker: "PETR4", weight: 100, do_not_trade: false }])]],
    [6, [edition(6, "2026-10", "ACTIVE", [{ ticker: "PETR4", weight: 20, do_not_trade: false }])]],
    [
      7,
      [
        edition(7, "2026-11", "ACTIVE", [{ ticker: "VALE3", weight: 20, do_not_trade: false }]),
        edition(7, "2026-10", "ACTIVE", [{ ticker: "PETR4", weight: 20, do_not_trade: false }]),
      ],
    ],
  ]);

  it("offers only report-driven Strategies of the class whose active edition weighs the ticker", () => {
    expect(transferDestinations(strategies, editions, "ACOES", "PETR4", null)).toEqual([
      { strategyId: 1, strategyName: "Top Dividendos", shortfall: undefined },
    ]);
  });

  it("reads the whole-unit shortfall from the order plan", () => {
    const plan = orderPlan({
      strategy: "Top Dividendos",
      ticker: "PETR4",
      targetQuantity: 25,
      attributedQuantity: 10,
    });

    expect(transferDestinations(strategies, editions, "ACOES", "PETR4", plan)).toEqual([
      { strategyId: 1, strategyName: "Top Dividendos", shortfall: 15 },
    ]);
  });

  it("clamps a surplus to a zero shortfall", () => {
    const plan = orderPlan({
      strategy: "Top Dividendos",
      ticker: "PETR4",
      targetQuantity: 10,
      attributedQuantity: 25,
    });

    expect(transferDestinations(strategies, editions, "ACOES", "PETR4", plan)[0]?.shortfall).toBe(
      0,
    );
  });

  it("leaves the shortfall undefined when the plan does not name the slice", () => {
    const plan = orderPlan({
      strategy: "Outra",
      ticker: "PETR4",
      targetQuantity: 25,
      attributedQuantity: 10,
    });

    expect(transferDestinations(strategies, editions, "ACOES", "PETR4", plan)[0]?.shortfall).toBe(
      undefined,
    );
  });
});

describe("defaultTransferQuantity", () => {
  it("defaults to the receiver's shortfall capped by the bucket slice", () => {
    expect(defaultTransferQuantity({ strategyId: 1, strategyName: "Top", shortfall: 15 }, 40)).toBe(
      15,
    );
    expect(defaultTransferQuantity({ strategyId: 1, strategyName: "Top", shortfall: 60 }, 40)).toBe(
      40,
    );
  });

  it("preselects nothing when no shortfall is known", () => {
    expect(defaultTransferQuantity({ strategyId: 1, strategyName: "Top" }, 40)).toBe(0);
  });
});

describe("isValidTransferQuantity", () => {
  it("accepts a positive quantity up to the bucket slice", () => {
    expect(isValidTransferQuantity(10, 40)).toBe(true);
    expect(isValidTransferQuantity(40, 40)).toBe(true);
  });

  it("rejects zero, negatives, NaN and values above the bucket", () => {
    expect(isValidTransferQuantity(0, 40)).toBe(false);
    expect(isValidTransferQuantity(-1, 40)).toBe(false);
    expect(isValidTransferQuantity(41, 40)).toBe(false);
    expect(isValidTransferQuantity(Number.NaN, 40)).toBe(false);
  });
});

function strategy(
  id: number,
  name: string,
  assetClass: Strategy["asset_class"],
  kind: Strategy["kind"],
): Strategy {
  return { id, name, asset_class: assetClass, kind };
}

function edition(
  strategyId: number,
  referenceMonth: string,
  status: EditionStatus,
  entries: Edition["entries"],
): Edition {
  return {
    id: strategyId,
    strategy_id: strategyId,
    reference_month: referenceMonth,
    status,
    entries,
  };
}

function orderPlan({
  strategy,
  ticker,
  targetQuantity,
  attributedQuantity,
}: {
  strategy: string;
  ticker: string;
  targetQuantity: number;
  attributedQuantity: number;
}): OrderPlan {
  return {
    period: "2026-10",
    phase: "FUNDING",
    capital: 1000,
    floor: 0,
    investable_capital: 1000,
    classes: [
      {
        asset_class: "ACOES",
        target: 100,
        ideal: 1000,
        strategies: [
          {
            strategy,
            target: 100,
            ideal: 1000,
            tickers: [
              {
                ticker,
                strategy,
                target: 100,
                ideal: 1000,
                target_quantity: targetQuantity,
                neutro: false,
                attributed_quantity: attributedQuantity,
              },
            ],
          },
        ],
      },
    ],
    candidates: [],
    outside_strategy: [],
    unclassified: [],
    attribution: [],
    transfers: [],
  };
}
