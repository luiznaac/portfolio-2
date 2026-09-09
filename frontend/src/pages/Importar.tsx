import { useState } from "react";
import { useConfirmBrokerageNote, usePreviewBrokerageNote } from "../api/queries.ts";
import type { ImportedTrade } from "../api/types.ts";
import { Panel } from "../components/Panel.tsx";
import { formatBRL } from "../lib/money.ts";

// Staging/review screen for the B3 "Negociação de Ativos" export — nothing enters the ledger
// before the user reviews and confirms each row (plan's Fase 4). Dividends/JCP and automatic
// corporate-action detection are out of scope here; only trades from that one sheet.
export function Importar() {
  const preview = usePreviewBrokerageNote();
  const confirm = useConfirmBrokerageNote();
  const [file, setFile] = useState<File | null>(null);
  const [checked, setChecked] = useState<Record<number, boolean>>({});

  const upload = () => {
    if (!file) return;
    preview.mutate(file, {
      onSuccess: (data) => {
        const initial: Record<number, boolean> = {};
        data.trades.forEach((t, i) => {
          if (t.resolvable) initial[i] = true;
        });
        setChecked(initial);
      },
    });
  };

  const trades = preview.data?.trades ?? [];
  const selected = trades.filter((_, i) => checked[i]);

  const doConfirm = () => {
    confirm.mutate(
      selected.map((t) => ({ ticker: t.ticker, date: t.date, quantity: t.quantity, price: t.price })),
    );
  };

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <h1 className="text-xl font-semibold text-slate-100">Importar extrato B3</h1>

      <Panel title="Extrato de negociação (.xlsx)">
        <div className="flex flex-wrap items-center gap-3">
          <input
            type="file"
            accept=".xlsx"
            onChange={(e) => {
              setFile(e.target.files?.[0] ?? null);
              preview.reset();
              confirm.reset();
              setChecked({});
            }}
            className="text-sm text-slate-400 file:mr-3 file:rounded-md file:border-0 file:bg-slate-800 file:px-3 file:py-2 file:text-slate-200"
          />
          <button
            onClick={upload}
            disabled={!file || preview.isPending}
            className="rounded-md bg-accent-500 px-4 py-2 text-sm font-medium text-slate-950 transition-colors hover:bg-accent-600 disabled:opacity-50"
          >
            {preview.isPending ? "Analisando…" : "Analisar"}
          </button>
        </div>
        {preview.isError && <p className="mt-3 text-sm text-tax">{String(preview.error)}</p>}
      </Panel>

      {preview.data && (
        <Panel
          title={`Lançamentos encontrados (${trades.length})`}
          action={
            <button
              onClick={doConfirm}
              disabled={selected.length === 0 || confirm.isPending}
              className="rounded-md bg-accent-500 px-3 py-1.5 text-xs font-medium text-slate-950 transition-colors hover:bg-accent-600 disabled:opacity-50"
            >
              {confirm.isPending ? "Confirmando…" : `Confirmar (${selected.length})`}
            </button>
          }
        >
          {preview.data.unresolved_tickers.length > 0 && (
            <p className="mb-3 text-xs text-tax">
              Ticker(s) não cadastrado(s), não podem ser confirmados:{" "}
              {preview.data.unresolved_tickers.join(", ")}
            </p>
          )}

          <div className="overflow-x-auto">
            <table className="w-full text-sm tabular-nums">
              <thead>
                <tr className="text-left text-xs uppercase tracking-wide text-slate-500">
                  <th className="py-2 pr-2 font-medium" />
                  <th className="py-2 pr-4 font-medium">Data</th>
                  <th className="py-2 pr-4 font-medium">Ticker</th>
                  <th className="py-2 pr-4 text-right font-medium">Qtd.</th>
                  <th className="py-2 pr-4 text-right font-medium">Preço</th>
                  <th className="py-2 text-right font-medium">Valor</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-white/5">
                {trades.map((t, i) => (
                  <TradeRow
                    key={i}
                    trade={t}
                    checked={checked[i] ?? false}
                    onToggle={(v) => setChecked((prev) => ({ ...prev, [i]: v }))}
                  />
                ))}
              </tbody>
            </table>
          </div>

          {confirm.isError && <p className="mt-3 text-sm text-tax">{String(confirm.error)}</p>}
          {confirm.isSuccess && (
            <p className="mt-3 text-sm text-yield">{confirm.data.length} negociação(ões) registrada(s).</p>
          )}
        </Panel>
      )}
    </div>
  );
}

function TradeRow({
  trade,
  checked,
  onToggle,
}: {
  trade: ImportedTrade;
  checked: boolean;
  onToggle: (checked: boolean) => void;
}) {
  return (
    <tr className={trade.matches_plan ? "bg-yield/5" : undefined}>
      <td className="py-2 pr-2">
        <input
          type="checkbox"
          checked={checked}
          disabled={!trade.resolvable}
          onChange={(e) => onToggle(e.target.checked)}
        />
      </td>
      <td className="py-2 pr-4 text-slate-300">{trade.date}</td>
      <td className="py-2 pr-4 text-slate-200">
        {trade.ticker}
        {!trade.resolvable && (
          <span className="ml-1.5 rounded bg-tax/15 px-1 py-0.5 text-[10px] text-tax">não cadastrado</span>
        )}
        {trade.matches_plan && (
          <span className="ml-1.5 rounded bg-yield/15 px-1 py-0.5 text-[10px] text-yield">no plano</span>
        )}
      </td>
      <td className={`py-2 pr-4 text-right ${trade.quantity < 0 ? "text-accent-500" : "text-slate-200"}`}>
        {trade.quantity}
      </td>
      <td className="py-2 pr-4 text-right text-slate-200">{formatBRL(trade.price)}</td>
      <td className="py-2 text-right text-slate-200">{formatBRL(trade.notional)}</td>
    </tr>
  );
}
