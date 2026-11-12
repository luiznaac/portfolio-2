import { Link } from "react-router-dom";
import { useClosedMonths, useMonthPerformance, usePerformanceSeries } from "../api/queries.ts";
import type {
  ClassPerformance,
  MonthPerformance,
  PerformanceDecomposition,
  PerformanceSeries,
  PeriodPerformance,
  TickerPerformance,
} from "../api/types.ts";
import { BenchmarkRefresh } from "../components/BenchmarkRefresh.tsx";
import { Kpi } from "../components/Kpi.tsx";
import { Panel } from "../components/Panel.tsx";
import {
  AportesPerformance,
  ClassChart,
  CumulativeChart,
  DecompositionChart,
  StrategyChart,
} from "../components/PerformanceCharts.tsx";
import { PeriodRail } from "../components/PeriodRail.tsx";
import { type Column, Table } from "../components/Table.tsx";
import { useAnalysisWindow } from "../hooks/useAnalysisWindow.tsx";
import { type WindowRange, windowRangeLabel } from "../lib/analysisWindow.ts";
import { cn } from "../lib/cn.ts";
import {
  formatCurrency,
  formatPercent,
  formatSignedPercent,
  formatSignedValue,
} from "../lib/format.ts";
import { assetClassLabel, monthPeriodLabel } from "../lib/month.ts";
import {
  type StrategyRow,
  benchmarkRows,
  blockingLegs,
  decompositionResult,
  returnFillClass,
  returnTextClass,
  strategyRows,
  valueKpi,
  windowCumulativeReturns,
} from "../lib/performance.ts";
import { ROUTES } from "../lib/routes.ts";
import { returnTone } from "../lib/tones.ts";

// Análise · Performance: the window read over the shared period rail — the base-100 cumulative
// return, the month-by-month table, the per-class and per-strategy series, the monthly decomposition
// in R$, the aportes beside the performance, and the benchmark's freshness. The rail owns time; the
// page never grows a second period control.
export function Performance() {
  const series = usePerformanceSeries();
  const closed = useClosedMonths();
  const months = closed.data ?? [];
  const periods = series.data?.months.map((month) => month.period) ?? [];
  const lastClosedPeriod = months[0]?.period;
  const railWindow = useAnalysisWindow(periods, lastClosedPeriod);
  const anchorMonthId = months.find((month) => month.period === railWindow.anchor)?.id ?? null;
  const detail = useMonthPerformance(anchorMonthId);
  const anchorIsSnapshot =
    series.data?.months.some(
      (month) => month.period === railWindow.anchor && month.source === "snapshot",
    ) ?? false;

  return (
    <section>
      {series.isLoading ? (
        <p className="text-[13.5px] text-ink-dim">Carregando…</p>
      ) : series.isError ? (
        <p className="text-[13.5px] text-tax">{series.error.message}</p>
      ) : !series.data ? null : series.data.months.length === 0 && series.data.ledger_empty ? (
        <Panel title="Sem meses fechados">
          <p className="text-[13.5px] text-ink-dim">
            A performance nasce dos retratos dos meses fechados e do histórico importado.{" "}
            <Link to={ROUTES.imports} className="text-principal underline">
              Importe um extrato
            </Link>{" "}
            ou{" "}
            <Link to={ROUTES.planClose} className="text-principal underline">
              feche um mês
            </Link>{" "}
            para começar a série.
          </p>
        </Panel>
      ) : series.data.months.length === 0 ? (
        <Panel title="Histórico sem mês completo">
          <p className="text-[13.5px] text-ink-dim">
            O mês corrente ainda não é um ponto da série. O histórico calculado começa no mês
            seguinte ao primeiro lançamento completo.
          </p>
        </Panel>
      ) : railWindow.range === undefined || railWindow.anchor === undefined ? null : (
        <>
          <PeriodRail
            preset={railWindow.preset}
            anchor={railWindow.anchor}
            range={railWindow.range}
            bounds={railWindow.bounds}
            onPreset={railWindow.setPreset}
            onShift={railWindow.shift}
          />
          <PerformanceWindow
            series={series.data}
            range={railWindow.range}
            anchor={railWindow.anchor}
            anchorIsSnapshot={anchorIsSnapshot}
            detail={detail.data ?? null}
            detailLoading={detail.isLoading || closed.isLoading}
            detailError={detail.isError ? detail.error.message : null}
          />
        </>
      )}
    </section>
  );
}

function PerformanceWindow({
  series,
  range,
  anchor,
  anchorIsSnapshot,
  detail,
  detailLoading,
  detailError,
}: {
  series: PerformanceSeries;
  range: WindowRange;
  anchor: string;
  anchorIsSnapshot: boolean;
  detail: MonthPerformance | null;
  detailLoading: boolean;
  detailError: string | null;
}) {
  const cumulative = windowCumulativeReturns(series.months, range);
  const months = cumulative.months;
  const monthCount = months.length;
  const last = months.at(-1);
  const value = valueKpi(months);
  const benchmark = cumulative.benchmarkPercentage;
  const fullBenchmark = benchmark !== undefined && cumulative.benchmarkCount === monthCount;
  const benchmarkHint =
    benchmark === undefined
      ? "sem série no recorte"
      : fullBenchmark
        ? `${formatDeltaPoints(cumulative.returnPercentage - benchmark)} de diferença`
        : sentenceCoverage(cumulative.benchmarkCount, monthCount);
  const xirr = last?.source === "snapshot" ? series.xirr : series.computed_xirr;
  const valueHint = [value.hint, xirr === undefined ? undefined : `XIRR ${formatPercent(xirr, 1)}`]
    .filter((part): part is string => part !== undefined)
    .join(" · ");

  return (
    <div>
      <div className="mb-4 grid grid-cols-2 gap-3 lg:grid-cols-4">
        <Kpi
          testId="period-return-kpi"
          label="Retorno no período"
          value={formatSignedPercent(cumulative.returnPercentage)}
          tone={returnTone(cumulative.returnPercentage)}
          hint={`${monthCount} ${monthCount === 1 ? "mês" : "meses"} · ${windowRangeLabel(range)}`}
        />
        <Kpi
          testId="benchmark-kpi"
          label="Benchmark blend"
          value={benchmark === undefined ? undefined : formatSignedPercent(benchmark)}
          tone={returnTone(benchmark)}
          hint={benchmarkHint}
        />
        <Kpi
          testId="last-month-kpi"
          label={last?.source === "snapshot" ? "Último mês fechado" : "Último mês do recorte"}
          value={formatSignedPercent(last?.decomposition.return_percentage)}
          tone={returnTone(last?.decomposition.return_percentage)}
          hint={
            last === undefined
              ? undefined
              : `${monthPeriodLabel(last.period)} · benchmark ${formatSignedPercent(last.benchmark_return)}`
          }
        />
        <Kpi
          testId="value-kpi"
          label={value.label}
          value={last === undefined ? undefined : formatCurrency(last.decomposition.value_end)}
          hint={valueHint.length === 0 ? undefined : valueHint}
        />
      </div>

      <div className="mb-4">
        <Panel title="Rentabilidade acumulada">
          <CumulativeChart cumulative={cumulative} />
          <p data-testid="benchmark-coverage-caption" className="pt-1 text-[11.5px] text-ink-faint">
            O retorno é a decomposição mensal encadeada; aportes e proventos ficam de fora.{" "}
            {cumulative.benchmarkCount === 0
              ? "O blend não tem série neste recorte."
              : `O ${sentenceCoverage(cumulative.benchmarkCount, monthCount)} com retorno no recorte (${windowRangeLabel(range)}).`}{" "}
            {xirrCaption(series)}
          </p>
        </Panel>
      </div>

      <div className="mb-4 grid items-start gap-3 xl:grid-cols-[minmax(0,1.35fr)_minmax(0,1fr)]">
        <Panel
          title="Mês a mês"
          action={<span className="text-num text-faint">mais recente primeiro</span>}
        >
          <div data-testid="month-table">
            <Table
              columns={monthColumns(anchor)}
              rows={[...months].reverse()}
              rowKey={(month) => month.period}
            />
          </div>
        </Panel>
        <Panel
          title="Benchmark do mês-âncora"
          action={<span className="font-mono text-num text-faint">{monthPeriodLabel(anchor)}</span>}
        >
          <AnchorBenchmark
            anchor={anchor}
            anchorIsSnapshot={anchorIsSnapshot}
            detail={detail}
            detailLoading={detailLoading}
            detailError={detailError}
          />
        </Panel>
      </div>

      <div className="mb-4 grid items-start gap-3 xl:grid-cols-2">
        <Panel title="Série por classe">
          <ClassChart months={months} />
        </Panel>
        <Panel title="Série por estratégia">
          <StrategyChart months={months} />
        </Panel>
      </div>

      <div className="mb-4">
        <Panel title="Decomposição mensal (R$)">
          <DecompositionChart months={months} />
        </Panel>
      </div>

      <div className="mb-4">
        <Panel title="Aportes × performance">
          <AportesPerformance months={months} />
        </Panel>
      </div>

      {anchorIsSnapshot && detail !== null && <AnchorTables detail={detail} />}

      <div className="mb-2">
        <Panel title="Benchmarks">
          <BenchmarkRefresh />
        </Panel>
      </div>
    </div>
  );
}

function AnchorBenchmark({
  anchor,
  anchorIsSnapshot,
  detail,
  detailLoading,
  detailError,
}: {
  anchor: string;
  anchorIsSnapshot: boolean;
  detail: MonthPerformance | null;
  detailLoading: boolean;
  detailError: string | null;
}) {
  if (!anchorIsSnapshot) {
    return (
      <p data-testid="detail-snapshot-only" className="text-[13px] text-ink-dim">
        O detalhe por benchmark, classe e ticker existe só para meses fechados. A era histórica
        mostra apenas os valores e o retorno calculados.
      </p>
    );
  }
  if (detailLoading) return <p className="text-[13.5px] text-ink-dim">Carregando…</p>;
  if (detailError) return <p className="text-[13.5px] text-tax">{detailError}</p>;
  if (detail === null) {
    return (
      <p className="text-[13px] text-ink-dim">Sem decomposição para {monthPeriodLabel(anchor)}.</p>
    );
  }
  return (
    <div data-testid="benchmark-panel">
      <BenchmarkRows detail={detail} />
    </div>
  );
}

function AnchorTables({ detail }: { detail: MonthPerformance }) {
  return (
    <div className="mb-4 space-y-4">
      <div className="grid items-start gap-3 xl:grid-cols-2">
        <div>
          <h3 className="mb-2 font-cond text-[12px] font-bold tracking-[.04em] text-ink-dim uppercase">
            Por classe no mês-âncora
          </h3>
          <div data-testid="class-table">
            <Table
              columns={CLASS_COLUMNS}
              rows={detail.classes}
              rowKey={(row) => row.asset_class}
            />
          </div>
        </div>
        <div>
          <h3 className="mb-2 font-cond text-[12px] font-bold tracking-[.04em] text-ink-dim uppercase">
            Por estratégia no mês-âncora
          </h3>
          <div data-testid="strategy-table">
            <Table
              columns={STRATEGY_COLUMNS}
              rows={strategyRows(detail.classes)}
              rowKey={(row) => row.key}
            />
          </div>
          <p className="pt-1 text-[11.5px] text-ink-faint">
            Cada linha é o retorno realizado da estratégia; a linha "Fora da estratégia" é o
            restante da classe. Juntas, elas somam a linha da classe.
          </p>
        </div>
      </div>
      <div>
        <h3 className="mb-2 font-cond text-[12px] font-bold tracking-[.04em] text-ink-dim uppercase">
          Por ticker no mês-âncora
        </h3>
        <div data-testid="ticker-table">
          <Table columns={TICKER_COLUMNS} rows={detail.tickers} rowKey={(row) => row.ticker} />
        </div>
      </div>
    </div>
  );
}

function BenchmarkRows({ detail }: { detail: MonthPerformance }) {
  const rows = benchmarkRows(detail.benchmark);
  const blocking = blockingLegs(detail.benchmark);
  const maxAbs = rows.reduce((max, row) => Math.max(max, Math.abs(row.returnPercentage ?? 0)), 0);

  return (
    <div className="space-y-2">
      {rows.map((row) => {
        const absent = row.targeted && row.returnPercentage === undefined;
        return (
          <div key={row.key} className="grid grid-cols-[52px_48px_1fr_128px] items-center gap-2">
            <span
              className={cn(
                "font-mono text-[11.5px]",
                row.key === "blend" ? "text-accent" : "text-ink-dim",
              )}
            >
              {row.label}
            </span>
            <span className="font-mono text-[10.5px] text-ink-faint">
              {formatPercent(row.target, 0)}
            </span>
            <div className="h-1.5 rounded-full bg-surface-3">
              <div
                className={cn("h-full rounded-full", returnFillClass(row.returnPercentage))}
                style={{
                  width:
                    maxAbs === 0
                      ? "0%"
                      : `${(Math.abs(row.returnPercentage ?? 0) / maxAbs) * 100}%`,
                }}
              />
            </div>
            <span
              data-testid={`benchmark-row-${row.key}`}
              className={cn(
                "text-right font-mono text-[11.5px]",
                absent ? "text-ink-faint" : "text-ink",
              )}
            >
              {absent ? "sem dados" : formatSignedPercent(row.returnPercentage)}
            </span>
          </div>
        );
      })}
      {blocking.length > 0 && (
        <p data-testid="blocking-legs" className="pt-1 text-[11.5px] text-tax">
          Sem blend: falta dado para{" "}
          {blocking
            .map((leg) =>
              leg.benchmark === undefined ? leg.label : `${leg.label} (${leg.benchmark})`,
            )
            .join(", ")}
          . Nenhum peso é renormalizado.
        </p>
      )}
      <p className="pt-1 text-[11.5px] text-ink-faint">
        O blend é a soma dos retornos de cada índice ponderada pelo alvo da classe. Antes do alvo a
        coluna mostra o peso que a política dá ao índice.
      </p>
    </div>
  );
}

function monthColumns(anchor: string): Column<PeriodPerformance>[] {
  return [
    {
      key: "period",
      header: "Mês",
      render: (month) => (
        <span className="flex items-center gap-2">
          <span
            data-testid={`month-row-${month.period}`}
            className="font-mono text-[12px] text-ink"
          >
            {monthPeriodLabel(month.period)}
          </span>
          {month.period === anchor && (
            <span
              data-testid={`month-anchor-${month.period}`}
              className="rounded-xs bg-action-bg px-1 font-mono text-[9px] tracking-[.06em] text-action uppercase"
            >
              âncora
            </span>
          )}
          {month.source === "computed" && (
            <span className="font-mono text-[9px] tracking-[.06em] text-ink-faint uppercase">
              histórico
            </span>
          )}
          {month.partial && (
            <span
              data-testid={`month-partial-${month.period}`}
              className="font-mono text-[9px] tracking-[.06em] text-tax uppercase"
            >
              parcial
            </span>
          )}
        </span>
      ),
    },
    {
      key: "return",
      header: "Rentab.",
      align: "right",
      render: (month) => (
        <span className={cn("font-mono", returnTextClass(month.decomposition.return_percentage))}>
          {formatSignedPercent(month.decomposition.return_percentage)}
        </span>
      ),
    },
    {
      key: "benchmark",
      header: "Bench.",
      align: "right",
      render: (month) => (
        <span className="font-mono text-ink-dim">
          {formatSignedPercent(month.benchmark_return)}
        </span>
      ),
    },
    {
      key: "value",
      header: "Valor",
      align: "right",
      render: (month) => (
        <span className="font-mono">{formatCurrency(month.decomposition.value_end)}</span>
      ),
    },
    {
      key: "aportes",
      header: "Aportes",
      align: "right",
      render: (month) => (
        <span className="font-mono">{formatCurrency(month.decomposition.net_aportes)}</span>
      ),
    },
    {
      key: "income",
      header: "Proventos",
      align: "right",
      render: (month) => (
        <span className="font-mono">{formatCurrency(month.decomposition.net_income)}</span>
      ),
    },
    {
      key: "result",
      header: "Resultado",
      align: "right",
      render: (month) => {
        const result = decompositionResult(month.decomposition);
        return (
          <span className={cn("font-mono", returnTextClass(result))}>
            {result === undefined ? "—" : formatCurrency(result)}
          </span>
        );
      },
    },
  ];
}

function decompositionCells(decomposition: PerformanceDecomposition) {
  const result = decompositionResult(decomposition);
  return {
    start:
      decomposition.value_start === undefined ? "—" : formatCurrency(decomposition.value_start),
    end: formatCurrency(decomposition.value_end),
    aportes: formatCurrency(decomposition.net_aportes),
    income: formatCurrency(decomposition.net_income),
    result: result === undefined ? "—" : formatCurrency(result),
    returnPercentage: formatSignedPercent(decomposition.return_percentage),
  };
}

// The shared decomposition columns. A strategy row drops the benchmark, which is a class-level
// comparison; a class or ticker row carries it.
interface DecompositionColumnOptions<T> {
  benchmarkOf?: (row: T) => number | undefined;
}

function decompositionColumns<T>(
  decompositionOf: (row: T) => PerformanceDecomposition,
  options: DecompositionColumnOptions<T> = {},
): Column<T>[] {
  const columns: Column<T>[] = [
    {
      key: "start",
      header: "Início",
      align: "right",
      render: (row) => (
        <span className="font-mono">{decompositionCells(decompositionOf(row)).start}</span>
      ),
    },
    {
      key: "end",
      header: "Fim",
      align: "right",
      render: (row) => (
        <span className="font-mono">{decompositionCells(decompositionOf(row)).end}</span>
      ),
    },
    {
      key: "aportes",
      header: "Aportes",
      align: "right",
      render: (row) => (
        <span className="font-mono">{decompositionCells(decompositionOf(row)).aportes}</span>
      ),
    },
    {
      key: "income",
      header: "Proventos",
      align: "right",
      render: (row) => (
        <span className="font-mono">{decompositionCells(decompositionOf(row)).income}</span>
      ),
    },
    {
      key: "result",
      header: "Resultado",
      align: "right",
      render: (row) => (
        <span className="font-mono">{decompositionCells(decompositionOf(row)).result}</span>
      ),
    },
    {
      key: "return",
      header: "Retorno",
      align: "right",
      render: (row) => (
        <span className={cn("font-mono", returnTextClass(decompositionOf(row).return_percentage))}>
          {decompositionCells(decompositionOf(row)).returnPercentage}
        </span>
      ),
    },
  ];

  const benchmarkOf = options.benchmarkOf;
  if (benchmarkOf) {
    columns.push({
      key: "benchmark",
      header: "Benchmark",
      align: "right",
      render: (row) => (
        <span className="font-mono text-ink-dim">{formatSignedPercent(benchmarkOf(row))}</span>
      ),
    });
  }

  return columns;
}

const CLASS_COLUMNS: Column<ClassPerformance>[] = [
  {
    key: "class",
    header: "Classe",
    render: (row) => (
      <span className="text-[12.5px] text-ink">{assetClassLabel(row.asset_class)}</span>
    ),
  },
  ...decompositionColumns<ClassPerformance>((row) => row.decomposition, {
    benchmarkOf: (row) => row.benchmark_return,
  }),
];

const TICKER_COLUMNS: Column<TickerPerformance>[] = [
  {
    key: "ticker",
    header: "Ticker",
    render: (row) => (
      <span className="font-mono text-[12px] font-semibold text-ink">{row.ticker}</span>
    ),
  },
  {
    key: "class",
    header: "Classe",
    render: (row) => (
      <span className="text-[11.5px] text-ink-dim">
        {row.asset_class ? assetClassLabel(row.asset_class) : "—"}
      </span>
    ),
  },
  ...decompositionColumns<TickerPerformance>((row) => row.decomposition, {
    benchmarkOf: (row) => row.benchmark_return,
  }),
];

const STRATEGY_COLUMNS: Column<StrategyRow>[] = [
  {
    key: "class",
    header: "Classe",
    render: (row) => (
      <span className="text-[11.5px] text-ink-dim">{assetClassLabel(row.assetClass)}</span>
    ),
  },
  {
    key: "strategy",
    header: "Estratégia",
    render: (row) => (
      <span className={cn("text-[12.5px]", row.kind === "outside" ? "text-ink-dim" : "text-ink")}>
        {row.label}
      </span>
    ),
  },
  ...decompositionColumns<StrategyRow>((row) => row.decomposition),
];

// The denominator is the months the portfolio return actually chains inside the window, not the
// preset's calendar span: early in the history a 12m window may hold 2 months of series, and calling
// that a 12-month shortfall would overstate the gap. When the series fills the window this reads
// exactly like the design's "blend cobre 3 de 12 meses".
function sentenceCoverage(count: number, total: number): string {
  return `blend cobre ${count} de ${total} ${total === 1 ? "mês" : "meses"}`;
}

// A percentage-point gap, not a percent: the sign is always present and the unit is stated.
function formatDeltaPoints(value: number): string {
  return formatSignedValue(value, (magnitude) => formatPercent(magnitude, 2).replace("%", " p.p."));
}

function xirrCaption(series: PerformanceSeries): string {
  if (series.xirr !== undefined && series.computed_xirr !== undefined) {
    return `O XIRR não é recalculado no recorte: a série expõe ${formatPercent(series.xirr, 1)} nos fechamentos e ${formatPercent(series.computed_xirr, 1)} no histórico computado — as duas eras nunca são misturadas.`;
  }
  const only = series.xirr ?? series.computed_xirr;
  return only === undefined
    ? ""
    : `O XIRR não é recalculado no recorte: a série expõe ${formatPercent(only, 1)}.`;
}
