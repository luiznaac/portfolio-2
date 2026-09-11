import {
  type FormEvent,
  type ReactElement,
  type ReactNode,
  cloneElement,
  isValidElement,
  useId,
  useState,
} from "react";
import { useNavigate } from "react-router-dom";
import { useCreateBond } from "../api/queries.ts";
import type { IndexId } from "../api/types.ts";
import { Panel } from "../components/Panel.tsx";
import { INDEX_IDS } from "../i18n/indexId.ts";

export function NewBond() {
  const navigate = useNavigate();
  const mutation = useCreateBond();
  const [kind, setKind] = useState<"fixed" | "floating">("fixed");
  const [name, setName] = useState("");
  const [value, setValue] = useState("");
  const [maturity, setMaturity] = useState("");
  const [indexId, setIndexId] = useState<IndexId>("CDI");

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const base = { name, value: Number(value), maturity_date: maturity };
    const body =
      kind === "fixed"
        ? ({ kind, ...base } as const)
        : ({ kind, ...base, index_id: indexId } as const);
    mutation.mutate(body, { onSuccess: (bond) => navigate(`/bonds/${bond.id}`) });
  };

  return (
    <div className="mx-auto max-w-lg space-y-6">
      <h1 className="text-xl font-semibold text-slate-100">Novo título</h1>
      <Panel title="Dados do título">
        <form onSubmit={submit} className="space-y-4">
          <div className="flex gap-2">
            {(["fixed", "floating"] as const).map((k) => (
              <button
                key={k}
                type="button"
                onClick={() => setKind(k)}
                className={`rounded-md px-3 py-1.5 text-sm font-medium transition-colors ${
                  kind === k
                    ? "bg-accent-500 text-slate-950"
                    : "bg-slate-800 text-slate-400 hover:text-slate-200"
                }`}
              >
                {k === "fixed" ? "Prefixado" : "Pós-fixado"}
              </button>
            ))}
          </div>

          <Field label="Nome">
            <input
              required
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="w-full rounded-md border border-white/10 bg-slate-900 px-3 py-2 text-slate-100 outline-none focus:border-accent-500"
            />
          </Field>

          <Field label={kind === "fixed" ? "Taxa anual (%)" : "Percentual do índice (%)"}>
            <input
              type="number"
              step="0.01"
              required
              value={value}
              onChange={(e) => setValue(e.target.value)}
              className="w-full rounded-md border border-white/10 bg-slate-900 px-3 py-2 text-slate-100 outline-none focus:border-accent-500"
            />
          </Field>

          {kind === "floating" && (
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
          )}

          <Field label="Vencimento">
            <input
              type="date"
              required
              value={maturity}
              onChange={(e) => setMaturity(e.target.value)}
              className="w-full rounded-md border border-white/10 bg-slate-900 px-3 py-2 text-slate-100 outline-none focus:border-accent-500"
            />
          </Field>

          <button
            type="submit"
            disabled={mutation.isPending}
            className="rounded-md bg-accent-500 px-4 py-2 text-sm font-medium text-slate-950 transition-colors hover:bg-accent-600 disabled:opacity-50"
          >
            {mutation.isPending ? "Criando…" : "Criar título"}
          </button>
          {mutation.isError && <p className="text-sm text-tax">{String(mutation.error)}</p>}
        </form>
      </Panel>
    </div>
  );
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  const fieldId = useId();
  return (
    <div className="block text-sm">
      <label htmlFor={fieldId} className="mb-1 block text-xs text-slate-500">
        {label}
      </label>
      {isValidElement(children)
        ? cloneElement(children as ReactElement<{ id?: string }>, { id: fieldId })
        : children}
    </div>
  );
}
