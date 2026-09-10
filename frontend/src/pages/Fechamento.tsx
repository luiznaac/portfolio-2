import { useState } from "react";
import {
  useCloseMonth,
  useCurrentMonthlyClose,
  useDriftAlert,
  useIncomeSummary,
  useReconcileIncome,
} from "../api/queries.ts";
import type { AssetIncomeSummary, DriftAlert, IncomeReconciliation } from "../api/types.ts";
import { assetClassLabel } from "../i18n/assetClass.ts";
import { Panel } from "../components/Panel.tsx";
import { formatBRL, formatRatio } from "../lib/money.ts";

// Fase 7: proventos previstos por ativo (usecase/income), conciliação contra o extrato mensal, o
// fechamento do mês como estado ABERTO/FECHADO (usecase/monthlyclose), e o alerta de drift.
// Nenhum job agendado — chameidor não roda localmente por padrão, então o alerta é sob demanda.
export function Fechamento() {
  const monthlyClose = useCurrentMonthlyClose();
  const closeMonth = useCloseMonth();
  const driftAlert = useDriftAlert();
  const incomeSummary = useIncomeSummary();
  const reconcile = useReconcileIncome();
  const [file, setFile] = useState<File | null>(null);

  const upload = () => {
    if (!file) return;
    reconcile.mutate(file);
  };

  return (
    <div className="space-y-6">
      <h1 className="text-xl font-semibold text-slate-100">Fechamento</h1>

      <Panel
        title="Fechamento do mês"
        action={
          monthlyClose.data?.status === "OPEN" ? (
            <button
              onClick={() => closeMonth.mutate()}
              disabled={closeMonth.isPending}
              className="rounded-md bg-accent-500 px-3 py-1.5 text-xs font-medium text-slate-950 transition-colors hover:bg-accent-600 disabled:opacity-50"
            >
              {closeMonth.isPending ? "Fechando…" : "Fechar mês"}
            </button>
          ) : undefined
        }
      >
        {monthlyClose.isLoading && <p className="text-slate-400">Carregando…</p>}
        {monthlyClose.data && (
          <p className="text-sm text-slate-300">
            {monthlyClose.data.month.slice(0, 7)} está{" "}
            <span className={monthlyClose.data.status === "CLOSED" ? "text-yield" : "text-accent-500"}>
              {monthlyClose.data.status === "CLOSED" ? "fechado" : "aberto"}
            </span>
            {monthlyClose.data.closed_at && ` desde ${monthlyClose.data.closed_at.slice(0, 16).replace("T", " ")}`}.
          </p>
        )}
      </Panel>

      <Panel title="Alerta de drift">
        {driftAlert.isLoading && <p className="text-slate-400">Carregando…</p>}
        {driftAlert.data && driftAlert.data.length === 0 && (
          <p className="text-sm text-slate-500">Nenhuma classe fora de mais de 5 p.p. do alvo.</p>
        )}
        {driftAlert.data && driftAlert.data.length > 0 && (
          <ul className="divide-y divide-white/5">
            {driftAlert.data.map((a) => (
              <DriftRow key={a.asset_class} alert={a} />
            ))}
          </ul>
        )}
      </Panel>

      <Panel title="Proventos por ativo (yield on cost)">
        {incomeSummary.isLoading && <p className="text-slate-400">Carregando…</p>}
        {incomeSummary.data && incomeSummary.data.length === 0 && (
          <p className="text-sm text-slate-500">Nenhum provento previsto ainda.</p>
        )}
        {incomeSummary.data && incomeSummary.data.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full text-sm tabular-nums">
              <thead>
                <tr className="text-left text-xs uppercase tracking-wide text-slate-500">
                  <th className="py-2 pr-4 font-medium">Ticker</th>
                  <th className="py-2 pr-4 text-right font-medium">Recebido (previsto)</th>
                  <th className="py-2 pr-4 text-right font-medium">Custo</th>
                  <th className="py-2 text-right font-medium">Yield on cost</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-white/5">
                {incomeSummary.data.map((s) => (
                  <IncomeRow key={s.listed_asset_id} summary={s} />
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Panel>

      <Panel title="Conciliação previsto × recebido">
        <div className="flex flex-wrap items-center gap-3">
          <input
            type="file"
            accept=".xlsx"
            onChange={(e) => {
              setFile(e.target.files?.[0] ?? null);
              reconcile.reset();
            }}
            className="text-sm text-slate-400 file:mr-3 file:rounded-md file:border-0 file:bg-slate-800 file:px-3 file:py-2 file:text-slate-200"
          />
          <button
            onClick={upload}
            disabled={!file || reconcile.isPending}
            className="rounded-md bg-accent-500 px-4 py-2 text-sm font-medium text-slate-950 transition-colors hover:bg-accent-600 disabled:opacity-50"
          >
            {reconcile.isPending ? "Conciliando…" : "Conciliar extrato"}
          </button>
        </div>
        {reconcile.isError && <p className="mt-3 text-sm text-tax">{String(reconcile.error)}</p>}
        {reconcile.data && (
          <div className="mt-4 overflow-x-auto">
            <table className="w-full text-sm tabular-nums">
              <thead>
                <tr className="text-left text-xs uppercase tracking-wide text-slate-500">
                  <th className="py-2 pr-4 font-medium">Mês</th>
                  <th className="py-2 pr-4 font-medium">Ticker</th>
                  <th className="py-2 pr-4 text-right font-medium">Previsto</th>
                  <th className="py-2 pr-4 text-right font-medium">Recebido</th>
                  <th className="py-2 text-right font-medium">Divergência</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-white/5">
                {reconcile.data.map((r, i) => (
                  <ReconciliationRow key={`${r.ticker}-${r.month}-${r.type}-${i}`} row={r} />
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Panel>
    </div>
  );
}

function DriftRow({ alert }: { alert: DriftAlert }) {
  return (
    <li className="flex items-center gap-3 py-2.5 text-sm">
      <span className="font-medium text-slate-100">{assetClassLabel(alert.asset_class)}</span>
      <span className="text-slate-400">
        alvo {formatRatio(alert.ideal_weight)} · atual {formatRatio(alert.current_weight)}
      </span>
      <span className="ml-auto tabular-nums text-tax">{formatRatio(alert.drift_pp)} de drift</span>
    </li>
  );
}

function IncomeRow({ summary }: { summary: AssetIncomeSummary }) {
  return (
    <tr>
      <td className="py-2 pr-4 text-slate-200">{summary.ticker}</td>
      <td className="py-2 pr-4 text-right text-yield">{formatBRL(summary.total_net)}</td>
      <td className="py-2 pr-4 text-right text-slate-400">{formatBRL(summary.cost_basis)}</td>
      <td className="py-2 text-right text-slate-200">{formatRatio(summary.yield_on_cost)}</td>
    </tr>
  );
}

function ReconciliationRow({ row }: { row: IncomeReconciliation }) {
  return (
    <tr>
      <td className="py-2 pr-4 text-slate-300">{row.month.slice(0, 7)}</td>
      <td className="py-2 pr-4 text-slate-200">{row.ticker}</td>
      <td className="py-2 pr-4 text-right text-slate-200">{formatBRL(row.expected)}</td>
      <td className="py-2 pr-4 text-right text-slate-200">{formatBRL(row.received)}</td>
      <td className={`py-2 text-right ${row.received !== row.expected ? "text-tax" : "text-slate-500"}`}>
        {row.received === row.expected ? "—" : formatBRL(row.received - row.expected)}
      </td>
    </tr>
  );
}
