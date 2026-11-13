import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { useCurrentAllocation, useGaps, usePositions } from "../api/queries.ts";
import { BulkClassifyModal } from "../components/BulkClassifyModal.tsx";
import { Button } from "../components/Button.tsx";
import { Chip } from "../components/Chip.tsx";
import { ClassBadge } from "../components/ClassBadge.tsx";
import { FilterBar } from "../components/FilterBar.tsx";
import { Panel } from "../components/Panel.tsx";
import { type Column, Table } from "../components/Table.tsx";
import { TickerDrawer } from "../components/TickerDrawer.tsx";
import { usePageFilters } from "../hooks/usePageFilters.ts";
import { formatCurrency, formatIsoDate, formatNumber } from "../lib/format.ts";
import { assistantPath } from "../lib/importDestinations.ts";
import {
  type PositionRow,
  type PositionsTotal,
  positionRows,
  positionsTotal,
} from "../lib/position.ts";
import { POSITION_FILTER_FIELDS, filterPositions } from "../lib/positionFilters.ts";
import { ROUTES, positionDetail } from "../lib/routes.ts";

const COLUMNS: Column<PositionRow>[] = [
  {
    key: "ticker",
    header: "Ticker",
    render: (row) => <span className="font-mono font-medium text-principal">{row.ticker}</span>,
  },
  {
    key: "class",
    header: "Classe",
    render: (row) => <ClassBadge assetClass={row.assetClass} />,
  },
  {
    key: "quantity",
    header: "Qtd",
    align: "right",
    render: (row) => formatNumber(row.quantity),
  },
  {
    key: "average",
    header: "Preço médio",
    align: "right",
    render: (row) => formatCurrency(row.averagePrice),
  },
  {
    key: "close",
    header: "Fechamento",
    align: "right",
    render: (row) =>
      row.close === undefined ? (
        <span className="text-ink-dim">—</span>
      ) : (
        <span title={row.closeDate === undefined ? undefined : formatIsoDate(row.closeDate)}>
          {formatCurrency(row.close)}
        </span>
      ),
  },
  {
    key: "value",
    header: "Valor",
    align: "right",
    render: (row) =>
      row.value === undefined ? <span className="text-ink-dim">—</span> : formatCurrency(row.value),
  },
  { key: "signals", header: "Sinais", render: (row) => <Signals row={row} /> },
];

// The stale-quote and gap chips. Colour marks severity, but the text names the state so the row
// reads without it.
function Signals({ row }: { row: PositionRow }) {
  const noSignals = !row.stale && row.gaps === 0;
  return (
    <span className="flex flex-wrap items-center gap-1.5">
      {row.stale && (
        <Chip tone="amber">
          desatualizada{row.closeDate === undefined ? "" : ` · ${formatIsoDate(row.closeDate)}`}
        </Chip>
      )}
      {row.gaps > 0 && (
        <Chip tone="rose">
          {row.gaps} {row.gaps === 1 ? "lacuna" : "lacunas"}
        </Chip>
      )}
      {noSignals && <span className="text-ink-dim">—</span>}
    </span>
  );
}

const FOOTER_CELL = "border-t border-line px-2.5 py-2";

// The total follows the filtered table, never a window-level KPI: narrowing hides rows and the total
// moves with them. An unknown value keeps the total partial rather than reading as a real zero.
function TotalRow({ total }: { total: PositionsTotal }) {
  return (
    <tr data-testid="positions-total" className="bg-panel-2 font-mono text-num">
      <td colSpan={5} className={`${FOOTER_CELL} text-left text-[10px] tracking-[.08em] uppercase`}>
        Total{total.partial ? " · parcial" : ""}
      </td>
      <td data-testid="positions-total-value" className={`${FOOTER_CELL} text-right`}>
        {total.value === undefined ? "—" : formatCurrency(total.value)}
      </td>
      <td className={FOOTER_CELL} />
    </tr>
  );
}

// Ativos · Posições (#378): the custody table joined to the allocation read for class, close, value
// and staleness, narrowed by the shared filter bar. A row opens the ticker drawer, which is also a
// deep link at `/ativos/positions/:ticker` for Alocação and attention links.
export function Positions() {
  const positions = usePositions();
  const allocation = useCurrentAllocation();
  const gaps = useGaps();
  const filters = usePageFilters(POSITION_FILTER_FIELDS);
  const [bulkOpen, setBulkOpen] = useState(false);
  const navigate = useNavigate();
  const { ticker } = useParams<{ ticker?: string }>();

  if (positions.isLoading || allocation.isLoading) {
    return <p className="text-[13.5px] text-ink-dim">Carregando…</p>;
  }

  if (positions.isError) {
    return <p className="text-[13.5px] text-tax">{positions.error.message}</p>;
  }

  if (allocation.isError) {
    return <p className="text-[13.5px] text-tax">{allocation.error.message}</p>;
  }

  if (positions.data === undefined || allocation.data === undefined) {
    return <p className="text-[13.5px] text-ink-dim">Carregando…</p>;
  }

  const rows = positionRows(positions.data, allocation.data, gaps.data ?? []);
  const filtered = filterPositions(rows, filters.values);
  const hasUnclassified = rows.some((row) => row.assetClass === null);

  return (
    <section>
      <Panel
        title="Posições"
        action={
          <div className="flex items-center gap-3">
            {hasUnclassified && (
              <Button variant="ghost" onClick={() => setBulkOpen(true)}>
                Classificar sem classe
              </Button>
            )}
            <Link
              to={assistantPath("POSITIONS")}
              className="text-[12px] font-medium text-accent hover:underline"
            >
              Importar custódia →
            </Link>
          </div>
        }
      >
        {rows.length === 0 ? (
          <p className="text-[13.5px] text-ink-dim">Nenhuma posição encontrada.</p>
        ) : (
          <FilterBar
            fields={POSITION_FILTER_FIELDS}
            values={filters.values}
            onChange={filters.setFilter}
            onClear={filters.clear}
            shown={filtered.length}
            total={rows.length}
          >
            <div data-testid="positions-table">
              <Table
                columns={COLUMNS}
                rows={filtered}
                rowKey={(row) => row.ticker}
                footer={<TotalRow total={positionsTotal(filtered)} />}
                onRowClick={(row) => navigate(positionDetail(row.ticker))}
              />
            </div>
          </FilterBar>
        )}
      </Panel>

      {bulkOpen && <BulkClassifyModal onClose={() => setBulkOpen(false)} />}

      {ticker !== undefined && ticker.length > 0 && (
        <TickerDrawer
          ticker={ticker}
          onClose={() => navigate(ROUTES.positions, { replace: true })}
        />
      )}
    </section>
  );
}
