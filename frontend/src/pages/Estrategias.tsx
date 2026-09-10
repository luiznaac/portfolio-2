import { useRef, useState, type FormEvent } from "react";
import {
  useCreateStrategy,
  useSetStrategyWeight,
  useStrategies,
  useStrategyEditions,
  useStrategyWeightHistory,
  useUploadStrategyReport,
} from "../api/queries.ts";
import type { AssetClass, Strategy, StrategyTarget } from "../api/types.ts";
import { ASSET_CLASSES, assetClassLabel } from "../i18n/assetClass.ts";
import { Panel } from "../components/Panel.tsx";
import { formatBRL, formatRatio } from "../lib/money.ts";
import { formatDate } from "../lib/format.ts";

export function Estrategias() {
  const strategies = useStrategies();
  const weights = useStrategyWeightHistory();

  const currentWeightByStrategy = new Map<number, number>();
  for (const w of weights.data ?? []) currentWeightByStrategy.set(w.strategy_id, w.weight);

  return (
    <div className="space-y-6">
      <h1 className="text-xl font-semibold text-slate-100">Estratégias</h1>

      <NewStrategyForm />

      {strategies.isLoading && <p className="text-sm text-slate-500">Carregando…</p>}
      {strategies.data?.length === 0 && (
        <p className="text-sm text-slate-500">Nenhuma estratégia cadastrada ainda.</p>
      )}

      {(strategies.data ?? []).map((s) => (
        <Panel
          key={s.id}
          title={s.name}
          action={
            <span className="rounded bg-slate-800 px-1.5 py-0.5 text-[10px] uppercase text-slate-400">
              {assetClassLabel(s.asset_class)}
            </span>
          }
        >
          <StrategyPanel strategy={s} currentWeight={currentWeightByStrategy.get(s.id)} />
        </Panel>
      ))}
    </div>
  );
}

function NewStrategyForm() {
  const mutation = useCreateStrategy();
  const [name, setName] = useState("");
  const [assetClass, setAssetClass] = useState<AssetClass>("STOCKS");

  const submit = (e: FormEvent) => {
    e.preventDefault();
    mutation.mutate({ name, asset_class: assetClass }, { onSuccess: () => setName("") });
  };

  return (
    <Panel title="Nova estratégia">
      <form onSubmit={submit} className="flex flex-wrap items-end gap-3">
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
        <label className="text-sm">
          <span className="mb-1 block text-xs text-slate-500">Classe</span>
          <select
            value={assetClass}
            onChange={(e) => setAssetClass(e.target.value as AssetClass)}
            className="rounded-md border border-white/10 bg-slate-900 px-3 py-2 text-slate-100 outline-none focus:border-accent-500"
          >
            {ASSET_CLASSES.map((c) => (
              <option key={c} value={c}>
                {assetClassLabel(c)}
              </option>
            ))}
          </select>
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

function StrategyPanel({
  strategy,
  currentWeight,
}: {
  strategy: Strategy;
  currentWeight: number | undefined;
}) {
  const editions = useStrategyEditions(strategy.id);
  const uploadMutation = useUploadStrategyReport(strategy.id);
  const weightMutation = useSetStrategyWeight(strategy.id);
  const fileInput = useRef<HTMLInputElement>(null);
  const [weightPct, setWeightPct] = useState("");

  const onFileChosen = (file: File | undefined) => {
    if (!file) return;
    uploadMutation.mutate(file, {
      onSuccess: () => {
        if (fileInput.current) fileInput.current.value = "";
      },
    });
  };

  const submitWeight = (e: FormEvent) => {
    e.preventDefault();
    weightMutation.mutate(
      { weight: Number(weightPct) / 100, effective_from: new Date().toISOString().slice(0, 10) },
      { onSuccess: () => setWeightPct("") },
    );
  };

  const rows = [...(editions.data ?? [])].reverse();

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3 rounded-md border border-white/10 p-3">
        <p className="text-sm text-slate-300">
          Peso dentro de {assetClassLabel(strategy.asset_class)}:{" "}
          <span className="tabular-nums text-slate-100">
            {currentWeight != null ? formatRatio(currentWeight) : "não definido"}
          </span>
        </p>
        <form onSubmit={submitWeight} className="flex items-end gap-2">
          <label className="text-sm">
            <span className="mb-1 block text-xs text-slate-500">Novo peso (%)</span>
            <input
              type="number"
              step="0.01"
              min="0"
              max="100"
              required
              value={weightPct}
              onChange={(e) => setWeightPct(e.target.value)}
              className="w-24 rounded-md border border-white/10 bg-slate-900 px-3 py-2 text-slate-100 outline-none focus:border-accent-500"
            />
          </label>
          <button
            type="submit"
            disabled={weightMutation.isPending}
            className="rounded-md bg-accent-500 px-3 py-2 text-sm font-medium text-slate-950 transition-colors hover:bg-accent-600 disabled:opacity-50"
          >
            {weightMutation.isPending ? "Salvando…" : "Definir"}
          </button>
        </form>
      </div>

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
      {uploadMutation.isPending && <p className="text-sm text-slate-500">Processando…</p>}
      {uploadMutation.isError && <p className="text-sm text-tax">{String(uploadMutation.error)}</p>}

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
                <span className="text-xs text-slate-500">
                  {edition.targets.length} tickers
                </span>
              </div>

              {diff && (
                <div className="mb-3 flex flex-wrap gap-1.5 text-xs">
                  {diff.entered.map((t) => (
                    <span key={`in-${t.ticker}`} className="rounded bg-yield/15 px-1.5 py-0.5 text-yield">
                      + {t.ticker} {formatRatio(t.weight)}
                    </span>
                  ))}
                  {diff.exited.map((t) => (
                    <span key={`out-${t.ticker}`} className="rounded bg-tax/15 px-1.5 py-0.5 text-tax">
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
