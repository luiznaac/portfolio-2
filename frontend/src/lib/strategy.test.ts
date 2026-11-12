import { describe, expect, it } from "vitest";
import type {
  Allocation,
  Edition,
  EditionDiff,
  EditionEntry,
  MonthPerformance,
  Strategy,
} from "../api/types.ts";
import {
  type StrategyPricing,
  editionDiffSummary,
  editionSummaryChip,
  editionWeightSum,
  findStrategyPerformance,
  groupStrategiesByClass,
  isWeightSumValid,
  overrideEntry,
  ratingLabel,
  referenceMonthLabel,
  reportDrivenStrategies,
  stagedEdition,
  strategyComposition,
  strategyFatiaView,
  strategyKpis,
  strategyPositionRows,
  strategyRows,
  strategySlice,
  suggestStrategy,
} from "./strategy.ts";

const diff: EditionDiff = {
  entered: [{ ticker: "CXSE3", weight: 5 }],
  exited: [{ ticker: "B3SA3", weight: 0 }],
  changed: [
    {
      ticker: "PETR4",
      weight_before: 10,
      weight_after: 12.5,
      rating_before: "BUY",
      rating_after: "BUY",
    },
    {
      ticker: "ITUB4",
      weight_before: 15,
      weight_after: 10,
      rating_before: "BUY",
      rating_after: "NEUTRAL",
    },
  ],
};

const entries: EditionEntry[] = [
  {
    ticker: "PETR4",
    weight: 12.5,
    rating: "BUY",
    target_price: 63,
    do_not_trade: false,
  },
  {
    ticker: "VALE3",
    weight: 12.5,
    rating: "NEUTRAL",
    target_price: 85,
    do_not_trade: false,
  },
];

const strategies: Strategy[] = [
  { id: 9, name: "Legado", asset_class: "ACOES", kind: "MANUAL" },
  {
    id: 1,
    name: "Top Dividendos",
    asset_class: "ACOES",
    kind: "REPORT_DRIVEN",
  },
  {
    id: 5,
    name: "Fundamentalista FII",
    asset_class: "FIIS",
    kind: "REPORT_DRIVEN",
  },
  { id: 2, name: "Top Ações", asset_class: "ACOES", kind: "REPORT_DRIVEN" },
];

describe("referenceMonthLabel", () => {
  it.each([
    ["2026-01", "jan/26"],
    ["2026-09", "set/26"],
    ["2026-12", "dez/26"],
  ])("formats %s as %s", (month, label) => {
    expect(referenceMonthLabel(month)).toBe(label);
  });
});

describe("editionDiffSummary", () => {
  it("calls the first edition out plainly", () => {
    expect(editionDiffSummary(undefined)).toBe("primeira edição");
  });

  it("says so when nothing moved", () => {
    expect(editionDiffSummary({ entered: [], exited: [], changed: [] })).toBe("sem mudanças");
  });

  it("counts entered, exited and changed in the reference's notation", () => {
    expect(editionDiffSummary(diff)).toBe("+1 −1 ~2");
  });
});

describe("editionWeightSum", () => {
  it("adds the weights and rounds away floating-point noise", () => {
    const thirds: EditionEntry[] = [
      { ticker: "AAA3", weight: 33.33, do_not_trade: false },
      { ticker: "BBB3", weight: 33.33, do_not_trade: false },
      { ticker: "CCC3", weight: 33.34, do_not_trade: false },
    ];

    expect(editionWeightSum(thirds)).toBe(100);
  });
});

describe("isWeightSumValid", () => {
  it("accepts a portfolio that sums to 100 within a cent", () => {
    const entries: EditionEntry[] = [
      { ticker: "AAA3", weight: 33.33, do_not_trade: false },
      { ticker: "BBB3", weight: 33.33, do_not_trade: false },
      { ticker: "CCC3", weight: 33.34, do_not_trade: false },
    ];

    expect(isWeightSumValid(entries)).toBe(true);
  });

  it("rejects a portfolio that sums away from 100", () => {
    const entries: EditionEntry[] = [
      { ticker: "AAA3", weight: 60, do_not_trade: false },
      { ticker: "BBB3", weight: 30, do_not_trade: false },
    ];

    expect(isWeightSumValid(entries)).toBe(false);
  });
});

describe("groupStrategiesByClass", () => {
  it("groups by asset class in class order, skipping an empty class", () => {
    const groups = groupStrategiesByClass(strategies);

    expect(groups.map((group) => group.assetClass)).toEqual(["ACOES", "FIIS"]);
    expect(groups[0].strategies.map((strategy) => strategy.name)).toEqual([
      "Top Dividendos",
      "Top Ações",
      "Legado",
    ]);
    expect(groups[1].strategies.map((strategy) => strategy.name)).toEqual(["Fundamentalista FII"]);
  });
});

describe("ratingLabel", () => {
  it.each([
    ["BUY", "Compra"],
    ["NEUTRAL", "Neutro"],
    ["SELL", "Venda"],
  ] as const)("renders %s as %s", (rating, label) => {
    expect(ratingLabel(rating)).toBe(label);
  });

  it("renders an absent rating as an em dash", () => {
    expect(ratingLabel(undefined)).toBe("—");
  });
});

describe("overrideEntry", () => {
  it("replaces one entry immutably and leaves the others untouched", () => {
    const result = overrideEntry(entries, 0, {
      do_not_trade: true,
      weight: 15,
    });

    expect(result).not.toBe(entries);
    expect(result[0]).toEqual({
      ...entries[0],
      do_not_trade: true,
      weight: 15,
    });
    expect(result[1]).toBe(entries[1]);
  });
});

describe("suggestStrategy", () => {
  const catalog: Strategy[] = [
    {
      id: 1,
      name: "Top Dividendos",
      asset_class: "ACOES",
      kind: "REPORT_DRIVEN",
    },
    { id: 2, name: "Top Ações", asset_class: "ACOES", kind: "REPORT_DRIVEN" },
    {
      id: 3,
      name: "Dividendos Plus",
      asset_class: "ACOES",
      kind: "REPORT_DRIVEN",
    },
    { id: 4, name: "Small Caps", asset_class: "ACOES", kind: "REPORT_DRIVEN" },
    {
      id: 5,
      name: "Fundamentalista FII",
      asset_class: "FIIS",
      kind: "REPORT_DRIVEN",
    },
  ];

  it.each([
    ["Carteira Top Dividendos Plus.pdf", "Dividendos Plus"],
    ["Carteira Top Dividendos.pdf", "Top Dividendos"],
    ["Carteira Top Small Caps.pdf", "Small Caps"],
    ["Carteira-Fundamentalista-XP-09-2026-3.pdf", "Fundamentalista FII"],
    ["Top Ações XP.pdf", "Top Ações"],
  ])("maps %s to %s", (fileName, name) => {
    expect(suggestStrategy(catalog, fileName)?.name).toBe(name);
  });

  it("falls back to the first strategy for an unrecognised file name", () => {
    expect(suggestStrategy(catalog, "relatorio.pdf")?.id).toBe(1);
  });

  it("never suggests a manual strategy, even when its name matches", () => {
    const withBucket: Strategy[] = [
      { id: 9, name: "Top Dividendos", asset_class: "ACOES", kind: "MANUAL" },
      { id: 2, name: "Top Ações", asset_class: "ACOES", kind: "REPORT_DRIVEN" },
    ];

    expect(suggestStrategy(withBucket, "Top Dividendos.pdf")?.id).toBe(2);
    expect(suggestStrategy(withBucket, "relatorio.pdf")?.id).toBe(2);
  });
});

describe("reportDrivenStrategies", () => {
  it("keeps only the report-driven strategies", () => {
    expect(reportDrivenStrategies(strategies).map((strategy) => strategy.name)).toEqual([
      "Top Dividendos",
      "Fundamentalista FII",
      "Top Ações",
    ]);
  });
});

describe("strategyRows", () => {
  const pricing: StrategyPricing = {
    classes: [
      {
        strategies: [{ strategy: "Top Dividendos", actual: 12000, drift: 2.4 }],
        manual: { strategy: "Legado", actual: 3000 },
      },
      {
        strategies: [{ strategy: "Fundamentalista FII", actual: 5000, drift: -1.2 }],
      },
    ],
  };

  it("joins each strategy's value and drift and keeps the class order with the bucket last", () => {
    expect(
      strategyRows(strategies, pricing).map((row) => [row.strategy.name, row.value, row.drift]),
    ).toEqual([
      ["Top Dividendos", 12000, 2.4],
      ["Top Ações", undefined, undefined],
      ["Legado", 3000, undefined],
      ["Fundamentalista FII", 5000, -1.2],
    ]);
  });

  it("leaves every value unknown when no allocation was read", () => {
    expect(strategyRows(strategies).map((row) => row.value)).toEqual([
      undefined,
      undefined,
      undefined,
      undefined,
    ]);
  });
});

describe("editionSummaryChip", () => {
  it("shows the latest active edition with a check", () => {
    expect(editionSummaryChip({ id: 9, reference_month: "2026-09", status: "ACTIVE" })).toEqual({
      label: "set/26 ✓",
      tone: "green",
    });
  });

  it("badges a staged edition as in review", () => {
    expect(editionSummaryChip({ id: 9, reference_month: "2026-09", status: "STAGED" })).toEqual({
      label: "set/26 · revisão",
      tone: "amber",
    });
  });
});

const allocation: Allocation = {
  period: "2026-10",
  capital: 200000,
  floor: 0,
  ideal: 200000,
  actual: 100000,
  deficit: 100000,
  drift: -50,
  unattributed: 0,
  unclassified: { actual: 0, tickers: [] },
  classes: [
    {
      asset_class: "ACOES",
      target: 60,
      ideal: 120000,
      actual: 80000,
      deficit: 40000,
      drift: -20,
      unattributed: 0,
      strategies: [
        {
          strategy: "Top Dividendos",
          target: 60,
          ideal: 60000,
          actual: 12000,
          deficit: 48000,
          drift: -12.5,
          tickers: [
            {
              ticker: "PETR4",
              quantity: 100,
              target: 50,
              ideal: 30000,
              actual: 6000,
              deficit: 24000,
              drift: -12,
              price: 60,
            },
            {
              ticker: "VALE3",
              quantity: 50,
              target: 50,
              ideal: 30000,
              actual: 6000,
              deficit: 24000,
              drift: -13,
              price: 120,
            },
          ],
        },
        {
          strategy: "Top Ações",
          target: 40,
          ideal: 40000,
          actual: 8000,
          deficit: 32000,
          drift: -8,
          tickers: [],
        },
      ],
      manual: {
        strategy: "Legado",
        actual: 60000,
        tickers: [{ ticker: "WEGE3", actual: 60000, quantity: 1000, price: 60 }],
      },
      outside: { actual: 0, tickers: [] },
      sub_classes: [],
    },
  ],
};

const monthPerformance: MonthPerformance = {
  month_id: 5,
  period: "2026-10",
  decomposition: { value_end: 100000, net_aportes: 0, net_income: 0 },
  benchmark: { contributions: [] },
  classes: [
    {
      asset_class: "ACOES",
      decomposition: { value_end: 80000, net_aportes: 0, net_income: 0 },
      strategies: [
        {
          strategy_id: 1,
          strategy: "Top Dividendos",
          kind: "REPORT_DRIVEN",
          decomposition: {
            value_start: 10000,
            value_end: 12000,
            net_aportes: 1500,
            net_income: 100,
            return_percentage: 4,
          },
        },
      ],
    },
  ],
  tickers: [],
};

describe("strategySlice", () => {
  it("finds the report rung and its class", () => {
    const slice = strategySlice(strategies[1], allocation);

    expect(slice.cls?.asset_class).toBe("ACOES");
    expect(slice.report?.target).toBe(60);
    expect(slice.actual).toBe(12000);
    expect(slice.target).toBe(60);
    expect(slice.ideal).toBe(60000);
    expect(slice.drift).toBe(-12.5);
  });

  it("finds the manual rung and carries no target", () => {
    const slice = strategySlice(strategies[0], allocation);

    expect(slice.manual?.actual).toBe(60000);
    expect(slice.actual).toBe(60000);
    expect(slice.target).toBeUndefined();
    expect(slice.drift).toBeUndefined();
  });

  it("returns an empty slice when the tree carries no rung for the strategy", () => {
    const slice = strategySlice({ ...strategies[3], name: "Sem cadeia" }, allocation);

    expect(slice.actual).toBeUndefined();
    expect(slice.cls?.asset_class).toBe("ACOES");
  });

  it("joins by the additive strategy id when the tree carries one", () => {
    const [equities] = allocation.classes;
    const withId: Allocation = {
      ...allocation,
      classes: [
        {
          ...equities,
          strategies: equities.strategies.map((rung) =>
            rung.strategy === "Top Dividendos"
              ? { ...rung, strategy_id: 1, strategy: "Renomeada" }
              : rung,
          ),
          manual: equities.manual ? { ...equities.manual, strategy_id: 9 } : undefined,
        },
      ],
    };

    expect(strategySlice(strategies[1], withId).actual).toBe(12000);
    expect(strategySlice(strategies[0], withId).actual).toBe(60000);
  });
});

describe("strategyPositionRows", () => {
  it("lists the report strategy's tickers with target weight, units and value", () => {
    expect(strategyPositionRows(strategies[1], allocation)).toEqual([
      {
        ticker: "PETR4",
        quantity: 100,
        price: 60,
        value: 6000,
        target: 50,
        ideal: 30000,
        drift: -12,
        stale: undefined,
        quoteDate: undefined,
      },
      {
        ticker: "VALE3",
        quantity: 50,
        price: 120,
        value: 6000,
        target: 50,
        ideal: 30000,
        drift: -13,
        stale: undefined,
        quoteDate: undefined,
      },
    ]);
  });

  it("lists the manual bucket's custody with no target", () => {
    expect(strategyPositionRows(strategies[0], allocation)).toEqual([
      {
        ticker: "WEGE3",
        quantity: 1000,
        price: 60,
        value: 60000,
        target: undefined,
        ideal: undefined,
        drift: undefined,
        stale: undefined,
        quoteDate: undefined,
      },
    ]);
  });

  it("is empty when the tree carries no rung", () => {
    expect(strategyPositionRows({ ...strategies[3], name: "Sem cadeia" }, allocation)).toEqual([]);
  });
});

describe("strategyComposition", () => {
  it("weights each slice inside the strategy and totals the priced book", () => {
    const composition = strategyComposition(strategyPositionRows(strategies[1], allocation));

    expect(composition.total).toBe(12000);
    expect(composition.complete).toBe(true);
    expect(composition.rows.map((row) => [row.ticker, row.weight])).toEqual([
      ["PETR4", 50],
      ["VALE3", 50],
    ]);
  });

  it("leaves the total partial and the unpriced slice unweighted", () => {
    const composition = strategyComposition([
      { ticker: "PETR4", value: 1000 },
      { ticker: "VALE3", value: undefined },
    ]);

    expect(composition.total).toBe(1000);
    expect(composition.complete).toBe(false);
    expect(composition.rows[0].weight).toBe(100);
    expect(composition.rows[1].weight).toBeUndefined();
  });
});

describe("findStrategyPerformance", () => {
  it("finds a strategy's month decomposition by id", () => {
    expect(findStrategyPerformance(monthPerformance, 1)?.decomposition.return_percentage).toBe(4);
  });

  it("is absent when the close carries no strategy", () => {
    expect(findStrategyPerformance(monthPerformance, 99)).toBeUndefined();
    expect(findStrategyPerformance(undefined, 1)).toBeUndefined();
  });
});

describe("strategyKpis", () => {
  it("pins the report strategy's fatia, target, ideal, drift and last-close return", () => {
    expect(strategyKpis(strategies[1], allocation, monthPerformance)).toEqual([
      {
        key: "fatia",
        label: "Fatia",
        value: "R$ 12.000,00",
        hint: "12,0% da carteira",
        tone: "default",
      },
      {
        key: "alvo",
        label: "Alvo",
        value: "60,0%",
        hint: "da classe Ações",
        tone: "default",
      },
      {
        key: "ideal",
        label: "Ideal",
        value: "R$ 60.000,00",
        hint: "faltam R$ 48.000,00",
        tone: "deficit",
      },
      {
        key: "drift",
        label: "Drift",
        value: "-12,5 p.p.",
        hint: "contra o alvo",
        tone: "deficit",
      },
      {
        key: "return",
        label: "Retorno out/26",
        value: "+4,00%",
        hint: "último fechamento",
        tone: "return-up",
      },
    ]);
  });

  it("renders a manual bucket's target columns as sem alvo or an em dash", () => {
    const kpis = strategyKpis(strategies[0], allocation, monthPerformance);

    expect(kpis.map((kpi) => [kpi.key, kpi.value, kpi.hint])).toEqual([
      ["fatia", "R$ 60.000,00", "60,0% da carteira"],
      ["alvo", "sem alvo", "bucket manual"],
      ["ideal", "—", "não gera ordem"],
      ["drift", "—", "contra o alvo"],
      ["return", "—", "último fechamento"],
    ]);
  });
});

describe("strategyFatiaView", () => {
  it("states the slice's portfolio and class shares beside the close decomposition", () => {
    expect(strategyFatiaView(strategies[1], allocation, monthPerformance)).toEqual({
      portfolioShare: "12,0%",
      classShare: "15,0%",
      target: "60,0%",
      drift: "-12,5 p.p.",
      driftTone: "deficit",
      period: "2026-10",
      decomposition: {
        valueStart: "R$ 10.000,00",
        valueEnd: "R$ 12.000,00",
        aportes: "R$ 1.500,00",
        income: "R$ 100,00",
        return: "+4,00%",
        returnTone: "return-up",
      },
    });
  });

  it("leaves a manual bucket without a decomposition and every target unknown", () => {
    expect(strategyFatiaView(strategies[0], allocation, monthPerformance)).toEqual({
      portfolioShare: "60,0%",
      classShare: "75,0%",
      target: "sem alvo",
      drift: "—",
      driftTone: "muted",
      period: undefined,
      decomposition: undefined,
    });
  });
});

describe("stagedEdition", () => {
  const active: Edition = {
    id: 1,
    strategy_id: 1,
    reference_month: "2026-09",
    status: "ACTIVE",
    entries: [],
  };
  const staged: Edition = {
    id: 2,
    strategy_id: 1,
    reference_month: "2026-10",
    status: "STAGED",
    entries: [],
  };

  it("finds the staged edition beside the active one", () => {
    expect(stagedEdition([active, staged])).toEqual(staged);
  });

  it("is absent when only active editions exist", () => {
    expect(stagedEdition([active])).toBeUndefined();
    expect(stagedEdition([])).toBeUndefined();
  });
});
