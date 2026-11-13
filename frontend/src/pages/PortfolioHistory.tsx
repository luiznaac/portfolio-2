import { Link } from "react-router-dom";
import { useClosedMonths } from "../api/queries.ts";
import { Chip } from "../components/Chip.tsx";
import { Panel } from "../components/Panel.tsx";
import { type Column, Table } from "../components/Table.tsx";
import { formatCurrency } from "../lib/format.ts";
import { type HistoryListRow, historyListRows } from "../lib/portfolioHistory.ts";
import { ROUTES } from "../lib/routes.ts";

// Carteira · Histórico (issue #372): the closed-month records list. One immutable snapshot per month,
// each opening its frozen detail. The whole surface is read-only — this list only navigates.
export function PortfolioHistory() {
  const closed = useClosedMonths();

  if (closed.isLoading) return <p className="text-[13px] text-muted">Carregando…</p>;

  if (closed.isError) {
    return <p className="text-[13px] text-return-down">{closed.error.message}</p>;
  }

  const rows = historyListRows(closed.data ?? []);

  return (
    <section data-testid="portfolio-history">
      {rows.length === 0 ? (
        <Panel title="Fechamentos guardados">
          <p className="text-[12.5px] text-muted">
            Nenhum mês fechado ainda. O fechamento grava o primeiro snapshot —{" "}
            <Link to={ROUTES.planClose} className="text-action hover:underline">
              ir para Fechamentos
            </Link>
            .
          </p>
        </Panel>
      ) : (
        <Table columns={COLUMNS} rows={rows} rowKey={(row) => row.monthId} />
      )}
      <p className="mt-2.5 font-mono text-label text-faint">
        Um snapshot imutável por mês; abra um registro para ver o que o fechamento congelou.
      </p>
    </section>
  );
}

const COLUMNS: Column<HistoryListRow>[] = [
  {
    key: "period",
    header: "Mês",
    render: (row) => (
      <Link to={row.href} className="font-mono text-num text-action hover:underline">
        {row.label}
      </Link>
    ),
  },
  {
    key: "capital",
    header: "Capital",
    align: "right",
    render: (row) => <span className="font-mono">{formatCurrency(row.capital)}</span>,
  },
  {
    key: "floor",
    header: "Piso",
    align: "right",
    render: (row) => <span className="font-mono">{formatCurrency(row.floor)}</span>,
  },
  {
    key: "aportes",
    header: "Aportes",
    align: "right",
    render: (row) => <span className="font-mono">{formatCurrency(row.aportes)}</span>,
  },
  {
    key: "state",
    header: "Estado",
    render: () => <Chip tone="green">Fechado</Chip>,
  },
];
