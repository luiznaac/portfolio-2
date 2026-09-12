import {
  type FormEvent,
  type ReactElement,
  type ReactNode,
  cloneElement,
  isValidElement,
  useId,
  useState,
} from "react";
import { useBonds, useCheckingAccounts, useUploadXlsx } from "../api/queries.ts";
import type { UploadBroker, UploadProduct } from "../api/types.ts";
import { Panel } from "../components/Panel.tsx";

const BROKERS: UploadBroker[] = ["kinvo", "picpay"];

export function Upload() {
  const bonds = useBonds();
  const accounts = useCheckingAccounts();
  const mutation = useUploadXlsx();

  const [broker, setBroker] = useState<UploadBroker>("kinvo");
  const [product, setProduct] = useState<UploadProduct>("bond");
  const [productId, setProductId] = useState("");
  const [file, setFile] = useState<File | null>(null);

  const options =
    product === "bond"
      ? (bonds.data ?? []).map((b) => ({ id: b.id, name: b.name }))
      : (accounts.data ?? []).map((a) => ({ id: a.id, name: a.name }));

  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (!file || !productId) return;
    mutation.mutate({
      broker,
      product,
      productId: Number(productId),
      file,
    });
  };

  return (
    <div className="mx-auto max-w-lg space-y-6">
      <h1 className="text-xl font-semibold text-slate-100">Importar extrato</h1>
      <Panel title="Planilha da corretora (.xlsx)">
        <form onSubmit={submit} className="space-y-4">
          <Field label="Corretora">
            <select
              value={broker}
              onChange={(e) => setBroker(e.target.value as UploadBroker)}
              className="w-full rounded-md border border-white/10 bg-slate-900 px-3 py-2 text-slate-100 outline-none focus:border-accent-500"
            >
              {BROKERS.map((b) => (
                <option key={b} value={b}>
                  {b}
                </option>
              ))}
            </select>
          </Field>

          <Field label="Destino">
            <select
              value={product}
              onChange={(e) => {
                setProduct(e.target.value as UploadProduct);
                setProductId("");
              }}
              className="w-full rounded-md border border-white/10 bg-slate-900 px-3 py-2 text-slate-100 outline-none focus:border-accent-500"
            >
              <option value="bond">Título</option>
              <option value="checking-account">Conta corrente</option>
            </select>
          </Field>

          <Field label={product === "bond" ? "Título" : "Conta"}>
            <select
              required
              value={productId}
              onChange={(e) => setProductId(e.target.value)}
              className="w-full rounded-md border border-white/10 bg-slate-900 px-3 py-2 text-slate-100 outline-none focus:border-accent-500"
            >
              <option value="">Selecione…</option>
              {options.map((o) => (
                <option key={o.id} value={o.id}>
                  {o.name}
                </option>
              ))}
            </select>
          </Field>

          <Field label="Arquivo">
            <input
              type="file"
              accept=".xlsx"
              required
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
              className="w-full text-sm text-slate-400 file:mr-3 file:rounded-md file:border-0 file:bg-slate-800 file:px-3 file:py-2 file:text-slate-200"
            />
          </Field>

          <button
            type="submit"
            disabled={mutation.isPending || !file || !productId}
            className="rounded-md bg-accent-500 px-4 py-2 text-sm font-medium text-slate-950 transition-colors hover:bg-accent-600 disabled:opacity-50"
          >
            {mutation.isPending ? "Enviando…" : "Importar"}
          </button>

          {mutation.isError && <p className="text-sm text-tax">{String(mutation.error)}</p>}
          {mutation.isSuccess && (
            <p className="text-sm text-yield">
              {mutation.data.length} lançamento(s) processado(s).
            </p>
          )}
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
