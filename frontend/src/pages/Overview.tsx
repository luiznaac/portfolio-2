import { Link } from "react-router-dom";
import {
  useCurrentAllocation,
  usePerformanceSeries,
  usePortfolioStatus,
  useStockTax,
} from "../api/queries.ts";
import type { Allocation } from "../api/types.ts";
import { Num } from "../components/Num.tsx";
import { Panel } from "../components/Panel.tsx";
import { type Column, Table } from "../components/Table.tsx";
import { formatPoints } from "../lib/allocation.ts";
import { cn } from "../lib/cn.ts";
import { formatSignedPercent } from "../lib/format.ts";
import { monthPeriodLabel } from "../lib/month.ts";
import {
  type AttentionQueueView,
  type AttentionRow,
  type ClassRow,
  type DataPanelView,
  type MonthLineView,
  type ResultsView,
  type StandfirstPart,
  type StripCell,
  attentionQueue,
  classRows,
  dataStatePanel,
  monthLine,
  monthStandfirst,
  resultsPanel,
  statusCells,
} from "../lib/overview.ts";
import { toneClass } from "../lib/tones.ts";

// Carteira · Visão geral (issue #370): "how am I and what needs me" above the fold. The whole page
// reads the single status aggregate plus the class allocation, the performance series and the last
// close's tax; every sentence is written in lib/overview.ts from typed fields.
export function Overview() {
  const status = usePortfolioStatus();
  const allocation = useCurrentAllocation();
  const series = usePerformanceSeries();
  const lastClose = status.data?.last_close?.period ?? "";
  const tax = useStockTax(lastClose);
  const aggregate = status.data;

  if (status.isError) {
    return <p className="text-[13.5px] text-return-down">{status.error.message}</p>;
  }

  if (aggregate === undefined) {
    return <p className="text-[13.5px] text-muted">Carregando…</p>;
  }

  const queue = attentionQueue(aggregate.attention);
  const standfirst = monthStandfirst(aggregate, series.data);
  const results = resultsPanel(series.data, tax.data);

  return (
    <section data-testid="portfolio-overview">
      <StatusStrip cells={statusCells(aggregate)} />
      <MonthLine line={monthLine(aggregate)} />
      <Standfirst parts={standfirst} />

      <div className="grid items-start gap-3.5 lg:grid-cols-[minmax(0,1fr)_330px]">
        <div className="flex min-w-0 flex-col gap-3.5">
          <AllocationPanel
            allocation={allocation.data ?? null}
            loading={allocation.isLoading}
            error={allocation.isError}
          />
          <ResultsPanel view={results} />
        </div>

        <aside className="flex min-w-0 flex-col gap-3.5">
          <AttentionPanel queue={queue} />
          <DataPanel view={dataStatePanel(aggregate)} />
        </aside>
      </div>
    </section>
  );
}

function StatusStrip({ cells }: { cells: StripCell[] }) {
  return (
    <div className="mb-3 grid grid-cols-3 overflow-hidden rounded-panel border border-line bg-panel lg:grid-cols-6">
      {cells.map((cell) => (
        <div
          key={cell.key}
          data-testid={`strip-cell-${cell.key}`}
          className="min-w-0 border-r border-line-soft px-[11px] py-[9px] last:border-r-0"
        >
          <div className="font-mono text-label font-semibold tracking-[.12em] text-faint uppercase">
            {cell.label}
          </div>
          <div
            data-testid={`strip-${cell.key}-value`}
            className={cn(
              "mt-1 truncate font-mono text-[18px] font-semibold tracking-[-.04em] tabular-nums",
              toneClass(cell.tone),
            )}
          >
            {cell.value}
          </div>
          <div className="mt-0.5 truncate font-mono text-[10px] text-faint">{cell.hint}</div>
        </div>
      ))}
    </div>
  );
}

function MonthLine({ line }: { line: MonthLineView }) {
  return (
    <div
      data-testid="month-line"
      className="mb-2.5 flex flex-wrap items-center gap-2 border-y border-line py-[7px] text-[11.5px] text-muted"
    >
      <span className="font-mono text-label font-semibold tracking-[.14em] text-faint uppercase">
        Mês
      </span>
      <b className="font-semibold text-text">{line.period}</b>
      <span className="text-faint">/</span>
      <span>{line.planState}</span>
      <span className="text-faint">/</span>
      <span>{line.steps}</span>
      <span className="text-faint">/</span>
      <span>{line.orders}</span>
      <span className="text-faint">/</span>
      <span>{line.transfers}</span>
      <span className="flex-1" />
      <Link to={line.to} className="text-[11.5px] text-action hover:underline">
        abrir plano →
      </Link>
    </div>
  );
}

function Standfirst({ parts }: { parts: StandfirstPart[] }) {
  return (
    <p
      data-testid="standfirst"
      className="mb-3.5 max-w-[860px] text-[13.5px] leading-[1.55] text-muted"
    >
      {parts.map((part) =>
        part.strong === true ? (
          <b key={part.key} className="font-semibold text-text">
            {part.text}
          </b>
        ) : (
          <span key={part.key}>{part.text}</span>
        ),
      )}
    </p>
  );
}

function AllocationPanel({
  allocation,
  loading,
  error,
}: {
  allocation: Allocation | null;
  loading: boolean;
  error: boolean;
}) {
  const rows = classRows(allocation);
  const columns: Column<ClassRow>[] = [
    {
      key: "class",
      header: "Classe",
      render: (row) => <span className="font-medium text-text">{row.label}</span>,
    },
    {
      key: "target",
      header: "Alvo",
      align: "right",
      render: (row) => <span className="font-mono text-num text-muted">{row.target}</span>,
    },
    {
      key: "ideal",
      header: "Ideal",
      align: "right",
      render: (row) => <span className="font-mono text-num">{row.ideal}</span>,
    },
    {
      key: "actual",
      header: "Atual",
      align: "right",
      render: (row) => <span className="font-mono text-num">{row.actual}</span>,
    },
    {
      key: "distance",
      header: "Distância",
      align: "right",
      render: (row) => (
        <span className={cn("text-num", toneClass(row.distanceTone))}>{row.distance}</span>
      ),
    },
    {
      key: "drift",
      header: "Drift",
      align: "right",
      render: (row) => <Num value={row.drift} format={formatPoints} tone={row.driftTone} />,
    },
  ];

  return (
    <Panel
      title="Alocação por classe"
      action={
        <span className="font-mono text-[9.5px] text-faint">classes; estratégias em Alocação</span>
      }
    >
      <div data-testid="overview-allocation">
        {loading ? (
          <p className="text-num text-muted">Carregando…</p>
        ) : error ? (
          <p className="text-num text-return-down">Não foi possível ler a alocação.</p>
        ) : (
          <Table columns={columns} rows={rows} rowKey={(row) => row.key} />
        )}
      </div>
    </Panel>
  );
}

function ResultsPanel({ view }: { view: ResultsView }) {
  return (
    <Panel
      title={view.period === undefined ? "Resultados" : `Resultados · ${view.period}`}
      action={
        <span className="font-mono text-[9.5px] text-faint">
          12 meses encerrados · barra = retorno do mês
        </span>
      }
    >
      {view.empty ? (
        <p className="text-num text-muted">Sem meses fechados.</p>
      ) : (
        <>
          <div
            data-testid="results-sparkline"
            className="flex h-[46px] items-end gap-[3px] px-[11px] pt-2.5"
          >
            {view.bars.map((bar) => (
              <i
                key={bar.period}
                title={`${monthPeriodLabel(bar.period)} · ${formatSignedPercent(bar.percentage)}`}
                className={cn(
                  "min-w-[2px] flex-1 rounded-t-xs",
                  bar.positive ? "bg-return-up" : "bg-return-down",
                )}
                style={{ height: `${bar.height}px` }}
              />
            ))}
          </div>
          <div className="grid gap-1.5 border-t border-line-soft px-[11px] py-2">
            {view.rows.map((row) => (
              <div
                key={row.key}
                data-testid={`result-${row.key}`}
                className="flex min-w-0 items-center gap-2 text-[11px]"
              >
                <span className="truncate text-muted">{row.label}</span>
                <span className="flex-1" />
                <span
                  className={cn(
                    "font-mono text-num tabular-nums whitespace-nowrap",
                    toneClass(row.tone),
                  )}
                >
                  {row.value}
                </span>
                {row.hint !== undefined && (
                  <span className="font-mono text-[10px] whitespace-nowrap text-faint">
                    {row.hint}
                  </span>
                )}
              </div>
            ))}
          </div>
        </>
      )}
    </Panel>
  );
}

function AttentionPanel({ queue }: { queue: AttentionQueueView }) {
  return (
    <Panel
      title="Precisa de você"
      action={
        <span className="rounded-xs bg-attention px-1.5 py-[3px] font-mono text-label font-semibold text-bg">
          {queue.action.length}
        </span>
      }
    >
      <div data-testid="attention-queue">
        {queue.total === 0 ? (
          <p className="text-num text-muted">Nada precisa de você.</p>
        ) : (
          <>
            <AttentionGroup label="AÇÃO" rows={queue.action} />
            <AttentionGroup label="ACOMPANHAR" rows={queue.watch} />
          </>
        )}
      </div>
    </Panel>
  );
}

function AttentionGroup({ label, rows }: { label: string; rows: AttentionRow[] }) {
  return (
    <div>
      <div
        data-testid={`attention-group-${label}`}
        className="mb-1 font-mono text-[8.5px] font-semibold tracking-[.14em] text-faint uppercase"
      >
        {label} · {rows.length}
      </div>
      {rows.map((row) => (
        <Link
          key={row.key}
          to={row.to}
          data-testid={`attention-${row.key}`}
          className="block border-b border-line-soft py-2 last:border-b-0 hover:bg-panel-2"
        >
          <span className="flex items-start gap-2">
            <i
              className={cn(
                "mt-1 block h-[5px] w-[5px] shrink-0 rounded-full",
                row.severity === "ACTION" ? "bg-attention" : "bg-panel-3",
              )}
            />
            <span className="min-w-0">
              <span className="block text-[11.5px] text-text">{row.title}</span>
              <span className="mt-0.5 block font-mono text-[10px] text-faint">{row.detail}</span>
              <span className="mt-0.5 block font-mono text-label text-action">{row.to} →</span>
            </span>
          </span>
        </Link>
      ))}
    </div>
  );
}

function DataPanel({ view }: { view: DataPanelView }) {
  return (
    <Panel title="Estado dos dados">
      <div data-testid="data-state-panel" className="grid gap-1.5">
        {view.empty ? (
          <p className="text-num text-muted">Sem cotações, lacunas ou fechamentos registrados.</p>
        ) : (
          view.rows.map((row) => (
            <div key={row.key} className="flex min-w-0 items-center gap-2 text-[11px]">
              <span className="truncate text-muted">{row.label}</span>
              <span className="flex-1" />
              <span className={cn("truncate font-mono text-num", toneClass(row.tone))}>
                {row.value}
              </span>
            </div>
          ))
        )}
      </div>
    </Panel>
  );
}
