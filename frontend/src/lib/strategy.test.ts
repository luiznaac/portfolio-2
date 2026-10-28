import { describe, expect, it } from "vitest";
import type { Edition, EditionDiff, EditionEntry, Strategy } from "../api/types.ts";
import {
  editionDiffSummary,
  editionStatusChip,
  editionWeightSum,
  groupStrategiesByClass,
  isEditionForMonth,
  isWeightSumValid,
  overrideEntry,
  ratingLabel,
  referenceMonthLabel,
  reportDrivenStrategies,
  strategyCoverage,
  strategyCoverageLabel,
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

describe("isEditionForMonth", () => {
  it("matches the edition's reference month", () => {
    const edition: Edition = {
      id: 9,
      strategy_id: 1,
      reference_month: "2026-09",
      status: "ACTIVE",
      entries,
    };

    expect(isEditionForMonth(edition, "2026-09")).toBe(true);
    expect(isEditionForMonth(edition, "2026-10")).toBe(false);
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

describe("strategyCoverage", () => {
  const editions = new Map([
    [1, [{ reference_month: "2026-09" }]],
    [2, [{ reference_month: "2026-08" }]],
  ]);

  it("counts report-driven strategies with an edition for the month", () => {
    expect(strategyCoverage(strategies, editions, "2026-09")).toEqual({
      withEdition: 1,
      total: 3,
    });
    expect(strategyCoverageLabel(strategyCoverage(strategies, editions, "2026-09"))).toBe(
      "1 de 3 com edição",
    );
  });

  it("leaves the bucket out of both the numerator and the denominator", () => {
    const withBucketEdition = new Map([
      [1, [{ reference_month: "2026-09" }]],
      [2, [{ reference_month: "2026-09" }]],
      [9, [{ reference_month: "2026-09" }]],
    ]);

    expect(strategyCoverage(strategies, withBucketEdition, "2026-09")).toEqual({
      withEdition: 2,
      total: 3,
    });
  });
});

describe("editionStatusChip", () => {
  it("shows the latest active edition with a check", () => {
    expect(
      editionStatusChip([
        {
          id: 9,
          strategy_id: 1,
          reference_month: "2026-09",
          status: "ACTIVE",
          entries: [],
        },
      ]),
    ).toEqual({ label: "set/26 ✓", tone: "green" });
  });

  it("shows a staged edition as in review", () => {
    expect(
      editionStatusChip([
        {
          id: 9,
          strategy_id: 1,
          reference_month: "2026-09",
          status: "STAGED",
          entries: [],
        },
      ]),
    ).toEqual({ label: "set/26 · revisão", tone: "amber" });
  });

  it("reads sem edição when the strategy has none", () => {
    expect(editionStatusChip([])).toEqual({
      label: "sem edição",
      tone: "neutral",
    });
    expect(editionStatusChip(undefined)).toEqual({
      label: "sem edição",
      tone: "neutral",
    });
  });
});
