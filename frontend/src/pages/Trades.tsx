import { useState } from "react";
import { useTrades } from "../api/queries.ts";
import type { TradeSide } from "../api/types.ts";
import { Panel } from "../components/Panel.tsx";
import { formatNumber, formatTradeSide } from "../lib/format.ts";
import { type TradeFilters, emptyTradeFilters, filterTrades } from "../lib/tradeFilters.ts";

const FILTER_INPUT =
  "rounded-md border border-slate-700 bg-slate-900 px-2 py-1.5 text-sm text-slate-200 placeholder:text-slate-500";

const SIDES: { value: TradeSide; label: string }[] = [
  { value: "BUY", label: formatTradeSide("BUY") },
  { value: "SELL", label: formatTradeSide("SELL") },
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
      <h1 className="mb-4 text-lg font-semibold text-slate-100">Ativos</h1>
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
          <label className="flex items-center gap-2 text-sm text-slate-400">
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
          <label className="flex items-center gap-2 text-sm text-slate-400">
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
          <p className="text-sm text-slate-400">Carregando…</p>
        ) : filtered.length === 0 ? (
          <p className="text-sm text-slate-400">Nenhum negócio encontrado.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-b border-slate-800 text-xs text-slate-500 uppercase">
                  <th className="py-2 pr-3">Data</th>
                  <th className="pr-3">Ticker</th>
                  <th className="pr-3">Lado</th>
                  <th className="pr-3 text-right">Qtd</th>
                  <th className="pr-3 text-right">Preço</th>
                  <th>Origem</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((trade) => (
                  <tr key={trade.id} className="border-b border-slate-900">
                    <td className="py-2 pr-3">{trade.date}</td>
                    <td className="pr-3">{trade.ticker}</td>
                    <td
                      className={
                        trade.side === "BUY" ? "pr-3 text-emerald-400" : "pr-3 text-rose-400"
                      }
                    >
                      {formatTradeSide(trade.side)}
                    </td>
                    <td className="pr-3 text-right">{formatNumber(trade.quantity)}</td>
                    <td className="pr-3 text-right">{formatNumber(trade.unit_price)}</td>
                    <td>{trade.source === "MANUAL" ? "Manual" : "B3"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {!trades.isLoading && (
          <p className="mt-3 text-xs text-slate-500">{filtered.length} negócio(s)</p>
        )}
      </Panel>
    </section>
  );
}
