import type { AssetClass, BenchmarkIndex, PeriodPerformance } from "../api/types.ts";
import { cn } from "../lib/cn.ts";
import { formatCurrency, formatNumber, formatPercent, formatSignedPercent } from "../lib/format.ts";
import { assetClassLabel, monthPeriodLabel } from "../lib/month.ts";
import {
  BENCHMARK_INDEXES,
  type CumulativePoint,
  aportesPerformanceRows,
  classValueSeries,
  cumulativeReturns,
  decompositionResult,
  plotArea,
  projectX,
  projectY,
  returnFillClass,
  returnTextClass,
  strategyValueSeries,
  valueExtent,
} from "../lib/performance.ts";

// The performance history's chart layer, kept out of the page shell so each chart can change without
// touching the read orchestration or the tables.

const BENCHMARK_STYLES: Record<BenchmarkIndex, { stroke: string; fill: string }> = {
  CDI: { stroke: "stroke-cdi", fill: "bg-cdi" },
  IPCA: { stroke: "stroke-ipca", fill: "bg-ipca" },
  IBOV: { stroke: "stroke-ibov", fill: "bg-ibov" },
};

const BENCHMARK_LINES = BENCHMARK_INDEXES.map((index) => ({
  key: index,
  label: index,
  index,
  ...BENCHMARK_STYLES[index],
}));

// Each class keeps its primitive colour through every chart, so the class series and the class table
// read as the same three rungs.
const CLASS_LINES: { assetClass: AssetClass; stroke: string; fill: string }[] = [
  { assetClass: "ACOES", stroke: "stroke-principal", fill: "bg-principal" },
  { assetClass: "FIIS", stroke: "stroke-violet", fill: "bg-violet" },
  { assetClass: "RENDA_FIXA", stroke: "stroke-yield", fill: "bg-yield" },
];

// The monthly return chart: one blue bar for the portfolio and one violet bar for the target-weighted
// benchmark per closed month, sharing a zero axis so a loss reads below the line.
export function ReturnChart({ months }: { months: PeriodPerformance[] }) {
  const width = Math.max(640, months.length * 64);
  const height = 200;
  const pad = { top: 14, right: 12, bottom: 32, left: 44 };
  const area = plotArea(width, height, pad);
  const values = months.flatMap((month) => {
    const returns: number[] = [];
    if (month.decomposition.return_percentage !== undefined) {
      returns.push(month.decomposition.return_percentage);
    }
    if (month.benchmark_return !== undefined) returns.push(month.benchmark_return);
    return returns;
  });
  const extent = valueExtent(values);
  const y = (value: number) => projectY(value, extent.min, extent.max, area);
  const zero = y(0);
  const slot = area.width / months.length;
  const barWidth = Math.max(4, Math.min(12, slot * 0.26));

  return (
    <div data-testid="return-chart" className="overflow-x-auto">
      <svg
        width={width}
        height={height}
        viewBox={`0 0 ${width} ${height}`}
        role="img"
        aria-label="Retorno mensal da carteira e do benchmark"
      >
        <line
          x1={pad.left}
          y1={zero}
          x2={width - pad.right}
          y2={zero}
          className="stroke-line"
          strokeWidth={1}
        />
        <text x={4} y={pad.top + 4} className="fill-ink-faint font-mono text-[9px]">
          {formatPercent(extent.max, 1)}
        </text>
        <text x={4} y={height - pad.bottom} className="fill-ink-faint font-mono text-[9px]">
          {formatPercent(extent.min, 1)}
        </text>

        {months.map((month, index) => {
          const center = pad.left + slot * (index + 0.5);
          const portfolio = month.decomposition.return_percentage;
          const benchmark = month.benchmark_return;
          return (
            <g key={month.period}>
              {portfolio !== undefined && (
                <rect
                  x={center - barWidth - 1}
                  y={Math.min(y(portfolio), zero)}
                  width={barWidth}
                  height={Math.max(1, Math.abs(y(portfolio) - zero))}
                  className="fill-ink"
                  rx={1.5}
                />
              )}
              {benchmark !== undefined && (
                <rect
                  x={center + 1}
                  y={Math.min(y(benchmark), zero)}
                  width={barWidth}
                  height={Math.max(1, Math.abs(y(benchmark) - zero))}
                  className="fill-blend"
                  rx={1.5}
                />
              )}
              <text
                x={center}
                y={height - 12}
                textAnchor="middle"
                className="fill-ink-faint font-mono text-[9px]"
              >
                {monthPeriodLabel(month.period)}
              </text>
            </g>
          );
        })}
      </svg>
      <Legend
        items={[
          { label: "Carteira", className: "bg-ink" },
          { label: "Benchmark alvo", className: "bg-blend" },
        ]}
      />
    </div>
  );
}

// The cumulative lines: both indices start at 100 and compound the monthly returns, so the gap
// between them is the since-inception out/underperformance.
export function CumulativeChart({ months }: { months: PeriodPerformance[] }) {
  const points = cumulativeReturns(months);
  const width = 640;
  const height = 200;
  const pad = { top: 14, right: 14, bottom: 32, left: 44 };
  const area = plotArea(width, height, pad);
  const values = points.flatMap((point) => [
    point.portfolio,
    point.benchmark,
    point.indexes.CDI,
    point.indexes.IPCA,
    point.indexes.IBOV,
  ]);
  const min = Math.min(...values);
  const max = Math.max(...values);
  const padding = (max - min) * 0.12 || 1;
  const low = min - padding;
  const high = max + padding;
  const x = (index: number) => projectX(index, points.length, area);
  const y = (value: number) => projectY(value, low, high, area);
  const line = (selector: (point: CumulativePoint) => number) =>
    points.map((point, index) => `${x(index)},${y(selector(point))}`).join(" ");
  const lastPoint = points.at(-1);

  return (
    <div data-testid="cumulative-chart">
      <svg
        width="100%"
        height={height}
        viewBox={`0 0 ${width} ${height}`}
        role="img"
        aria-label="Retorno acumulado da carteira e dos benchmarks"
      >
        {[low, high].map((value) => (
          <g key={value}>
            <line
              x1={pad.left}
              y1={y(value)}
              x2={width - pad.right}
              y2={y(value)}
              className="stroke-line-soft"
              strokeWidth={1}
            />
            <text x={4} y={y(value) + 3} className="fill-ink-faint font-mono text-[9px]">
              {formatNumber(Math.round(value))}
            </text>
          </g>
        ))}

        {BENCHMARK_LINES.map((benchmark) => (
          <polyline
            key={benchmark.key}
            points={line((point) => point.indexes[benchmark.index])}
            fill="none"
            className={benchmark.stroke}
            strokeWidth={1.2}
          />
        ))}
        <polyline
          points={line((point) => point.benchmark)}
          fill="none"
          className="stroke-blend"
          strokeWidth={1.5}
        />
        <polyline
          points={line((point) => point.portfolio)}
          fill="none"
          className="stroke-ink"
          strokeWidth={1.9}
        />

        {lastPoint && (
          <>
            <text
              x={Math.min(width - pad.right, x(points.length - 1))}
              y={y(lastPoint.portfolio) - 5}
              textAnchor="end"
              className="fill-ink font-mono text-[9px]"
            >
              {formatNumber(Math.round(lastPoint.portfolio))}
            </text>
            <text x={pad.left} y={height - 12} className="fill-ink-faint font-mono text-[9px]">
              {months.length > 0 ? monthPeriodLabel(months[0].period) : ""}
            </text>
            <text
              x={width - pad.right}
              y={height - 12}
              textAnchor="end"
              className="fill-ink-faint font-mono text-[9px]"
            >
              {months.length > 0 ? monthPeriodLabel(months[months.length - 1].period) : ""}
            </text>
          </>
        )}
      </svg>
      <Legend
        items={[
          { label: "Carteira", className: "bg-ink" },
          { label: "Benchmark alvo", className: "bg-blend" },
          ...BENCHMARK_LINES.map((benchmark) => ({
            label: benchmark.label,
            className: benchmark.fill,
          })),
        ]}
      />
    </div>
  );
}

// The line-chart geometry the class and strategy series share: each line plots one rung's value at
// every close, on one currency axis. Keeping it in one place means the two series cannot drift.
interface ValueLine {
  key: string;
  label: string;
  points: { period: string; value: number }[];
  style: { stroke: string; fill: string };
}

function ValueLineChart({
  testId,
  ariaLabel,
  emptyMessage,
  months,
  lines,
}: {
  testId: string;
  ariaLabel: string;
  emptyMessage: string;
  months: PeriodPerformance[];
  lines: ValueLine[];
}) {
  if (lines.length === 0) {
    return <p className="text-[13px] text-ink-dim">{emptyMessage}</p>;
  }

  const width = 640;
  const height = 220;
  const pad = { top: 14, right: 14, bottom: 32, left: 56 };
  const area = plotArea(width, height, pad);
  const values = lines.flatMap((line) => line.points.map((point) => point.value));
  const min = Math.min(...values);
  const max = Math.max(...values);
  const padding = (max - min) * 0.12 || 1;
  const low = Math.max(0, min - padding);
  const high = max + padding;
  const periods = months.map((month) => month.period);
  const x = (period: string) => projectX(periods.indexOf(period), periods.length, area);
  const y = (value: number) => projectY(value, low, high, area);

  return (
    <div data-testid={testId}>
      <svg
        width="100%"
        height={height}
        viewBox={`0 0 ${width} ${height}`}
        role="img"
        aria-label={ariaLabel}
      >
        {[low, high].map((value) => (
          <g key={value}>
            <line
              x1={pad.left}
              y1={y(value)}
              x2={width - pad.right}
              y2={y(value)}
              className="stroke-line-soft"
              strokeWidth={1}
            />
            <text x={4} y={y(value) + 3} className="fill-ink-faint font-mono text-[9px]">
              {formatCurrency(Math.round(value))}
            </text>
          </g>
        ))}

        {lines.map((line) => (
          <polyline
            key={line.key}
            points={line.points.map((point) => `${x(point.period)},${y(point.value)}`).join(" ")}
            fill="none"
            className={line.style.stroke}
            strokeWidth={1.7}
          />
        ))}

        <text x={pad.left} y={height - 12} className="fill-ink-faint font-mono text-[9px]">
          {periods.length > 0 ? monthPeriodLabel(periods[0]) : ""}
        </text>
        <text
          x={width - pad.right}
          y={height - 12}
          textAnchor="end"
          className="fill-ink-faint font-mono text-[9px]"
        >
          {periods.length > 0 ? monthPeriodLabel(periods[periods.length - 1]) : ""}
        </text>
      </svg>
      <Legend items={lines.map((line) => ({ label: line.label, className: line.style.fill }))} />
    </div>
  );
}

// Each class's value at every close, one line per class — the per-class value series the snapshot
// history can reconstruct without re-reading the live book.
export function ClassChart({ months }: { months: PeriodPerformance[] }) {
  const lines = classValueSeries(months).map((line) => ({
    key: line.assetClass,
    label: assetClassLabel(line.assetClass),
    points: line.points,
    style: CLASS_LINES.find((entry) => entry.assetClass === line.assetClass) ?? {
      stroke: "stroke-ink",
      fill: "bg-ink",
    },
  }));

  return (
    <ValueLineChart
      testId="class-chart"
      ariaLabel="Valor por classe ao longo dos meses fechados"
      emptyMessage="Sem valores por classe nos meses fechados."
      months={months}
      lines={lines}
    />
  );
}

// The strategy colours: the bucket keeps the violet that marks "Legado" elsewhere, and the report
// slices cycle through the data palette so each line stays distinguishable.
const MANUAL_STRATEGY_STYLE = { stroke: "stroke-violet", fill: "bg-violet" };
const REPORT_STRATEGY_STYLES: { stroke: string; fill: string }[] = [
  { stroke: "stroke-principal", fill: "bg-principal" },
  { stroke: "stroke-yield", fill: "bg-yield" },
  { stroke: "stroke-accent", fill: "bg-accent" },
  { stroke: "stroke-cdi", fill: "bg-cdi" },
  { stroke: "stroke-ipca", fill: "bg-ipca" },
];

// Each Strategy's frozen value at every close, the bucket included — the legacy book's evolution
// beside the report-driven slices, read from the snapshot slices so a rename cannot rewrite it.
export function StrategyChart({ months }: { months: PeriodPerformance[] }) {
  let reportIndex = 0;
  const lines = strategyValueSeries(months).map((line) => ({
    key: String(line.strategyId),
    label: line.strategy,
    points: line.points,
    style:
      line.kind === "MANUAL"
        ? MANUAL_STRATEGY_STYLE
        : REPORT_STRATEGY_STYLES[reportIndex++ % REPORT_STRATEGY_STYLES.length],
  }));

  return (
    <ValueLineChart
      testId="strategy-chart"
      ariaLabel="Valor por estratégia ao longo dos meses fechados"
      emptyMessage="Sem valores por estratégia nos meses fechados."
      months={months}
      lines={lines}
    />
  );
}

// The decomposition in R$: for every month, the net aportes, the net income and what is left after
// both — the month's performance (value_end − value_start − aportes − income).
export function DecompositionChart({ months }: { months: PeriodPerformance[] }) {
  const rows = months.map((month) => ({
    period: month.period,
    aportes: month.decomposition.net_aportes,
    income: month.decomposition.net_income,
    result: decompositionResult(month.decomposition),
  }));
  const width = Math.max(640, months.length * 84);
  const height = 220;
  const pad = { top: 14, right: 14, bottom: 32, left: 56 };
  const area = plotArea(width, height, pad);
  const values = rows.flatMap((row) => [
    row.aportes,
    row.income,
    ...(row.result === undefined ? [] : [row.result]),
  ]);
  const extent = valueExtent(values);
  const y = (value: number) => projectY(value, extent.min, extent.max, area);
  const zero = y(0);
  const slot = area.width / months.length;
  const barWidth = Math.max(4, Math.min(14, slot * 0.22));
  const bars = [
    { key: "aportes", fill: "fill-principal", value: (row: (typeof rows)[number]) => row.aportes },
    { key: "income", fill: "fill-yield", value: (row: (typeof rows)[number]) => row.income },
    {
      key: "result",
      fill: "fill-blend",
      value: (row: (typeof rows)[number]) => row.result,
    },
  ];

  return (
    <div data-testid="decomposition-chart" className="overflow-x-auto">
      <svg
        width={width}
        height={height}
        viewBox={`0 0 ${width} ${height}`}
        role="img"
        aria-label="Decomposição mensal em reais"
      >
        <line
          x1={pad.left}
          y1={zero}
          x2={width - pad.right}
          y2={zero}
          className="stroke-line"
          strokeWidth={1}
        />
        <text x={4} y={pad.top + 4} className="fill-ink-faint font-mono text-[9px]">
          {formatCurrency(Math.round(extent.max))}
        </text>
        <text x={4} y={height - pad.bottom} className="fill-ink-faint font-mono text-[9px]">
          {formatCurrency(Math.round(extent.min))}
        </text>

        {rows.map((row, index) => {
          const center = pad.left + slot * (index + 0.5);
          return (
            <g key={row.period}>
              {bars.map((bar, barIndex) => {
                const value = bar.value(row);
                if (value === undefined) return null;
                const barX = center + (barIndex - 1) * (barWidth + 1) - barWidth / 2;
                return (
                  <rect
                    key={bar.key}
                    x={barX}
                    y={Math.min(y(value), zero)}
                    width={barWidth}
                    height={Math.max(1, Math.abs(y(value) - zero))}
                    className={bar.fill}
                    rx={1.5}
                  />
                );
              })}
              <text
                x={center}
                y={height - 12}
                textAnchor="middle"
                className="fill-ink-faint font-mono text-[9px]"
              >
                {monthPeriodLabel(row.period)}
              </text>
            </g>
          );
        })}
      </svg>
      <Legend
        items={[
          { label: "Aportes", className: "bg-principal" },
          { label: "Proventos", className: "bg-yield" },
          { label: "Performance", className: "bg-blend" },
        ]}
      />
    </div>
  );
}

export function AportesPerformance({ months }: { months: PeriodPerformance[] }) {
  const rows = aportesPerformanceRows(months);
  const maxAportes = rows.reduce((max, row) => Math.max(max, Math.abs(row.netAportes)), 0);
  const maxReturn = rows.reduce(
    (max, row) => Math.max(max, Math.abs(row.returnPercentage ?? 0)),
    0,
  );

  return (
    <div data-testid="aportes-performance">
      <div className="space-y-1.5">
        {rows.map((row) => (
          <div
            key={row.period}
            className="grid grid-cols-[62px_92px_1fr_1fr_62px] items-center gap-2"
          >
            <span className="font-mono text-[10.5px] text-ink-faint">
              {monthPeriodLabel(row.period)}
            </span>
            <span className="text-right font-mono text-[11.5px] text-ink-dim">
              {formatCurrency(row.netAportes)}
            </span>
            <div className="h-1.5 rounded-full bg-surface-3">
              <div
                className={cn(
                  "h-full rounded-full",
                  row.netAportes < 0 ? "bg-accent" : "bg-principal",
                )}
                style={{
                  width:
                    maxAportes === 0 ? "0%" : `${(Math.abs(row.netAportes) / maxAportes) * 100}%`,
                }}
              />
            </div>
            <div className="h-1.5 rounded-full bg-surface-3">
              <div
                className={cn("h-full rounded-full", returnFillClass(row.returnPercentage))}
                style={{
                  width:
                    maxReturn === 0
                      ? "0%"
                      : `${(Math.abs(row.returnPercentage ?? 0) / maxReturn) * 100}%`,
                }}
              />
            </div>
            <span
              className={cn(
                "text-right font-mono text-[11.5px]",
                returnTextClass(row.returnPercentage),
              )}
            >
              {formatSignedPercent(row.returnPercentage)}
            </span>
          </div>
        ))}
      </div>
      <Legend
        items={[
          { label: "Aportes", className: "bg-principal" },
          { label: "Retorno", className: "bg-yield" },
        ]}
      />
      <p className="pt-1 text-[11.5px] text-ink-faint">
        Aportes positivos entram na carteira; negativos são resgates. Cada barra é normalizada na
        própria escala; o retorno é o do mês já líquido de aportes e proventos.
      </p>
    </div>
  );
}

export function Legend({ items }: { items: { label: string; className: string }[] }) {
  return (
    <div className="mt-2 flex flex-wrap gap-4">
      {items.map((item) => (
        <span key={item.label} className="flex items-center gap-1.5 text-[11px] text-ink-dim">
          <span className={cn("h-1.5 w-3 rounded-full", item.className)} />
          {item.label}
        </span>
      ))}
    </div>
  );
}
