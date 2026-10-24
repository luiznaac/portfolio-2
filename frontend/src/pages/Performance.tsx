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
} from "../components/PerformanceCharts.tsx";
import { type Column, Table } from "../components/Table.tsx";
import { useClosedMonthSelection } from "../hooks/useClosedMonthSelection.ts";
import { cn } from "../lib/cn.ts";
import { formatCurrency, formatPercent, formatSignedPercent } from "../lib/format.ts";
import { assetClassLabel, monthPeriodLabel } from "../lib/month.ts";
import {
  benchmarkRows,
  decompositionResult,
  returnFillClass,
  returnTextClass,
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
      ) : !series.data || series.data.months.length === 0 ? (
        <Panel title="Sem meses fechados">
          <p className="text-[13.5px] text-ink-dim">
            A performance nasce dos retratos dos meses fechados.{" "}
            <Link to="/fechamento" className="text-principal underline">
              Feche um mês
            </Link>{" "}
            para começar a série.
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

  return (
    <div>
      <div className="mb-4 grid grid-cols-2 gap-3 lg:grid-cols-4">
        <Kpi testId="xirr" label="XIRR desde o início" value={formatPercent(series.xirr)} />
        <Kpi label="Patrimônio" value={last ? formatCurrency(last.decomposition.value_end) : "—"} />
        <Kpi
          label="Retorno do mês"
          value={formatSignedPercent(detail?.decomposition.return_percentage)}
          tone={toneOf(detail?.decomposition.return_percentage)}
        />
        <Kpi
          label="Benchmark do mês"
          value={formatSignedPercent(detail?.benchmark.return_percentage)}
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
        <Panel title="Decomposição mensal (R$)">
          <DecompositionChart months={series.months} />
        </Panel>
      </div>

      <div className="mb-4">
        <h2 className="mb-2 font-cond text-[13px] font-bold tracking-[.04em] text-ink-dim uppercase">
          Decomposição mensal
        </h2>
        <div data-testid="decomposition-table">
          <Table
            columns={DECOMPOSITION_COLUMNS}
            rows={series.months}
            rowKey={(month) => month.period}
          />
        </div>
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

      {detailLoading ? (
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
  const maxAbs = rows.reduce((max, row) => Math.max(max, Math.abs(row.returnPercentage ?? 0)), 0);

  if (maxAbs === 0) {
    return <p className="text-[13px] text-ink-dim">Sem benchmark precificado neste mês.</p>;
  }

  return (
    <div className="space-y-2">
      {rows.map((row) => (
        <div key={row.key} className="grid grid-cols-[52px_48px_1fr_64px] items-center gap-2">
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
                width: `${(Math.abs(row.returnPercentage ?? 0) / maxAbs) * 100}%`,
              }}
            />
          </div>
          <span className="text-right font-mono text-[11.5px]">
            {formatSignedPercent(row.returnPercentage)}
          </span>
        </div>
      ))}
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

function decompositionColumns<T>(
  decompositionOf: (row: T) => PerformanceDecomposition,
  benchmarkOf: (row: T) => number | undefined,
): Column<T>[] {
  return [
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
    {
      key: "benchmark",
      header: "Benchmark",
      align: "right",
      render: (row) => (
        <span className="font-mono text-ink-dim">{formatSignedPercent(benchmarkOf(row))}</span>
      ),
    },
  ];
}

const DECOMPOSITION_COLUMNS: Column<PeriodPerformance>[] = [
  {
    key: "period",
    header: "Mês",
    render: (month) => (
      <span className="font-mono text-[12px] text-ink">{monthPeriodLabel(month.period)}</span>
    ),
  },
  ...decompositionColumns<PeriodPerformance>(
    (month) => month.decomposition,
    (month) => month.benchmark_return,
  ),
];

const CLASS_COLUMNS: Column<ClassPerformance>[] = [
  {
    key: "class",
    header: "Classe",
    render: (row) => (
      <span className="text-[12.5px] text-ink">{assetClassLabel(row.asset_class)}</span>
    ),
  },
  ...decompositionColumns<ClassPerformance>(
    (row) => row.decomposition,
    (row) => row.benchmark_return,
  ),
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
  ...decompositionColumns<TickerPerformance>(
    (row) => row.decomposition,
    (row) => row.benchmark_return,
  ),
];
