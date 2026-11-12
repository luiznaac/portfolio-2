import { Link } from "react-router-dom";
import { useTradeLedger } from "../api/queries.ts";
import type { Trade } from "../api/types.ts";
import { Badge } from "../components/Badge.tsx";
import { Button } from "../components/Button.tsx";
import { FilterBar } from "../components/FilterBar.tsx";
import { ImportAppliedBanner } from "../components/ImportAppliedBanner.tsx";
import { Panel } from "../components/Panel.tsx";
import { RebuildHistory } from "../components/RebuildHistory.tsx";
import { ResumableImportStrip } from "../components/ResumableImportStrip.tsx";
import { type Column, Table } from "../components/Table.tsx";
import { usePageFilters } from "../hooks/usePageFilters.ts";
import { hasActiveFilter } from "../lib/filters.ts";
import {
  formatCurrency,
  formatIsoDate,
  formatNumber,
  formatTradeSide,
  formatTradeSource,
} from "../lib/format.ts";
import { assistantPath } from "../lib/importDestinations.ts";
import { positionDetail } from "../lib/routes.ts";
import { TRADE_FILTER_FIELDS, tradeLedgerQuery } from "../lib/tradeFilters.ts";

const COLUMNS: Column<Trade>[] = [
  { key: "date", header: "Data", render: (trade) => formatIsoDate(trade.date) },
  {
    key: "ticker",
    header: "Ticker",
    render: (trade) => (
      <Link
        to={positionDetail(trade.ticker)}
        className="font-mono font-medium text-principal hover:underline"
      >
        {trade.ticker}
      </Link>
    ),
  },
  {
    key: "side",
    header: "Lado",
    render: (trade) => (
      <Badge tone={trade.side === "BUY" ? "ok" : "warn"}>{formatTradeSide(trade.side)}</Badge>
    ),
  },
  {
    key: "quantity",
    header: "Qtd",
    align: "right",
    render: (trade) => formatNumber(trade.quantity),
  },
  {
    key: "unit_price",
    header: "Preço",
    align: "right",
    render: (trade) => formatCurrency(trade.unit_price),
  },
  {
    key: "financial",
    header: "Financeiro",
    align: "right",
    render: (trade) => formatCurrency(trade.quantity * trade.unit_price),
  },
  {
    key: "source",
    header: "Origem",
    render: (trade) => formatTradeSource(trade.source),
  },
];

const FOOTER_CELL = "border-t border-line px-2.5 py-2";

// The total follows the filtered ledger, never a window-level KPI: a narrower filter moves it. The
// server sums the whole filter, so the footer is complete even while only the first page is loaded.
function LedgerTotal({ value }: { value: number }) {
  return (
    <tr data-testid="ledger-total" className="bg-panel-2 font-mono text-num">
      <td colSpan={5} className={`${FOOTER_CELL} text-left text-[10px] tracking-[.08em] uppercase`}>
        Total filtrado
      </td>
      <td data-testid="ledger-total-value" className={`${FOOTER_CELL} text-right`}>
        {formatCurrency(value)}
      </td>
      <td className={FOOTER_CELL} />
    </tr>
  );
}

// Ativos · Negócios (#379): the trade ledger read through the paged, filtered GET /trades/ledger, with
// the filter state owned by the page. Below it the page hosts "Reconstruir histórico" — adoption
// seeds, the backfill replay and the custody cross-check — which the old Histórico screen carried.
// The import assistant's banners/strip stay above the ledger; uploading a B3 extract lands here.
export function Trades() {
  const filters = usePageFilters(TRADE_FILTER_FIELDS);
  const query = tradeLedgerQuery(filters.values);
  const ledger = useTradeLedger(query);

  const rows = ledger.data?.pages.flatMap((page) => page.rows) ?? [];
  const total = ledger.data?.pages[0]?.total ?? 0;
  const financialTotal = ledger.data?.pages[0]?.financial_total ?? 0;
  const narrowing = hasActiveFilter(filters.values);

  return (
    <section className="space-y-4">
      <ImportAppliedBanner />
      <ResumableImportStrip destination="TRADES" />
      <Panel
        title="Negócios"
        action={
          <Link
            to={assistantPath("TRADES")}
            className="text-[12px] font-medium text-accent hover:underline"
          >
            Enviar extrato →
          </Link>
        }
      >
        {ledger.isLoading ? (
          <p className="text-[13.5px] text-ink-dim">Carregando…</p>
        ) : total === 0 && !narrowing ? (
          <p className="text-[13.5px] text-ink-dim">Nenhum negócio encontrado.</p>
        ) : (
          <FilterBar
            fields={TRADE_FILTER_FIELDS}
            values={filters.values}
            onChange={filters.setFilter}
            onClear={filters.clear}
            shown={rows.length}
            total={total}
          >
            <div data-testid="ledger-table">
              <Table
                columns={COLUMNS}
                rows={rows}
                rowKey={(trade) => trade.id}
                footer={<LedgerTotal value={financialTotal} />}
              />
            </div>

            {ledger.hasNextPage && (
              <div className="mt-3">
                <Button onClick={() => ledger.fetchNextPage()} disabled={ledger.isFetchingNextPage}>
                  {ledger.isFetchingNextPage ? "Carregando…" : "Carregar mais"}
                </Button>
              </div>
            )}
          </FilterBar>
        )}
      </Panel>

      <RebuildHistory />
    </section>
  );
}
