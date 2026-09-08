import { useState, type FormEvent, type ReactNode } from "react";
import { useNavigate } from "react-router-dom";
import { useCreateCheckingAccount } from "../api/queries.ts";
import type { IndexId } from "../api/types.ts";
import { INDEX_IDS } from "../i18n/indexId.ts";
import { Panel } from "../components/Panel.tsx";

/** years + months -> ISO-8601 period ("P2Y", "P1Y6M", "P0D"). */
function toPeriod(years: number, months: number): string {
  if (years === 0 && months === 0) return "P0D";
  return `P${years ? `${years}Y` : ""}${months ? `${months}M` : ""}`;
}

export function NewCheckingAccount() {
  const navigate = useNavigate();
  const mutation = useCreateCheckingAccount();
  const [name, setName] = useState("");
  const [value, setValue] = useState("");
  const [indexId, setIndexId] = useState<IndexId>("CDI");
  const [years, setYears] = useState("2");
  const [months, setMonths] = useState("0");

  const submit = (e: FormEvent) => {
    e.preventDefault();
    mutation.mutate(
      {
        name,
        value: Number(value),
        index_id: indexId,
        maturity_duration: toPeriod(Number(years), Number(months)),
      },
      { onSuccess: (a) => navigate(`/checking-accounts/${a.id}`) },
    );
  };

  return (
    <div className="mx-auto max-w-lg space-y-6">
      <h1 className="text-xl font-semibold text-slate-100">Nova conta corrente</h1>
      <Panel title="Dados da conta">
        <form onSubmit={submit} className="space-y-4">
          <Field label="Nome">
            <input
              required
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="w-full rounded-md border border-white/10 bg-slate-900 px-3 py-2 text-slate-100 outline-none focus:border-accent-500"
            />
          </Field>
          <Field label="Percentual do índice (%)">
            <input
              type="number"
              step="0.01"
              required
              value={value}
              onChange={(e) => setValue(e.target.value)}
              className="w-full rounded-md border border-white/10 bg-slate-900 px-3 py-2 text-slate-100 outline-none focus:border-accent-500"
            />
          </Field>
          <Field label="Índice">
            <select
              value={indexId}
              onChange={(e) => setIndexId(e.target.value as IndexId)}
              className="w-full rounded-md border border-white/10 bg-slate-900 px-3 py-2 text-slate-100 outline-none focus:border-accent-500"
            >
              {INDEX_IDS.map((i) => (
                <option key={i} value={i}>
                  {i}
                </option>
              ))}
            </select>
          </Field>
          <div className="flex gap-3">
            <Field label="Carência (anos)">
              <input
                type="number"
                min="0"
                value={years}
                onChange={(e) => setYears(e.target.value)}
                className="w-24 rounded-md border border-white/10 bg-slate-900 px-3 py-2 text-slate-100 outline-none focus:border-accent-500"
              />
            </Field>
            <Field label="Carência (meses)">
              <input
                type="number"
                min="0"
                value={months}
                onChange={(e) => setMonths(e.target.value)}
                className="w-24 rounded-md border border-white/10 bg-slate-900 px-3 py-2 text-slate-100 outline-none focus:border-accent-500"
              />
            </Field>
          </div>
          <button
            type="submit"
            disabled={mutation.isPending}
            className="rounded-md bg-accent-500 px-4 py-2 text-sm font-medium text-slate-950 transition-colors hover:bg-accent-600 disabled:opacity-50"
          >
            {mutation.isPending ? "Criando…" : "Criar conta"}
          </button>
          {mutation.isError && (
            <p className="text-sm text-tax">{String(mutation.error)}</p>
          )}
        </form>
      </Panel>
    </div>
  );
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="block text-sm">
      <span className="mb-1 block text-xs text-slate-500">{label}</span>
      {children}
    </label>
  );
}
