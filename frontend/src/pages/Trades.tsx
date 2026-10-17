import { useState } from "react";
import { useTrades } from "../api/queries.ts";
import type { Trade, TradeSide } from "../api/types.ts";
import { Badge } from "../components/Badge.tsx";
import { Panel } from "../components/Panel.tsx";
import { type Column, Table } from "../components/Table.tsx";
import { formatNumber, formatTradeSide } from "../lib/format.ts";
import { type TradeFilters, emptyTradeFilters, filterTrades } from "../lib/tradeFilters.ts";

const FILTER_INPUT =
  "rounded-sm border border-line bg-surface-2 px-2 py-1.5 text-[13px] text-ink placeholder:text-ink-faint";

const SIDES: { value: TradeSide; label: string }[] = [
  { value: "BUY", label: formatTradeSide("BUY") },
  { value: "SELL", label: formatTradeSide("SELL") },
];

const COLUMNS: Column<Trade>[] = [
  { key: "date", header: "Data", render: (trade) => trade.date },
  {
    key: "ticker",
    header: "Ticker",
    render: (trade) => <span className="font-mono font-medium">{trade.ticker}</span>,
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

function parseSide(value: string): TradeSide | "" {
  return value === "BUY" || value === "SELL" ? value : "";
}

export function Trades() {
  const [filters, setFilters] = useState<TradeFilters>(emptyTradeFilters);
  const trades = useTrades();

  const filtered = filterTrades(trades.data ?? [], filters);

  return (
    <section>
      <h1 className="mb-4 font-cond text-[24px] font-bold tracking-[.005em]">Ativos</h1>
      <Panel title="Negócios">
        <div className="mb-4 flex flex-wrap items-center gap-3">
          <input
            type="text"
            placeholder="Ticker"
            value={filters.ticker}
            onChange={(event) =>
              setFilters((current) => ({ ...current, ticker: event.target.value }))
            }
            className={FILTER_INPUT}
          />
          <label className="flex items-center gap-2 text-[13px] text-ink-dim">
            De
            <input
              type="date"
              value={filters.from}
              onChange={(event) =>
                setFilters((current) => ({ ...current, from: event.target.value }))
              }
              className={FILTER_INPUT}
            />
          </label>
          <label className="flex items-center gap-2 text-[13px] text-ink-dim">
            Até
            <input
              type="date"
              value={filters.to}
              onChange={(event) =>
                setFilters((current) => ({ ...current, to: event.target.value }))
              }
              className={FILTER_INPUT}
            />
          </label>
          <select
            value={filters.side}
            onChange={(event) =>
              setFilters((current) => ({ ...current, side: parseSide(event.target.value) }))
            }
            className={FILTER_INPUT}
          >
            <option value="">Todos os lados</option>
            {SIDES.map((side) => (
              <option key={side.value} value={side.value}>
                {side.label}
              </option>
            ))}
          </select>
        </div>

        {trades.isLoading ? (
          <p className="text-[13.5px] text-ink-dim">Carregando…</p>
        ) : filtered.length === 0 ? (
          <p className="text-[13.5px] text-ink-dim">Nenhum negócio encontrado.</p>
        ) : (
          <Table columns={COLUMNS} rows={filtered} rowKey={(trade) => trade.id} />
        )}

        {!trades.isLoading && (
          <p className="mt-3 font-mono text-[11.5px] text-ink-faint">
            {filtered.length} negócio(s)
          </p>
        )}
      </Panel>
    </section>
  );
}
