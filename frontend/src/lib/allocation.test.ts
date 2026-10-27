import { describe, expect, it } from "vitest";
import type { Allocation } from "../api/types.ts";
import {
  allocationRows,
  barWidth,
  formatPoints,
  formatTarget,
  maxAbsDeficit,
  maxAbsDrift,
  unattributedShare,
} from "./allocation.ts";

const allocation: Allocation = {
  period: "2026-09",
  capital: 100000,
  floor: 20000,
  ideal: 100000,
  actual: 3600,
  deficit: 96400,
  drift: -96.4,
  unattributed: 500,
  unclassified: {
    actual: 100,
    tickers: [{ ticker: "ZZZZ9", actual: 100 }],
  },
  classes: [
    {
      asset_class: "ACOES",
      target: 50,
      ideal: 50000,
      actual: 1500,
      deficit: 48500,
      drift: -48.5,
      unattributed: 400,
      strategies: [
        {
          strategy: "Top Dividendos",
          target: 100,
          ideal: 50000,
          actual: 600,
          deficit: 49400,
          drift: -49.4,
          tickers: [
            { ticker: "VALE3", target: 12, ideal: 6000, actual: 600, deficit: 5400, drift: -5.4 },
          ],
        },
      ],
      manual: {
        strategy: "Legado Ações",
        actual: 500,
        tickers: [
          { ticker: "ITUB4", actual: 300 },
          { ticker: "VALE3", actual: 200 },
        ],
      },
      outside: {
        actual: 400,
        tickers: [
          { ticker: "ITUB4", actual: 200 },
          { ticker: "VALE3", actual: 200 },
        ],
      },
      sub_classes: [],
    },
    {
      asset_class: "FIIS",
      ideal: 0,
      actual: 0,
      deficit: 0,
      drift: 0,
      unattributed: 0,
      strategies: [],
      outside: { actual: 0, tickers: [] },
      sub_classes: [],
    },
    {
      asset_class: "RENDA_FIXA",
      target: 50,
      ideal: 50000,
      actual: 2000,
      deficit: 48000,
      drift: -48,
      unattributed: 0,
      strategies: [],
      outside: { actual: 0, tickers: [] },
      sub_classes: [
        {
          sub_class: "FLOATING_RATE",
          target: 55,
          ideal: 27500,
          actual: 2000,
          deficit: 25500,
          drift: -25.5,
        },
      ],
    },
  ],
};

describe("allocationRows", () => {
  it("shows the Capital root, every class and the unclassified line, collapsed", () => {
    const rows = allocationRows(allocation, new Set());

    expect(rows.map((row) => [row.kind, row.depth, row.label])).toEqual([
      ["capital", 0, "Capital"],
      ["class", 1, "Ações"],
      ["class", 1, "Fundos imob."],
      ["class", 1, "Renda fixa"],
      ["unclassified", 1, "Sem classe"],
    ]);
  });

  it("reveals a class's report Strategies, then the bucket, then the outside row", () => {
    const rows = allocationRows(allocation, new Set(["class:ACOES"]));

    expect(rows.map((row) => row.label)).toEqual([
      "Capital",
      "Ações",
      "Top Dividendos",
      "Legado Ações",
      "Fora da estratégia",
      "Fundos imob.",
      "Renda fixa",
      "Sem classe",
    ]);
    expect(rows.find((row) => row.label === "Legado Ações")?.kind).toBe("manual");
    expect(rows.find((row) => row.label === "Fora da estratégia")?.kind).toBe("outside");
  });

  it("leaves Alvo, Ideal, Δ R$ and Δ p.p. undefined on the bucket, outside and unclassified rows", () => {
    const rows = allocationRows(
      allocation,
      new Set(["class:ACOES", "manual:ACOES", "outside:ACOES", "unclassified"]),
    );

    for (const label of ["Legado Ações", "Fora da estratégia", "Sem classe", "ITUB4", "ZZZZ9"]) {
      const row = rows.find((candidate) => candidate.label === label);
      expect(row).toBeDefined();
      expect(row?.target).toBeUndefined();
      expect(row?.ideal).toBeUndefined();
      expect(row?.deficit).toBeUndefined();
      expect(row?.drift).toBeUndefined();
    }
  });

  it("reveals a strategy's tickers only when the strategy is expanded", () => {
    const classExpanded = allocationRows(allocation, new Set(["class:ACOES"]));
    expect(classExpanded.some((row) => row.label === "VALE3")).toBe(false);

    const strategyExpanded = allocationRows(
      allocation,
      new Set(["class:ACOES", "strategy:ACOES:Top Dividendos"]),
    );
    const ticker = strategyExpanded.find((row) => row.kind === "ticker" && row.label === "VALE3");
    expect(ticker?.depth).toBe(3);
    expect(ticker?.actual).toBe(600);
  });

  it("reveals the bucket's and the outside row's tickers at leaf depth", () => {
    const rows = allocationRows(
      allocation,
      new Set(["class:ACOES", "manual:ACOES", "outside:ACOES"]),
    );

    const manualTickers = rows.filter((row) => row.kind === "manual_ticker");
    expect(manualTickers.map((row) => [row.label, row.depth, row.actual])).toEqual([
      ["ITUB4", 3, 300],
      ["VALE3", 3, 200],
    ]);
    expect(manualTickers.map((row) => row.assetClass)).toEqual(["ACOES", "ACOES"]);

    const outsideTickers = rows.filter((row) => row.kind === "outside_ticker");
    expect(outsideTickers.map((row) => [row.label, row.depth, row.actual])).toEqual([
      ["ITUB4", 3, 200],
      ["VALE3", 3, 200],
    ]);
  });

  it("reveals the unclassified tickers under the root line", () => {
    const rows = allocationRows(allocation, new Set(["unclassified"]));

    const ticker = rows.find((row) => row.label === "ZZZZ9");
    expect(ticker?.kind).toBe("unclassified_ticker");
    expect(ticker?.depth).toBe(2);
    expect(ticker?.actual).toBe(100);
  });

  it("reveals the RF sub-classes as the RF class's children", () => {
    const rows = allocationRows(allocation, new Set(["class:RENDA_FIXA"]));

    const subClass = rows.find((row) => row.label === "Pós-fixado");
    expect(subClass?.kind).toBe("sub_class");
    expect(subClass?.depth).toBe(2);
    expect(subClass?.target).toBe(55);
  });

  it("marks a class with children expandable and a leaf not", () => {
    const rows = allocationRows(allocation, new Set());

    expect(rows[0].expandable).toBe(false);
    expect(rows.find((row) => row.label === "Ações")?.expandable).toBe(true);
    expect(rows.find((row) => row.label === "Fundos imob.")?.expandable).toBe(false);
    expect(rows.find((row) => row.label === "Sem classe")?.expandable).toBe(true);
  });
});

describe("bars", () => {
  it("maps a value to at most half the track, and zero when nothing diverges", () => {
    expect(barWidth(50, 100)).toBe(25);
    expect(barWidth(-50, 100)).toBe(25);
    expect(barWidth(100, 100)).toBe(50);
    expect(barWidth(200, 100)).toBe(50);
    expect(barWidth(10, 0)).toBe(0);
  });

  it("scales against the largest absolute deficit and drift on the planned rows", () => {
    const rows = allocationRows(allocation, new Set());

    expect(maxAbsDeficit(rows)).toBe(96400);
    expect(maxAbsDrift(rows)).toBe(96.4);
  });
});

describe("unattributedShare", () => {
  it("is the outside plus unclassified value over the listed book, never the bucket", () => {
    expect(unattributedShare(allocation)).toBeCloseTo(0.3125);
  });

  it("is zero when nothing is listed", () => {
    const onlyFixedIncome: Allocation = {
      ...allocation,
      actual: 2000,
      unattributed: 0,
      unclassified: { actual: 0, tickers: [] },
      classes: allocation.classes.filter((cls) => cls.asset_class === "RENDA_FIXA"),
    };

    expect(unattributedShare(onlyFixedIncome)).toBe(0);
  });
});

describe("allocation formatting", () => {
  it("renders a target as a pt-BR percentage, or an em dash when absent", () => {
    expect(formatTarget(50)).toBe("50,0%");
    expect(formatTarget(undefined)).toBe("—");
  });

  it("renders drift in percentage points with an explicit positive sign, or an em dash", () => {
    expect(formatPoints(-97)).toBe("-97,0 p.p.");
    expect(formatPoints(0.5)).toBe("+0,5 p.p.");
    expect(formatPoints(undefined)).toBe("—");
  });
});
