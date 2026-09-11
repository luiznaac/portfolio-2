import { type FormEvent, useRef, useState } from "react";
import {
  useCreateStrategy,
  useStrategies,
  useStrategyEditions,
  useUploadStrategyReport,
} from "../api/queries.ts";
import type { StrategyTarget } from "../api/types.ts";
import { Panel } from "../components/Panel.tsx";
import { formatDate } from "../lib/format.ts";
import { formatBRL, formatRatio } from "../lib/money.ts";

export function Estrategias() {
  const strategies = useStrategies();

  return (
    <div className="space-y-6">
      <h1 className="text-xl font-semibold text-slate-100">Estratégias</h1>

      <NewStrategyForm />

      {strategies.isLoading && <p className="text-sm text-slate-500">Carregando…</p>}
      {strategies.data?.length === 0 && (
        <p className="text-sm text-slate-500">Nenhuma estratégia cadastrada ainda.</p>
      )}

      {(strategies.data ?? []).map((s) => (
        <Panel key={s.id} title={s.name}>
          <StrategyPanel strategyId={s.id} />
        </Panel>
      ))}
    </div>
  );
}

function NewStrategyForm() {
  const mutation = useCreateStrategy();
  const [name, setName] = useState("");

  const submit = (e: FormEvent) => {
    e.preventDefault();
    mutation.mutate({ name }, { onSuccess: () => setName("") });
  };

  return (
    <Panel title="Nova estratégia">
      <form onSubmit={submit} className="flex items-end gap-3">
        <label className="text-sm">
          <span className="mb-1 block text-xs text-slate-500">Nome</span>
          <input
            required
            placeholder="ex.: Top, Dividendos…"
            value={name}
            onChange={(e) => setName(e.target.value)}
            className="w-56 rounded-md border border-white/10 bg-slate-900 px-3 py-2 text-slate-100 outline-none focus:border-accent-500"
          />
        </label>
        <button
          type="submit"
          disabled={mutation.isPending}
          className="rounded-md bg-accent-500 px-3 py-2 text-sm font-medium text-slate-950 transition-colors hover:bg-accent-600 disabled:opacity-50"
        >
          {mutation.isPending ? "Salvando…" : "Adicionar"}
        </button>
      </form>
    </Panel>
  );
}

function StrategyPanel({ strategyId }: { strategyId: number }) {
  const editions = useStrategyEditions(strategyId);
  const mutation = useUploadStrategyReport(strategyId);
  const fileInput = useRef<HTMLInputElement>(null);

  const onFileChosen = (file: File | undefined) => {
    if (!file) return;
    mutation.mutate(file, {
      onSuccess: () => {
        if (fileInput.current) fileInput.current.value = "";
      },
    });
  };

  const rows = [...(editions.data ?? [])].reverse();

  return (
    <div className="space-y-4">
      <label className="flex items-center gap-3 text-sm">
        <span className="text-xs text-slate-500">Importar relatório da corretora (PDF)</span>
        <input
          ref={fileInput}
          type="file"
          accept="application/pdf"
          onChange={(e) => onFileChosen(e.target.files?.[0])}
          className="text-slate-300 file:mr-3 file:rounded-md file:border-0 file:bg-accent-500 file:px-3 file:py-1.5 file:text-sm file:font-medium file:text-slate-950 hover:file:bg-accent-600"
        />
      </label>
      {mutation.isPending && <p className="text-sm text-slate-500">Processando…</p>}
      {mutation.isError && <p className="text-sm text-tax">{String(mutation.error)}</p>}

      {rows.length === 0 ? (
        <p className="text-sm text-slate-500">Nenhum relatório importado ainda.</p>
      ) : (
        <div className="space-y-4">
          {rows.map(({ edition, diff }) => (
            <div key={edition.id} className="rounded-md border border-white/10 p-4">
              <div className="mb-2 flex items-center justify-between">
                <span className="text-sm font-medium text-slate-100">
                  {formatDate(edition.reference_date)}
                </span>
                <span className="text-xs text-slate-500">{edition.targets.length} tickers</span>
              </div>

              {diff && (
                <div className="mb-3 flex flex-wrap gap-1.5 text-xs">
                  {diff.entered.map((t) => (
                    <span
                      key={`in-${t.ticker}`}
                      className="rounded bg-yield/15 px-1.5 py-0.5 text-yield"
                    >
                      + {t.ticker} {formatRatio(t.weight)}
                    </span>
                  ))}
                  {diff.exited.map((t) => (
                    <span
                      key={`out-${t.ticker}`}
                      className="rounded bg-tax/15 px-1.5 py-0.5 text-tax"
                    >
                      − {t.ticker}
                    </span>
                  ))}
                  {diff.changed.map((c) => (
                    <span
                      key={`chg-${c.ticker}`}
                      className="rounded bg-accent-500/15 px-1.5 py-0.5 text-accent-500"
                    >
                      {c.ticker} {formatRatio(c.before.weight)} → {formatRatio(c.after.weight)}
                    </span>
                  ))}
                </div>
              )}

              {edition.changes_text && (
                <p className="mb-3 text-sm italic text-slate-400">{edition.changes_text}</p>
              )}

              <TargetsTable targets={edition.targets} />
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function TargetsTable({ targets }: { targets: StrategyTarget[] }) {
  const sorted = [...targets].sort((a, b) => b.weight - a.weight);

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm tabular-nums">
        <thead>
          <tr className="text-left text-xs uppercase tracking-wide text-slate-500">
            <th className="py-1.5 pr-4 font-medium">Ticker</th>
            <th className="py-1.5 pr-4 text-right font-medium">Peso</th>
            <th className="py-1.5 pr-4 font-medium">Rating</th>
            <th className="py-1.5 text-right font-medium">Preço-alvo</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-white/5">
          {sorted.map((t) => (
            <tr key={t.ticker}>
              <td className="py-1.5 pr-4 text-slate-200">{t.ticker}</td>
              <td className="py-1.5 pr-4 text-right text-slate-300">{formatRatio(t.weight)}</td>
              <td className="py-1.5 pr-4 text-slate-400">{t.rating ?? "—"}</td>
              <td className="py-1.5 text-right text-slate-400">
                {t.target_price != null ? formatBRL(t.target_price) : "—"}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
