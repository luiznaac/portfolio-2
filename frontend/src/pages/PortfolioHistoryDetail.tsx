import type { ReactNode } from "react";
import { Link, useParams } from "react-router-dom";
import { useClosedMonths, useMonthSnapshot } from "../api/queries.ts";
import type { Snapshot } from "../api/types.ts";
import { Chip } from "../components/Chip.tsx";
import { Kpi } from "../components/Kpi.tsx";
import { Num } from "../components/Num.tsx";
import { ObjectTabs } from "../components/ObjectTabs.tsx";
import { PageHead } from "../components/PageHead.tsx";
import { Panel } from "../components/Panel.tsx";
import { type Column, Table } from "../components/Table.tsx";
import { formatPoints, formatTarget } from "../lib/allocation.ts";
import { formatCurrency, formatNumber } from "../lib/format.ts";
import { monthPeriodLabel } from "../lib/month.ts";
import {
  FROZEN_CAPTION,
  type FrozenClassRow,
  type FrozenOrderRow,
  type FrozenSliceRow,
  type SnapshotSummary,
  frozenClassRows,
  frozenOrderRows,
  frozenSliceRows,
  snapshotSummary,
} from "../lib/portfolioHistory.ts";
import { ROUTES } from "../lib/routes.ts";
import { deficitTone, driftTone } from "../lib/tones.ts";

// Carteira · Histórico · frozen month (issue #372): the close's immutable record. Every panel reads
// the snapshot's own frozen state — classes, orders and unplanned trades, strategy slices — so a
// month that no longer matches the live book still renders what was recorded. No write action lives
// on this surface; the only exits are navigation.
export function PortfolioHistoryDetail() {
  const { period = "" } = useParams();
  const closed = useClosedMonths();
  const month = closed.data?.find((candidate) => candidate.period === period);
  const snapshot = useMonthSnapshot(month?.id ?? null);
  const title = `Histórico · ${monthPeriodLabel(period)}`;

  if (closed.isError) {
    return (
      <DetailFrame title={title} subtitle="Snapshot do mês fechado">
        <p className="text-[13px] text-return-down">{closed.error.message}</p>
      </DetailFrame>
    );
  }

  if (!closed.isLoading && month === undefined) {
    return (
      <DetailFrame title={title} subtitle="Mês fechado não encontrado">
        <Panel title="Mês fechado não encontrado">
          <p className="text-[12.5px] text-muted">
            O mês <span className="font-mono text-text">{period}</span> não tem snapshot gravado.
          </p>
          <Link
            to={ROUTES.portfolioHistory}
            className="mt-3 inline-block text-[12.5px] text-action hover:underline"
          >
            Voltar para Histórico
          </Link>
        </Panel>
      </DetailFrame>
    );
  }

  if (snapshot.isError) {
    return (
      <DetailFrame title={title} subtitle="Snapshot do mês fechado">
        <p className="text-[13px] text-return-down">{snapshot.error.message}</p>
      </DetailFrame>
    );
  }

  if (snapshot.data === undefined) {
    return (
      <DetailFrame title={title} subtitle="Snapshot do mês fechado">
        <p className="text-[13px] text-muted">Carregando…</p>
      </DetailFrame>
    );
  }

  const summary = snapshotSummary(snapshot.data);

  return (
    <DetailFrame
      title={title}
      subtitle={`Snapshot de ${summary.createdLabel} · ${snapshot.data.id}`}
    >
      <FrozenMonth snapshot={snapshot.data} summary={summary} />
    </DetailFrame>
  );
}

// The head and tab strip every state carries, so a loading or not-found month still renders inside
// the object shell instead of as a bare fragment.
function DetailFrame({
  title,
  subtitle,
  children,
}: {
  title: string;
  subtitle: string;
  children: ReactNode;
}) {
  return (
    <>
      <PageHead title={title} subtitle={subtitle} actions={<BackToHistory />} />
      <ObjectTabs objectKey="portfolio" />
      {children}
    </>
  );
}

function BackToHistory() {
  return (
    <Link
      to={ROUTES.portfolioHistory}
      className="rounded-xs border border-line bg-panel-2 px-2.5 py-1.5 font-mono text-label text-muted hover:border-panel-3 hover:text-text"
    >
      ← Todos os fechamentos
    </Link>
  );
}

function FrozenMonth({ snapshot, summary }: { snapshot: Snapshot; summary: SnapshotSummary }) {
  const classes = frozenClassRows(snapshot.state.classes);
  const orders = frozenOrderRows(snapshot.state.orders);
  const slices = frozenSliceRows(snapshot.state.strategies);

  return (
    <section data-testid="frozen-month">
      <div className="mb-3 grid grid-cols-2 gap-2.5 md:grid-cols-3 xl:grid-cols-5">
        <Kpi
          label="Capital"
          value={formatCurrency(summary.capital)}
          hint={`piso ${formatCurrency(summary.floor)}`}
          size="lg"
        />
        <Kpi
          label="Aportes"
          value={formatCurrency(summary.aportes)}
          hint="capital novo"
          size="lg"
        />
        <Kpi label="Caixa" value={formatCurrency(summary.caixa)} hint="não investido" size="lg" />
        <Kpi
          label="Proventos"
          value={formatCurrency(summary.incomeNet)}
          hint="recebidos no mês"
          size="lg"
        />
        <Kpi
          label="DARF"
          value={formatCurrency(summary.darf)}
          hint="apuração fechada"
          tone={summary.darf > 0 ? "attention" : "default"}
          size="lg"
        />
      </div>

      <div className="grid items-start gap-3.5 lg:grid-cols-[minmax(0,1fr)_330px]">
        <div className="flex min-w-0 flex-col gap-3.5">
          <Panel title="Classes congeladas">
            <Table
              columns={CLASS_COLUMNS}
              rows={classes}
              rowKey={(row) => row.key}
              footer={
                classes.length === 0 ? undefined : (
                  <tr>
                    <td className="px-2.5 py-1.5 text-num text-faint" colSpan={6}>
                      Valores do fechamento, não da alocação atual.
                    </td>
                  </tr>
                )
              }
            />
          </Panel>

          <Panel title="Ordens e negócios do mês">
            <Table columns={ORDER_COLUMNS} rows={orders} rowKey={(row) => row.key} />
          </Panel>
        </div>

        <aside className="flex min-w-0 flex-col gap-3.5">
          <Panel title="Estratégias no fechamento">
            <Table columns={SLICE_COLUMNS} rows={slices} rowKey={(row) => row.strategyId} />
          </Panel>

          <Panel title="O que este snapshot congela">
            <p data-testid="frozen-caption" className="text-[12.5px] leading-relaxed text-muted">
              {FROZEN_CAPTION}
            </p>
          </Panel>
        </aside>
      </div>
    </section>
  );
}

const CLASS_COLUMNS: Column<FrozenClassRow>[] = [
  { key: "class", header: "Classe", render: (row) => row.label },
  {
    key: "target",
    header: "Alvo",
    align: "right",
    render: (row) => (
      <span className="font-mono">{row.target === undefined ? "—" : formatTarget(row.target)}</span>
    ),
  },
  {
    key: "ideal",
    header: "Ideal",
    align: "right",
    render: (row) => <span className="font-mono">{formatCurrency(row.ideal)}</span>,
  },
  {
    key: "actual",
    header: "Atual",
    align: "right",
    render: (row) => <span className="font-mono">{formatCurrency(row.actual)}</span>,
  },
  {
    key: "deficit",
    header: "Déficit",
    align: "right",
    render: (row) => (
      <Num value={row.deficit} format={formatCurrency} tone={deficitTone(row.deficit)} />
    ),
  },
  {
    key: "drift",
    header: "Drift",
    align: "right",
    render: (row) => <Num value={row.drift} format={formatPoints} tone={driftTone(row.drift)} />,
  },
];

const ORDER_COLUMNS: Column<FrozenOrderRow>[] = [
  {
    key: "ticker",
    header: "Ativo",
    render: (row) => <span className="font-mono font-semibold text-text">{row.ticker}</span>,
  },
  { key: "side", header: "Lado", render: (row) => <Chip tone="neutral">{row.sideLabel}</Chip> },
  {
    key: "quantity",
    header: "Qtd.",
    align: "right",
    render: (row) => <span className="font-mono">{formatNumber(row.quantity)}</span>,
  },
  {
    key: "price",
    header: "Preço",
    align: "right",
    render: (row) =>
      row.price === undefined ? (
        <span className="text-faint">—</span>
      ) : (
        <span className="font-mono">{formatCurrency(row.price)}</span>
      ),
  },
  {
    key: "state",
    header: "Estado",
    render: (row) => <Chip tone={row.stateTone}>{row.stateLabel}</Chip>,
  },
];

const SLICE_COLUMNS: Column<FrozenSliceRow>[] = [
  { key: "strategy", header: "Estratégia", render: (row) => row.strategy },
  { key: "kind", header: "Tipo", render: (row) => row.kindLabel },
  {
    key: "value",
    header: "Valor",
    align: "right",
    render: (row) => <span className="font-mono">{formatCurrency(row.valueEnd)}</span>,
  },
  {
    key: "flow",
    header: "Fluxo",
    align: "right",
    render: (row) => <span className="font-mono">{formatCurrency(row.flow)}</span>,
  },
];
