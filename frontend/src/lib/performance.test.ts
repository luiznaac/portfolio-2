import { describe, expect, it } from "vitest";
import type {
  BenchmarkBlend,
  ClassPerformance,
  PerformanceSource,
  PeriodPerformance,
} from "../api/types.ts";
import {
  aportesPerformanceRows,
  benchmarkIndexCoverage,
  benchmarkRows,
  blockingLegs,
  classValueSeries,
  contiguousSegments,
  decompositionResult,
  plotArea,
  projectX,
  projectY,
  returnFillClass,
  returnTextClass,
  strategyRows,
  strategyValueSeries,
  valueExtent,
  valueKpi,
  windowCumulativeReturns,
} from "./performance.ts";

function point(
  period: string,
  returnPercentage: number | undefined,
  benchmarkReturn: number | undefined = undefined,
  source: PerformanceSource = "snapshot",
): PeriodPerformance {
  return {
    period,
    source,
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

function computed(period: string, returnPercentage: number | undefined): PeriodPerformance {
  return point(period, returnPercentage, undefined, "computed");
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

  it("nets a strategy slice's bookkeeping flow out of its result", () => {
    expect(
      decompositionResult({
        value_start: 3_000,
        value_end: 4_000,
        net_aportes: 0,
        net_income: 0,
        flow: 1_000,
        return_percentage: 0,
      }),
    ).toBe(0);
  });
});

describe("strategyRows", () => {
  const classes: ClassPerformance[] = [
    {
      asset_class: "ACOES",
      decomposition: {
        value_start: 10_000,
        value_end: 12_100,
        net_aportes: 0,
        net_income: 110,
        return_percentage: 9,
      },
      benchmark_return: 9,
      strategies: [
        {
          strategy_id: 1,
          strategy: "Top Dividendos",
          kind: "REPORT_DRIVEN",
          decomposition: {
            value_start: 4_000,
            value_end: 4_400,
            net_aportes: 0,
            net_income: 40,
            return_percentage: 9,
          },
        },
        {
          strategy_id: 2,
          strategy: "Legado",
          kind: "MANUAL",
          decomposition: {
            value_start: 6_000,
            value_end: 6_600,
            net_aportes: 0,
            net_income: 60,
            return_percentage: 9,
          },
        },
      ],
      outside: {
        value_start: 1_000,
        value_end: 1_100,
        net_aportes: 0,
        net_income: 10,
        return_percentage: 9,
      },
    },
    {
      asset_class: "RENDA_FIXA",
      decomposition: {
        value_start: 5_000,
        value_end: 5_050,
        net_aportes: 0,
        net_income: 0,
        return_percentage: 1,
      },
      benchmark_return: 1,
    },
  ];

  it("lists each Strategy slice then the read-only outside row, per class", () => {
    const rows = strategyRows(classes);

    expect(rows.map((row) => row.label)).toEqual([
      "Top Dividendos",
      "Legado",
      "Fora da estratégia",
    ]);
    expect(rows.map((row) => row.kind)).toEqual(["strategy", "strategy", "outside"]);
    expect(rows.at(-1)?.assetClass).toBe("ACOES");
    expect(rows.at(-1)?.decomposition.value_end).toBe(1_100);
  });

  it("keeps the outside row only for a class that carries one", () => {
    expect(strategyRows(classes).filter((row) => row.kind === "outside")).toHaveLength(1);
  });
});

describe("strategyValueSeries", () => {
  it("plots one frozen value series per Strategy, ordered by first appearance", () => {
    const months: PeriodPerformance[] = [
      {
        ...point("2026-01", undefined),
        strategies: [
          {
            strategy_id: 1,
            strategy: "Top Dividendos",
            kind: "REPORT_DRIVEN",
            value_end: 4_000,
            flow: 0,
          },
          { strategy_id: 2, strategy: "Legado", kind: "MANUAL", value_end: 6_000, flow: 0 },
        ],
      },
      {
        ...point("2026-02", 9),
        strategies: [
          {
            strategy_id: 1,
            strategy: "Top Dividendos",
            kind: "REPORT_DRIVEN",
            value_end: 4_400,
            flow: 0,
          },
          { strategy_id: 2, strategy: "Legado", kind: "MANUAL", value_end: 6_600, flow: 0 },
        ],
      },
    ];

    const series = strategyValueSeries(months);

    expect(series.map((line) => line.strategyId)).toEqual([1, 2]);
    expect(series.map((line) => line.strategy)).toEqual(["Top Dividendos", "Legado"]);
    expect(series[0].kind).toBe("REPORT_DRIVEN");
    expect(series[1].kind).toBe("MANUAL");
    expect(series[1].points).toEqual([
      { period: "2026-01", value: 6_000 },
      { period: "2026-02", value: 6_600 },
    ]);
  });
});

describe("windowCumulativeReturns", () => {
  const range = { start: "2026-01", end: "2026-03" };

  it("starts at 100 one month before the window and compounds every in-window month", () => {
    const result = windowCumulativeReturns(
      [point("2026-01", 1, 2), point("2026-02", -1, 1), point("2026-03", 0.5, undefined)],
      range,
    );
    const points = result.points;

    expect(points.map((point) => point.period)).toEqual([
      "2025-12",
      "2026-01",
      "2026-02",
      "2026-03",
    ]);
    expect(points[0].portfolio).toBe(100);
    expect(points[1].portfolio).toBeCloseTo(101);
    expect(points[2].portfolio).toBeCloseTo(99.99);
    expect(points[3].portfolio).toBeCloseTo(100.48995);
  });

  it("dashes the benchmark only where a return exists and counts its coverage", () => {
    const result = windowCumulativeReturns(
      [
        point("2026-01", 1, undefined),
        point("2026-02", 1, 2),
        { ...point("2026-03", 0), benchmark_return: undefined },
      ],
      range,
    );

    expect(result.points[1].benchmark).toBeUndefined();
    expect(result.points[2].benchmark).toBeCloseTo(102);
    expect(result.points[3].benchmark).toBeUndefined();
    expect(result.benchmarkCount).toBe(1);
    expect(result.benchmarkPercentage).toBeCloseTo(2);
  });

  it("reports a window with no benchmark as having no blend", () => {
    const result = windowCumulativeReturns([point("2026-01", 1)], range);

    expect(result.benchmarkCount).toBe(0);
    expect(result.benchmarkPercentage).toBeUndefined();
  });

  it("leaves out series months outside the window, even when they predate its baseline", () => {
    const result = windowCumulativeReturns(
      [point("2025-11", 5), point("2025-12", 5), point("2026-01", 1)],
      range,
    );

    expect(result.points.map((point) => point.period)).toEqual(["2025-12", "2026-01"]);
    expect(result.months.map((month) => month.period)).toEqual(["2026-01"]);
    expect(result.points[1].portfolio).toBeCloseTo(101);
    expect(result.returnPercentage).toBeCloseTo(1);
  });

  it("tracks each policy index on its own cumulative line and breaks a missing point", () => {
    const result = windowCumulativeReturns(
      [
        {
          ...point("2026-01", 1, 0.9),
          benchmarks: [
            { index: "CDI", return_percentage: 1 },
            { index: "IPCA", return_percentage: 0.5 },
          ],
        },
        point("2026-02", 0),
      ],
      range,
    );

    expect(result.points[1].indexes.CDI).toBeCloseTo(101);
    expect(result.points[1].indexes.IPCA).toBeCloseTo(100.5);
    expect(result.points[1].indexes.IBOV).toBeUndefined();
    expect(result.points[2].indexes.IPCA).toBeUndefined();
  });
});

describe("contiguousSegments", () => {
  it("groups a defined value's points into contiguous segments for a broken line", () => {
    expect(contiguousSegments([100, 101, undefined, undefined, 103, 104])).toEqual([
      [0, 1],
      [4, 5],
    ]);
    expect(contiguousSegments([undefined, 100])).toEqual([[1]]);
    expect(contiguousSegments([undefined])).toEqual([]);
  });
});

describe("benchmarkIndexCoverage", () => {
  it("counts the window months each policy index could not price", () => {
    const coverage = benchmarkIndexCoverage([
      { ...computed("2026-01", 1), benchmarks: [{ index: "IBOV", return_percentage: 1 }] },
      {
        ...point("2026-02", 1, 0.9),
        benchmarks: [{ index: "IBOV" }, { index: "CDI", return_percentage: 1 }],
      },
      {
        ...point("2026-03", 1, 0.9),
        benchmarks: [{ index: "IBOV", return_percentage: 1 }, { index: "CDI" }],
      },
    ]);

    expect(coverage.find((row) => row.index === "IBOV")?.missing).toBe(1);
    expect(coverage.find((row) => row.index === "IPCA")?.missing).toBe(3);
    expect(coverage.find((row) => row.index === "CDI")?.missing).toBe(2);
  });
});

describe("blockingLegs", () => {
  it("names each targeted leg a missing return makes the blend unknown", () => {
    const blend: BenchmarkBlend = {
      contributions: [
        { label: "ACOES", target: 60, benchmark: "IBOV" },
        { label: "RENDA_FIXA.FLOATING_RATE", target: 20, benchmark: "CDI", return_percentage: 0.9 },
        { label: "FIIS", target: 20, benchmark: "IBOV" },
      ],
    };

    expect(blockingLegs(blend).map((leg) => leg.label)).toEqual(["ACOES", "FIIS"]);
  });

  it("has no blocking leg when every targeted leg is priced", () => {
    expect(
      blockingLegs({
        return_percentage: 1.8,
        contributions: [
          { label: "ACOES", target: 60, benchmark: "IBOV", return_percentage: 2.5 },
          { label: "RENDA_FIXA", target: 40, benchmark: "CDI", return_percentage: 0.9 },
        ],
      }),
    ).toEqual([]);
  });
});

describe("valueKpi", () => {
  it("switches to Valor investido while the last point is the computed history", () => {
    expect(valueKpi([computed("2026-01", undefined)]).label).toBe("Valor investido");
    expect(valueKpi([computed("2026-01", undefined)]).hint).toBe(
      "histórico · sem caixa registrado",
    );
    expect(valueKpi([point("2026-02", 1)]).label).toBe("Patrimônio");
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
