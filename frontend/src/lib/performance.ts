// Pure performance helpers — no React imports here (see AGENTS.md). The decomposition arithmetic,
// the cumulative series the charts plot, the benchmark legend and the chart geometry live here so
// their rules are unit-tested rather than buried in the page.

import type {
  AssetClass,
  BenchmarkBlend,
  BenchmarkContribution,
  BenchmarkIndex,
  ClassPerformance,
  PerformanceDecomposition,
  PerformanceSeries,
  PeriodPerformance,
  StrategyKind,
} from "../api/types.ts";
import { ASSET_CLASSES } from "./month.ts";

// The shared sign vocabulary for a return figure: a gain reads in the yield colour, a loss in the
// principal colour. Returns the Tailwind class, so the four tables and charts cannot drift apart.
export function returnTextClass(value: number | undefined): string {
  return (value ?? 0) < 0 ? "text-principal" : "text-yield";
}

export function returnFillClass(value: number | undefined): string {
  return (value ?? 0) < 0 ? "bg-principal" : "bg-yield";
}

// The month's return in R$: value_end − value_start − net aportes − net income − flow. The first
// point has no prior value, so it has no result. A strategy slice's flow is its bookkeeping
// attribution change, which moved units without being performance.
export function decompositionResult(decomposition: PerformanceDecomposition): number | undefined {
  if (decomposition.value_start === undefined) return undefined;
  return (
    decomposition.value_end -
    decomposition.value_start -
    decomposition.net_aportes -
    decomposition.net_income -
    (decomposition.flow ?? 0)
  );
}

export interface PlotArea {
  left: number;
  top: number;
  width: number;
  height: number;
}

// The inner rectangle of an SVG chart once its padding is taken out — the one projection every
// performance chart shares.
export function plotArea(
  width: number,
  height: number,
  pad: { top: number; right: number; bottom: number; left: number },
): PlotArea {
  return {
    left: pad.left,
    top: pad.top,
    width: width - pad.left - pad.right,
    height: height - pad.top - pad.bottom,
  };
}

// Maps a value in [min, max] to its y coordinate: the maximum sits at the top of the plot.
export function projectY(value: number, min: number, max: number, area: PlotArea): number {
  const range = max - min || 1;
  return area.top + (1 - (value - min) / range) * area.height;
}

// Spreads `count` points across the plot width; a single point sits at the centre.
export function projectX(index: number, count: number, area: PlotArea): number {
  if (count <= 1) return area.left + area.width / 2;
  return area.left + (index / (count - 1)) * area.width;
}

export interface CumulativePoint {
  period: string;
  portfolio: number;
  // The blend has no point before the first Snapshot, and a month with no priced return breaks the
  // line instead of reading as flat 0,00%.
  benchmark?: number;
  // Each policy index's cumulative line; a missing point is a gap, never a carried-forward value.
  indexes: Partial<Record<BenchmarkIndex, number>>;
}

// Both the portfolio and the blend start at 100 and compound each month's percentage return, and
// each policy index carries its own line from the same base. The first snapshot is the anchor: it
// has no portfolio return, so it does not compound any line — otherwise a benchmark priced for that
// month would start ahead of the portfolio. A month without a priced return leaves a gap on the
// line (the running value is kept for the next priced month), so a data hole never reads as 0,00%.
export function cumulativeReturns(months: PeriodPerformance[]): CumulativePoint[] {
  let portfolio = 100;
  let benchmarkRunning: number | undefined;
  const indexRunning: Partial<Record<BenchmarkIndex, number>> = {};
  const firstSnapshotIndex = months.findIndex((month) => month.source === "snapshot");

  return months.map((month, index) => {
    if (index > 0 && month.decomposition.return_percentage !== undefined) {
      portfolio *= 1 + month.decomposition.return_percentage / 100;
    }

    let benchmarkPoint: number | undefined;
    const indexPoints: Partial<Record<BenchmarkIndex, number>> = {};

    if (index === firstSnapshotIndex) {
      benchmarkRunning ??= 100;
      benchmarkPoint = benchmarkRunning;
      for (const benchmarkIndex of BENCHMARK_INDEXES) {
        indexRunning[benchmarkIndex] ??= 100;
        indexPoints[benchmarkIndex] = indexRunning[benchmarkIndex];
      }
    } else if (index > 0) {
      if (month.benchmark_return !== undefined) {
        benchmarkRunning = (benchmarkRunning ?? 100) * (1 + month.benchmark_return / 100);
        benchmarkPoint = benchmarkRunning;
      }

      const seen = new Set<BenchmarkIndex>();
      for (const row of month.benchmarks ?? []) {
        seen.add(row.index);
        if (row.return_percentage === undefined) {
          indexPoints[row.index] = undefined;
        } else {
          indexRunning[row.index] =
            (indexRunning[row.index] ?? 100) * (1 + row.return_percentage / 100);
          indexPoints[row.index] = indexRunning[row.index];
        }
      }
      for (const benchmarkIndex of BENCHMARK_INDEXES) {
        if (!seen.has(benchmarkIndex)) indexPoints[benchmarkIndex] = undefined;
      }
    }

    return { period: month.period, portfolio, benchmark: benchmarkPoint, indexes: indexPoints };
  });
}

// The contiguous runs of defined points a chart draws as one polyline, so a gap splits the line
// instead of joining two points across a month with no data.
export function contiguousSegments(values: (number | undefined)[]): number[][] {
  const segments: number[][] = [];
  let current: number[] = [];

  values.forEach((value, index) => {
    if (value === undefined) {
      if (current.length > 0) {
        segments.push(current);
        current = [];
      }
      return;
    }
    current.push(index);
  });
  if (current.length > 0) segments.push(current);

  return segments;
}

// The eras a series actually holds. A page labels the value and the XIRR from these, never from a
// guessed default.
export interface EraState {
  hasComputed: boolean;
  hasSnapshot: boolean;
}

export function eraState(months: PeriodPerformance[]): EraState {
  return {
    hasComputed: months.some((month) => month.source === "computed"),
    hasSnapshot: months.some((month) => month.source === "snapshot"),
  };
}

// The value KPI reads "Valor investido" while the last point is the computed history, because the
// computed era is Positions only and has no recorded cash basis.
export function valueKpi(months: PeriodPerformance[]): { label: string; hint?: string } {
  const last = months.at(-1);
  if (last?.source === "computed") {
    return { label: "Valor investido", hint: "histórico · sem caixa registrado" };
  }
  return { label: "Patrimônio" };
}

export interface XirrKpi {
  testId: string;
  label: string;
  value?: number;
}

// One XIRR per era that exists: the computed history and the Snapshot era are never blended.
export function xirrKpis(series: PerformanceSeries): XirrKpi[] {
  const eras = eraState(series.months);
  const kpis: XirrKpi[] = [];
  if (eras.hasComputed) {
    kpis.push({
      testId: "computed-xirr",
      label: "XIRR do histórico",
      value: series.computed_xirr,
    });
  }
  if (eras.hasSnapshot) {
    kpis.push({
      testId: "xirr",
      label: eras.hasComputed ? "XIRR dos fechamentos" : "XIRR desde o início",
      value: series.xirr,
    });
  }
  return kpis;
}

export interface BenchmarkCoverageRow {
  index: BenchmarkIndex;
  missing: number;
}

// How many months of the series each policy index could not price — the coverage strip's count.
export function benchmarkCoverage(months: PeriodPerformance[]): BenchmarkCoverageRow[] {
  return BENCHMARK_INDEXES.map((index) => ({
    index,
    missing: months.filter((month) => {
      const row = (month.benchmarks ?? []).find((entry) => entry.index === index);
      return row?.return_percentage === undefined;
    }).length,
  })).filter((row) => row.missing > 0);
}

// The targeted legs whose missing return leaves the blend unknown, so the panel can name what blocks
// it rather than showing a renormalised number.
export function blockingLegs(blend: BenchmarkBlend): BenchmarkContribution[] {
  return blend.contributions.filter(
    (contribution) => contribution.target > 0 && contribution.return_percentage === undefined,
  );
}

export interface ClassSeriesPoint {
  period: string;
  value: number;
}

export interface ClassSeries {
  assetClass: AssetClass;
  points: ClassSeriesPoint[];
}

// Each class's value at every close that carries it, in the fixed class order so the chart's colours
// and legend stay stable across months. A class missing from a snapshot contributes no point.
export function classValueSeries(months: PeriodPerformance[]): ClassSeries[] {
  const points = new Map<AssetClass, ClassSeriesPoint[]>();

  for (const month of months) {
    for (const row of month.classes ?? []) {
      const series = points.get(row.asset_class) ?? [];
      series.push({ period: month.period, value: row.value_end });
      points.set(row.asset_class, series);
    }
  }

  return ASSET_CLASSES.filter((assetClass) => points.has(assetClass)).map((assetClass) => ({
    assetClass,
    points: points.get(assetClass) ?? [],
  }));
}

export type StrategyRowKind = "strategy" | "outside";

// One row of the strategy-level decomposition: a Strategy slice, or the class's read-only outside
// remainder after it. The class line equals its strategy rows plus the outside row.
export interface StrategyRow {
  key: string;
  assetClass: AssetClass;
  label: string;
  kind: StrategyRowKind;
  strategyId?: number;
  decomposition: PerformanceDecomposition;
}

// Flattens the class rungs into their strategy rows, each class's outside row last, so the table can
// render one additive list without re-deriving the nesting.
export function strategyRows(classes: ClassPerformance[]): StrategyRow[] {
  return classes.flatMap((cls) => {
    const slices: StrategyRow[] = (cls.strategies ?? []).map((strategy) => ({
      key: `strategy:${cls.asset_class}:${strategy.strategy_id}`,
      assetClass: cls.asset_class,
      label: strategy.strategy,
      kind: "strategy",
      strategyId: strategy.strategy_id,
      decomposition: strategy.decomposition,
    }));
    const outside: StrategyRow[] = cls.outside
      ? [
          {
            key: `outside:${cls.asset_class}`,
            assetClass: cls.asset_class,
            label: "Fora da estratégia",
            kind: "outside",
            decomposition: cls.outside,
          },
        ]
      : [];
    return [...slices, ...outside];
  });
}

export interface StrategySeriesPoint {
  period: string;
  value: number;
}

export interface StrategySeries {
  strategyId: number;
  strategy: string;
  kind: StrategyKind;
  points: StrategySeriesPoint[];
}

// Each Strategy's frozen value at every close that carries it, ordered by first appearance so the
// chart's colours and legend stay stable. A rename cannot rewrite the line: the id, name and kind
// come from the snapshot.
export function strategyValueSeries(months: PeriodPerformance[]): StrategySeries[] {
  const series = new Map<number, StrategySeries>();

  for (const month of months) {
    for (const row of month.strategies ?? []) {
      const line = series.get(row.strategy_id) ?? {
        strategyId: row.strategy_id,
        strategy: row.strategy,
        kind: row.kind,
        points: [],
      };
      line.points.push({ period: month.period, value: row.value_end });
      series.set(row.strategy_id, line);
    }
  }

  return [...series.values()];
}

export interface BenchmarkRow {
  key: string;
  label: string;
  index?: BenchmarkIndex;
  target?: number;
  returnPercentage?: number;
  // Whether the policy gives the index a positive weight; only a targeted index reads "sem dados"
  // when its return is absent.
  targeted: boolean;
}

// The policy indexes the benchmark overlay plots, in their display order. One list feeds the blend
// rows, the cumulative index series and the chart lines.
export const BENCHMARK_INDEXES: BenchmarkIndex[] = ["CDI", "IPCA", "IBOV"];

// The blend's target-weighted return beside each policy index it draws from. Every class mapped to
// an index shares that index's return, so the row sums the classes' targets and keeps the one
// return; an index no class targets stays unpriced instead of silently dropping out.
export function benchmarkRows(blend: BenchmarkBlend): BenchmarkRow[] {
  const indexRows = BENCHMARK_INDEXES.map((index) => {
    const contributions = blend.contributions.filter((row) => row.benchmark === index);
    if (contributions.length === 0) {
      return { key: index, label: index, index, targeted: false };
    }

    return {
      key: index,
      label: index,
      index,
      targeted: contributions.some((row) => row.target > 0),
      target: contributions.reduce((total, row) => total + row.target, 0),
      returnPercentage: contributions.find((row) => row.return_percentage !== undefined)
        ?.return_percentage,
    };
  });

  return [
    ...indexRows,
    {
      key: "blend",
      label: "Blend alvo",
      returnPercentage: blend.return_percentage,
      targeted: true,
    },
  ];
}

export interface Extent {
  min: number;
  max: number;
}

// The vertical domain of a bar chart: the data's range with the zero baseline always included, so
// gains and losses read against the same axis.
export function valueExtent(values: number[]): Extent {
  const min = Math.min(0, ...values);
  const max = Math.max(0, ...values);
  return { min, max };
}

export interface AportesRow {
  period: string;
  netAportes: number;
  returnPercentage?: number;
}

export function aportesPerformanceRows(months: PeriodPerformance[]): AportesRow[] {
  return months.map((month) => ({
    period: month.period,
    netAportes: month.decomposition.net_aportes,
    returnPercentage: month.decomposition.return_percentage,
  }));
}
