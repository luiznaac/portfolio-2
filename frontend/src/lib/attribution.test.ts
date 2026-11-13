import { describe, expect, it } from "vitest";
import type {
  Attribution,
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
  reportStrategiesOfClass,
  strategyAttributionView,
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

describe("reportStrategiesOfClass", () => {
  it("keeps only report-driven Strategies of the named class", () => {
    const strategies: Strategy[] = [
      strategy(1, "Top Dividendos", "ACOES", "REPORT_DRIVEN"),
      strategy(2, "Legado Ações", "ACOES", "MANUAL"),
      strategy(3, "Top FIIs", "FIIS", "REPORT_DRIVEN"),
    ];

    expect(reportStrategiesOfClass(strategies, "ACOES")).toEqual([strategies[0]]);
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

describe("strategyAttributionView", () => {
  const report: Strategy = {
    id: 1,
    name: "Top Dividendos",
    asset_class: "ACOES",
    kind: "REPORT_DRIVEN",
  };
  const attribution = (
    ticker: string,
    custody: number,
    slices: Attribution["slices"],
  ): Attribution => {
    const attributed = slices.reduce((sum, item) => sum + item.quantity, 0);
    return {
      ticker,
      custody_quantity: custody,
      attributed_quantity: attributed,
      unattributed_quantity: custody - attributed,
      slices,
    };
  };

  it("marks a ticker whose whole custody sits in the strategy as reconciled", () => {
    const view = strategyAttributionView(report.id, [
      attribution("PETR4", 100, [
        { strategy_id: 1, strategy_name: "Top Dividendos", quantity: 100 },
      ]),
    ]);

    expect(view.rows).toEqual([
      {
        ticker: "PETR4",
        custody: 100,
        own: 100,
        outsideSlice: 0,
        others: [],
        state: "reconciled",
        tone: "green",
        statement: undefined,
      },
    ]);
    expect(view.finding).toBe("Custódia e atribuição conferem em todos os tickers.");
  });

  it("keeps a split custody reconciled and names the other strategy", () => {
    const view = strategyAttributionView(report.id, [
      attribution("PETR4", 100, [
        { strategy_id: 1, strategy_name: "Top Dividendos", quantity: 60 },
        { strategy_id: 4, strategy_name: "Legado Ações", quantity: 40 },
      ]),
    ]);

    expect(view.rows[0]).toMatchObject({
      own: 60,
      outsideSlice: 40,
      others: [{ strategyName: "Legado Ações", quantity: 40 }],
      state: "reconciled",
      tone: "green",
      statement: undefined,
    });
  });

  it("states unattributed custody as an attribution gap", () => {
    const view = strategyAttributionView(report.id, [
      attribution("PETR4", 100, [
        { strategy_id: 1, strategy_name: "Top Dividendos", quantity: 60 },
      ]),
    ]);

    expect(view.rows[0]).toMatchObject({
      own: 60,
      outsideSlice: 40,
      state: "gap",
      tone: "amber",
      statement: "40 cotas fora de toda estratégia",
    });
    expect(view.finding).toBe(
      "1 de 1 ticker diverge da custódia · PETR4 (40 cotas fora de toda estratégia)",
    );
  });

  it("states an attribution above custody as a divergence", () => {
    const view = strategyAttributionView(report.id, [
      attribution("PETR4", 100, [
        { strategy_id: 1, strategy_name: "Top Dividendos", quantity: 120 },
      ]),
    ]);

    expect(view.rows[0]).toMatchObject({
      custody: 100,
      own: 120,
      outsideSlice: -20,
      state: "over",
      tone: "amber",
      statement: "atribuição 20 cotas acima da custódia",
    });
  });

  it("counts every divergent ticker in the finding and sorts by ticker", () => {
    const view = strategyAttributionView(report.id, [
      attribution("VALE3", 50, [{ strategy_id: 1, strategy_name: "Top Dividendos", quantity: 70 }]),
      attribution("PETR4", 100, [
        { strategy_id: 1, strategy_name: "Top Dividendos", quantity: 60 },
      ]),
    ]);

    expect(view.rows.map((row) => row.ticker)).toEqual(["PETR4", "VALE3"]);
    expect(view.finding).toBe(
      "2 de 2 tickers divergem da custódia · PETR4 (40 cotas fora de toda estratégia); " +
        "VALE3 (atribuição 20 cotas acima da custódia)",
    );
  });

  it("shows a ticker the strategy targets but holds nothing of, reconciled through another strategy", () => {
    const view = strategyAttributionView(report.id, [
      attribution("WEGE3", 1000, [
        { strategy_id: 4, strategy_name: "Legado Ações", quantity: 1000 },
      ]),
    ]);

    expect(view.rows[0]).toMatchObject({
      ticker: "WEGE3",
      custody: 1000,
      own: 0,
      outsideSlice: 1000,
      others: [{ strategyName: "Legado Ações", quantity: 1000 }],
      state: "reconciled",
    });
  });

  it("has nothing to reconcile when the strategy holds no ticker", () => {
    const view = strategyAttributionView(report.id, []);

    expect(view.rows).toEqual([]);
    expect(view.finding).toBe("Nenhum ativo atribuído a esta estratégia.");
  });
});
