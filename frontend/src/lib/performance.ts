// Pure performance helpers — no React imports here (see AGENTS.md). The decomposition arithmetic,
// the cumulative series the charts plot, the benchmark legend and the chart geometry live here so
// their rules are unit-tested rather than buried in the page.

import type {
  AssetClass,
  BenchmarkBlend,
  BenchmarkIndex,
  PerformanceDecomposition,
  PeriodPerformance,
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

// The month's return in R$: value_end − value_start − net aportes − net income. The first point has
// no prior value, so it has no result.
export function decompositionResult(decomposition: PerformanceDecomposition): number | undefined {
  if (decomposition.value_start === undefined) return undefined;
  return (
    decomposition.value_end -
    decomposition.value_start -
    decomposition.net_aportes -
    decomposition.net_income
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
  benchmark: number;
  indexes: Record<BenchmarkIndex, number>;
}

// Both the portfolio and the blend start at 100 and compound each month's percentage return, and
// each policy index carries its own line from the same base. The first snapshot is the anchor: it
// has no portfolio return, so it does not compound any line — otherwise a benchmark priced for that
// month would start ahead of the portfolio. A month without a benchmark carries the previous index
// forward.
export function cumulativeReturns(months: PeriodPerformance[]): CumulativePoint[] {
  let portfolio = 100;
  let benchmark = 100;
  const indexes = Object.fromEntries(BENCHMARK_INDEXES.map((index) => [index, 100])) as Record<
    BenchmarkIndex,
    number
  >;

  return months.map((month, index) => {
    if (index > 0) {
      if (month.decomposition.return_percentage !== undefined) {
        portfolio *= 1 + month.decomposition.return_percentage / 100;
      }
      if (month.benchmark_return !== undefined) {
        benchmark *= 1 + month.benchmark_return / 100;
      }
      for (const row of month.benchmarks ?? []) {
        if (row.return_percentage !== undefined) {
          indexes[row.index] *= 1 + row.return_percentage / 100;
        }
      }
    }
    return { period: month.period, portfolio, benchmark, indexes: { ...indexes } };
  });
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

export interface BenchmarkRow {
  key: string;
  label: string;
  index?: BenchmarkIndex;
  target?: number;
  returnPercentage?: number;
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
    if (contributions.length === 0) return { key: index, label: index, index };

    return {
      key: index,
      label: index,
      index,
      target: contributions.reduce((total, row) => total + row.target, 0),
      returnPercentage: contributions.find((row) => row.return_percentage !== undefined)
        ?.return_percentage,
    };
  });

  return [
    ...indexRows,
    { key: "blend", label: "Blend alvo", returnPercentage: blend.return_percentage },
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
