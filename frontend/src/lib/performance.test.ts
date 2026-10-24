import { describe, expect, it } from "vitest";
import type { BenchmarkBlend, PeriodPerformance } from "../api/types.ts";
import {
  aportesPerformanceRows,
  benchmarkRows,
  classValueSeries,
  cumulativeReturns,
  decompositionResult,
  plotArea,
  projectX,
  projectY,
  returnFillClass,
  returnTextClass,
  valueExtent,
} from "./performance.ts";

function point(
  period: string,
  returnPercentage: number | undefined,
  benchmarkReturn: number | undefined = undefined,
): PeriodPerformance {
  return {
    period,
    decomposition: {
      value_start: returnPercentage === undefined ? undefined : 100_000,
      value_end: 101_000,
      net_aportes: 0,
      net_income: 0,
      return_percentage: returnPercentage,
    },
    benchmark_return: benchmarkReturn,
  };
}

describe("decompositionResult", () => {
  it("subtracts the start value, the aportes and the income from the end value", () => {
    expect(
      decompositionResult({
        value_start: 100_000,
        value_end: 103_500,
        net_aportes: 2_000,
        net_income: 500,
        return_percentage: 1,
      }),
    ).toBe(1_000);
  });

  it("has no result for the first point, which has no prior value", () => {
    expect(
      decompositionResult({
        value_end: 100_000,
        net_aportes: 0,
        net_income: 0,
      }),
    ).toBeUndefined();
  });
});

describe("cumulativeReturns", () => {
  it("compounds the monthly returns from a base of 100", () => {
    const rows = cumulativeReturns([
      point("2026-01", undefined, undefined),
      point("2026-02", 1, 2),
      point("2026-03", -1, 1),
    ]);

    expect(rows.map((row) => row.period)).toEqual(["2026-01", "2026-02", "2026-03"]);
    expect(rows[0].portfolio).toBe(100);
    expect(rows[0].benchmark).toBe(100);
    expect(rows[1].portfolio).toBeCloseTo(101);
    expect(rows[1].benchmark).toBeCloseTo(102);
    expect(rows[2].portfolio).toBeCloseTo(99.99);
    expect(rows[2].benchmark).toBeCloseTo(103.02);
  });

  it("carries the previous index forward when a month has no benchmark", () => {
    const rows = cumulativeReturns([point("2026-01", undefined), point("2026-02", 1, undefined)]);

    expect(rows[1].portfolio).toBeCloseTo(101);
    expect(rows[1].benchmark).toBe(100);
  });

  it("tracks each policy index on its own cumulative line", () => {
    const rows = cumulativeReturns([
      point("2026-01", undefined),
      {
        ...point("2026-02", 1, 0.9),
        benchmarks: [
          { index: "CDI", return_percentage: 1 },
          { index: "IPCA", return_percentage: 0.5 },
        ],
      },
      point("2026-03", 0),
    ]);

    expect(rows[1].indexes.CDI).toBeCloseTo(101);
    expect(rows[1].indexes.IPCA).toBeCloseTo(100.5);
    expect(rows[1].indexes.IBOV).toBe(100);
    expect(rows[2].indexes.IPCA).toBeCloseTo(100.5);
  });

  it("anchors every line at 100 on the first snapshot, even when a benchmark priced that month", () => {
    const rows = cumulativeReturns([point("2026-01", undefined, 2), point("2026-02", 1, 1)]);

    expect(rows[0].portfolio).toBe(100);
    expect(rows[0].benchmark).toBe(100);
    expect(rows[0].indexes.CDI).toBe(100);
    expect(rows[1].benchmark).toBeCloseTo(101);
  });
});

describe("benchmarkRows", () => {
  const blend: BenchmarkBlend = {
    return_percentage: 1.8,
    contributions: [
      { label: "ACOES", target: 60, benchmark: "IBOV", return_percentage: 2.5 },
      { label: "FIIS", target: 20, benchmark: "IBOV", return_percentage: 2.5 },
      { label: "RENDA_FIXA", target: 20, benchmark: "CDI", return_percentage: 0.9 },
    ],
  };

  it("lists CDI, IPCA and IBOV in order, then the target-weighted blend", () => {
    const rows = benchmarkRows(blend);

    expect(rows.map((row) => row.key)).toEqual(["CDI", "IPCA", "IBOV", "blend"]);
    expect(rows.map((row) => row.label)).toEqual(["CDI", "IPCA", "IBOV", "Blend alvo"]);
  });

  it("shares one index return across the classes that map to it and sums their targets", () => {
    const ibov = benchmarkRows(blend).find((row) => row.key === "IBOV");

    expect(ibov?.returnPercentage).toBe(2.5);
    expect(ibov?.target).toBe(80);
  });

  it("leaves an index the policy does not target unpriced", () => {
    const ipca = benchmarkRows(blend).find((row) => row.key === "IPCA");

    expect(ipca?.returnPercentage).toBeUndefined();
    expect(ipca?.target).toBeUndefined();
  });

  it("carries the weighted blend return on the last row", () => {
    expect(benchmarkRows(blend).at(-1)?.returnPercentage).toBe(1.8);
  });
});

describe("valueExtent", () => {
  it("always includes the zero baseline", () => {
    expect(valueExtent([1, 2, 3])).toEqual({ min: 0, max: 3 });
    expect(valueExtent([-3, -1])).toEqual({ min: -3, max: 0 });
  });

  it("spans both signs", () => {
    expect(valueExtent([-2, 5])).toEqual({ min: -2, max: 5 });
  });
});

describe("returnTextClass", () => {
  it("uses the yield colour for a gain and the principal colour for a loss", () => {
    expect(returnTextClass(1.5)).toBe("text-yield");
    expect(returnTextClass(-1.5)).toBe("text-principal");
    expect(returnTextClass(0)).toBe("text-yield");
    expect(returnTextClass(undefined)).toBe("text-yield");
  });

  it("maps the same sign to the fill class for the bars", () => {
    expect(returnFillClass(1.5)).toBe("bg-yield");
    expect(returnFillClass(-1.5)).toBe("bg-principal");
  });
});

describe("chart geometry", () => {
  const area = plotArea(100, 100, { top: 10, right: 10, bottom: 10, left: 10 });

  it("derives the plot area from the canvas and its padding", () => {
    expect(area).toEqual({ left: 10, top: 10, width: 80, height: 80 });
  });

  it("projects values onto the vertical axis, high at the top", () => {
    expect(projectY(0, 0, 10, area)).toBe(90);
    expect(projectY(10, 0, 10, area)).toBe(10);
    expect(projectY(5, 0, 10, area)).toBe(50);
  });

  it("spreads a line's points across the horizontal axis", () => {
    expect(projectX(0, 3, area)).toBe(10);
    expect(projectX(2, 3, area)).toBe(90);
  });

  it("centres a single point", () => {
    expect(projectX(0, 1, area)).toBe(50);
  });
});

describe("aportesPerformanceRows", () => {
  it("keeps the months in order with their net aportes and return", () => {
    const rows = aportesPerformanceRows([
      point("2026-01", undefined),
      {
        ...point("2026-02", 1.5),
        decomposition: {
          value_start: 100_000,
          value_end: 103_500,
          net_aportes: 2_000,
          net_income: 0,
          return_percentage: 1.5,
        },
      },
    ]);

    expect(rows).toEqual([
      { period: "2026-01", netAportes: 0, returnPercentage: undefined },
      { period: "2026-02", netAportes: 2_000, returnPercentage: 1.5 },
    ]);
  });
});

describe("classValueSeries", () => {
  it("plots each class's value across the months that carry it, in class order", () => {
    const series = classValueSeries([
      {
        ...point("2026-01", undefined),
        classes: [
          { asset_class: "ACOES", value_end: 60_000 },
          { asset_class: "RENDA_FIXA", value_end: 40_000 },
        ],
      },
      {
        ...point("2026-02", 1),
        classes: [
          { asset_class: "ACOES", value_end: 62_100 },
          { asset_class: "RENDA_FIXA", value_end: 41_400 },
        ],
      },
      {
        ...point("2026-03", 0),
        classes: [{ asset_class: "ACOES", value_end: 61_000 }],
      },
    ]);

    expect(series.map((point) => point.assetClass)).toEqual(["ACOES", "RENDA_FIXA"]);
    expect(series[0].points).toEqual([
      { period: "2026-01", value: 60_000 },
      { period: "2026-02", value: 62_100 },
      { period: "2026-03", value: 61_000 },
    ]);
    expect(series[1].points).toEqual([
      { period: "2026-01", value: 40_000 },
      { period: "2026-02", value: 41_400 },
    ]);
  });
});
