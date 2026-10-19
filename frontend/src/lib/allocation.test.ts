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
  actual: 3000,
  deficit: 97000,
  drift: -97,
  unattributed: 500,
  classes: [
    {
      asset_class: "ACOES",
      target: 50,
      ideal: 50000,
      actual: 1500,
      deficit: 48500,
      drift: -48.5,
      unattributed: 500,
      strategies: [
        {
          strategy: "Top Dividendos",
          target: 100,
          ideal: 50000,
          actual: 1000,
          deficit: 49000,
          drift: -49,
          tickers: [
            { ticker: "VALE3", target: 12, ideal: 6000, actual: 1000, deficit: 5000, drift: -5 },
          ],
        },
      ],
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
  it("shows the Capital root and every class, collapsed", () => {
    const rows = allocationRows(allocation, new Set());

    expect(rows.map((row) => [row.kind, row.depth, row.label])).toEqual([
      ["capital", 0, "Capital"],
      ["class", 1, "Ações"],
      ["class", 1, "Fundos imob."],
      ["class", 1, "Renda fixa"],
    ]);
  });

  it("reveals a class's strategies, and a strategy's tickers, only when expanded", () => {
    const classExpanded = allocationRows(allocation, new Set(["class:ACOES"]));
    expect(classExpanded.map((row) => row.label)).toEqual([
      "Capital",
      "Ações",
      "Top Dividendos",
      "Fundos imob.",
      "Renda fixa",
    ]);
    expect(classExpanded.some((row) => row.label === "VALE3")).toBe(false);

    const fullyExpanded = allocationRows(
      allocation,
      new Set(["class:ACOES", "strategy:ACOES:Top Dividendos"]),
    );
    const ticker = fullyExpanded.find((row) => row.label === "VALE3");
    expect(ticker?.kind).toBe("ticker");
    expect(ticker?.depth).toBe(3);
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

  it("scales against the largest absolute deficit and drift on the rows", () => {
    const rows = allocationRows(allocation, new Set());

    expect(maxAbsDeficit(rows)).toBe(97000);
    expect(maxAbsDrift(rows)).toBe(97);
  });
});

describe("unattributedShare", () => {
  it("is the unattributed value over the listed book (RF excluded)", () => {
    expect(unattributedShare(allocation)).toBe(0.5);
  });

  it("is zero when nothing is listed", () => {
    const onlyFixedIncome: Allocation = {
      ...allocation,
      actual: 2000,
      unattributed: 0,
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

  it("renders drift in percentage points with an explicit positive sign", () => {
    expect(formatPoints(-97)).toBe("-97,0 p.p.");
    expect(formatPoints(0.5)).toBe("+0,5 p.p.");
  });
});
