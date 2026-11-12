import { Link } from "react-router-dom";
import { useTrades } from "../api/queries.ts";
import type { Trade } from "../api/types.ts";
import { Badge } from "../components/Badge.tsx";
import { FilterBar } from "../components/FilterBar.tsx";
import { Panel } from "../components/Panel.tsx";
import { type Column, Table } from "../components/Table.tsx";
import { usePageFilters } from "../hooks/usePageFilters.ts";
import { formatNumber, formatTradeSide } from "../lib/format.ts";
import { TRADE_FILTER_FIELDS, filterTrades } from "../lib/tradeFilters.ts";

const COLUMNS: Column<Trade>[] = [
  { key: "date", header: "Data", render: (trade) => trade.date },
  {
    key: "ticker",
    header: "Ticker",
    render: (trade) => (
      <Link
        to={`/trades/${encodeURIComponent(trade.ticker)}`}
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
    render: (trade) => formatNumber(trade.unit_price),
  },
  {
    key: "source",
    header: "Origem",
    render: (trade) => (trade.source === "MANUAL" ? "Manual" : "B3"),
  },
];

export function Trades() {
  const filters = usePageFilters(TRADE_FILTER_FIELDS);
  const trades = useTrades();

  const rows = trades.data ?? [];
  const filtered = filterTrades(rows, filters.values);

  return (
    <section>
      <h1 className="mb-4 font-cond text-[24px] font-bold tracking-[.005em]">Ativos</h1>
      <Panel title="Negócios">
        {trades.isLoading ? (
          <p className="text-[13.5px] text-ink-dim">Carregando…</p>
        ) : rows.length === 0 ? (
          <p className="text-[13.5px] text-ink-dim">Nenhum negócio encontrado.</p>
        ) : (
          <FilterBar
            fields={TRADE_FILTER_FIELDS}
            values={filters.values}
            onChange={filters.setFilter}
            onClear={filters.clear}
            shown={filtered.length}
            total={rows.length}
          >
            <Table columns={COLUMNS} rows={filtered} rowKey={(trade) => trade.id} />
          </FilterBar>
        )}
      </Panel>
    </section>
  );
}
