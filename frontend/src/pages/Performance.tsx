import { Link } from "react-router-dom";
import { useClosedMonths, useMonthPerformance, usePerformanceSeries } from "../api/queries.ts";
import type {
  ClassPerformance,
  Month,
  MonthPerformance,
  PerformanceDecomposition,
  PerformanceSeries,
  PeriodPerformance,
  TickerPerformance,
} from "../api/types.ts";
import { ClosedMonthSelect } from "../components/ClosedMonthSelect.tsx";
import { Kpi } from "../components/Kpi.tsx";
import { Panel } from "../components/Panel.tsx";
import {
  AportesPerformance,
  ClassChart,
  CumulativeChart,
  DecompositionChart,
  ReturnChart,
  StrategyChart,
} from "../components/PerformanceCharts.tsx";
import { type Column, Table } from "../components/Table.tsx";
import { useClosedMonthSelection } from "../hooks/useClosedMonthSelection.ts";
import { cn } from "../lib/cn.ts";
import { formatCurrency, formatPercent, formatSignedPercent } from "../lib/format.ts";
import { assetClassLabel, monthPeriodLabel } from "../lib/month.ts";
import {
  type StrategyRow,
  benchmarkRows,
  blockingLegs,
  decompositionResult,
  eraState,
  returnFillClass,
  returnTextClass,
  strategyRows,
  valueKpi,
  xirrKpis,
} from "../lib/performance.ts";

// Performance (issue #176): the closed months replayed from their immutable snapshots — the monthly
// return decomposition, the since-inception XIRR, the target-weighted benchmark, the per-class and
// per-ticker views and the flow (aportes) beside the performance.
export function Performance() {
  const series = usePerformanceSeries();
  const closed = useClosedMonths();
  const months = closed.data ?? [];
  const { monthId, select: selectMonth } = useClosedMonthSelection(months);
  const detail = useMonthPerformance(monthId);

  return (
    <section>
      <div className="mb-4">
        <h1 className="font-cond text-[24px] font-bold tracking-[.005em]">Performance</h1>
        <p className="text-[13px] text-ink-dim">
          O retorno mensal depois dos aportes e proventos, o XIRR desde o início e o benchmark
          ponderado pelos alvos da política.
        </p>
      </div>

      {series.isLoading ? (
        <p className="text-[13.5px] text-ink-dim">Carregando…</p>
      ) : series.isError ? (
        <p className="text-[13.5px] text-tax">{series.error.message}</p>
      ) : !series.data ? null : series.data.months.length === 0 && series.data.ledger_empty ? (
        <Panel title="Sem meses fechados">
          <p className="text-[13.5px] text-ink-dim">
            A performance nasce dos retratos dos meses fechados e do histórico importado.{" "}
            <Link to="/imports" className="text-principal underline">
              Importe um extrato
            </Link>{" "}
            ou{" "}
            <Link to="/fechamento" className="text-principal underline">
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
      ) : (
        <PerformanceView
          series={series.data}
          months={months}
          monthId={monthId}
          onSelect={selectMonth}
          detail={detail.data ?? null}
          detailLoading={detail.isLoading}
          detailError={detail.isError ? detail.error.message : null}
        />
      )}
    </section>
  );
}

function PerformanceView({
  series,
  months,
  monthId,
  onSelect,
  detail,
  detailLoading,
  detailError,
}: {
  series: PerformanceSeries;
  months: Month[];
  monthId: number | null;
  onSelect: (monthId: number) => void;
  detail: MonthPerformance | null;
  detailLoading: boolean;
  detailError: string | null;
}) {
  const last = series.months.at(-1);
  const eras = eraState(series.months);
  const computedLast = last?.source === "computed";
  const value = valueKpi(series.months);
  const returnOfMonth = computedLast
    ? last?.decomposition.return_percentage
    : detail?.decomposition.return_percentage;
  const valueHint = [value.hint, last?.partial ? "parcial" : undefined]
    .filter((part): part is string => part !== undefined)
    .join(" · ");

  return (
    <div>
      <div className="mb-4 grid grid-cols-2 gap-3 lg:grid-cols-4">
        {xirrKpis(series).map((kpi) => (
          <Kpi
            key={kpi.testId}
            testId={kpi.testId}
            label={kpi.label}
            value={formatPercent(kpi.value)}
          />
        ))}
        <Kpi
          testId="value-kpi"
          label={value.label}
          hint={valueHint.length > 0 ? valueHint : undefined}
          value={last ? formatCurrency(last.decomposition.value_end) : "—"}
        />
        <Kpi
          label="Retorno do mês"
          hint={computedLast ? "histórico" : undefined}
          value={formatSignedPercent(returnOfMonth)}
          tone={toneOf(returnOfMonth)}
        />
        <Kpi
          label="Benchmark do mês"
          hint={computedLast ? "sem benchmark antes do fechamento" : undefined}
          value={
            computedLast ? undefined : formatSignedPercent(detail?.benchmark.return_percentage)
          }
          tone="muted"
        />
      </div>

      <div className="mb-4 grid items-start gap-3 xl:grid-cols-2">
        <Panel title="Retorno mensal">
          <ReturnChart months={series.months} />
        </Panel>
        <Panel title="Carteira × benchmark · acumulado">
          <CumulativeChart months={series.months} />
        </Panel>
      </div>

      <div className="mb-4 grid items-start gap-3 xl:grid-cols-2">
        <Panel title="Série por classe">
          <ClassChart months={series.months} />
        </Panel>
        <Panel title="Série por estratégia">
          <StrategyChart months={series.months} />
        </Panel>
      </div>

      <div className="mb-4">
        <Panel title="Decomposição mensal (R$)">
          <DecompositionChart months={series.months} />
        </Panel>
      </div>

      <div className="mb-4">
        <h2 className="mb-2 font-cond text-[13px] font-bold tracking-[.04em] text-ink-dim uppercase">
          Decomposição mensal
        </h2>
        <DecompositionTable months={series.months} />
      </div>

      <div className="mb-4">
        <Panel title="Aportes × performance">
          <AportesPerformance months={series.months} />
        </Panel>
      </div>

      <div className="mb-2 flex flex-wrap items-center justify-between gap-3">
        <h2 className="font-cond text-[13px] font-bold tracking-[.04em] text-ink-dim uppercase">
          Detalhe do mês
        </h2>
        <ClosedMonthSelect months={months} monthId={monthId} onSelect={onSelect} />
      </div>

      {!eras.hasSnapshot ? (
        <Panel>
          <p data-testid="detail-snapshot-only" className="text-[13px] text-ink-dim">
            O detalhe por benchmark, classe e ticker existe só para meses fechados. A era histórica
            mostra apenas os valores e o retorno calculados.
          </p>
        </Panel>
      ) : detailLoading ? (
        <p className="text-[13.5px] text-ink-dim">Carregando…</p>
      ) : detailError ? (
        <p className="text-[13.5px] text-tax">{detailError}</p>
      ) : detail ? (
        <MonthDetail detail={detail} />
      ) : (
        <Panel>
          <p className="text-[13px] text-ink-dim">
            Selecione um mês fechado para ver o benchmark, as classes e os tickers.
          </p>
        </Panel>
      )}
    </div>
  );
}

// The decomposition table splits at the basis change: the computed history first, then the closed
// months, with the divider between them. The divider stays visible even when no month was closed
// yet — the historical era's end is still a boundary the reader must see.
function DecompositionTable({ months }: { months: PeriodPerformance[] }) {
  const computed = months.filter((month) => month.source === "computed");
  const snapshots = months.filter((month) => month.source === "snapshot");

  return (
    <div data-testid="decomposition-table" className="space-y-2">
      {computed.length > 0 && (
        <>
          <Table columns={DECOMPOSITION_COLUMNS} rows={computed} rowKey={(month) => month.period} />
          <EraDivider hasSnapshot={snapshots.length > 0} />
        </>
      )}
      {snapshots.length > 0 && (
        <Table columns={DECOMPOSITION_COLUMNS} rows={snapshots} rowKey={(month) => month.period} />
      )}
    </div>
  );
}

function EraDivider({ hasSnapshot }: { hasSnapshot: boolean }) {
  return (
    <div
      data-testid="era-divider"
      className="flex items-center gap-3 py-1 font-mono text-[10px] tracking-[.06em] text-ink-faint uppercase"
    >
      <span className="h-px flex-1 bg-line" />
      <span>histórico · valor investido</span>
      <span className="h-px w-6 bg-line" />
      <span>{hasSnapshot ? "fechamentos · base Capital" : "sem fechamentos ainda"}</span>
      <span className="h-px flex-1 bg-line" />
    </div>
  );
}

function MonthDetail({ detail }: { detail: MonthPerformance }) {
  return (
    <div className="space-y-4">
      <Panel title="Benchmark do mês">
        <div data-testid="benchmark-panel">
          <BenchmarkRows detail={detail} />
        </div>
      </Panel>

      <div>
        <h3 className="mb-2 font-cond text-[12px] font-bold tracking-[.04em] text-ink-dim uppercase">
          Por classe
        </h3>
        <div data-testid="class-table">
          <Table columns={CLASS_COLUMNS} rows={detail.classes} rowKey={(row) => row.asset_class} />
        </div>
      </div>

      <div>
        <h3 className="mb-2 font-cond text-[12px] font-bold tracking-[.04em] text-ink-dim uppercase">
          Por estratégia
        </h3>
        <div data-testid="strategy-table">
          <Table
            columns={STRATEGY_COLUMNS}
            rows={strategyRows(detail.classes)}
            rowKey={(row) => row.key}
          />
        </div>
        <p className="pt-1 text-[11.5px] text-ink-faint">
          Cada linha é o retorno realizado da estratégia; a linha "Fora da estratégia" é o restante
          da classe. Juntas, elas somam a linha da classe.
        </p>
      </div>

      <div>
        <h3 className="mb-2 font-cond text-[12px] font-bold tracking-[.04em] text-ink-dim uppercase">
          Por ticker
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

function toneOf(value: number | undefined): "gain" | "loss" | "default" {
  if (value === undefined || value === 0) return "default";
  return value > 0 ? "gain" : "loss";
}

// The three policy index lines the cumulative chart overlays beside the blend. Each keeps its own
// reference colour; the portfolio line is the ink chrome so it reads as "you" against them.
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
// comparison; a period, class or ticker row carries it.
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
  ];

  columns.push(
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
  );

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

const DECOMPOSITION_COLUMNS: Column<PeriodPerformance>[] = [
  {
    key: "period",
    header: "Mês",
    render: (month) => (
      <span className="flex items-center gap-2">
        <span className="font-mono text-[12px] text-ink">{monthPeriodLabel(month.period)}</span>
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
  ...decompositionColumns<PeriodPerformance>((month) => month.decomposition, {
    benchmarkOf: (month) => month.benchmark_return,
  }),
];

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
