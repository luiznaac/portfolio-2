import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { useCreateListedAsset, useTickerCatalogSearch } from "../api/queries.ts";
import type { TickerCatalogEntry } from "../api/types.ts";
import { assetKindLabel } from "../i18n/assetKind.ts";
import { Panel } from "../components/Panel.tsx";
import { useDebouncedValue } from "../lib/useDebouncedValue.ts";

// Trading name that B3's own dividend endpoint expects (see B3DividendGateway.kt) isn't part of
// brapi's ticker catalog — only the FII case is mechanically derivable (ticker minus the "11"
// suffix). For stocks/ETFs/BDRs this stays a manual field the user may need to correct.
function guessB3Identifier(entry: TickerCatalogEntry): string {
  return entry.kind === "FII" ? entry.ticker.replace(/11$/, "") : "";
}

export function NewListedAsset() {
  const navigate = useNavigate();
  const mutation = useCreateListedAsset();
  const [query, setQuery] = useState("");
  const [selected, setSelected] = useState<TickerCatalogEntry | null>(null);
  const [b3Identifier, setB3Identifier] = useState("");

  const debouncedQuery = useDebouncedValue(query);
  const results = useTickerCatalogSearch(debouncedQuery);

  const pick = (entry: TickerCatalogEntry) => {
    setSelected(entry);
    setQuery(entry.ticker);
    setB3Identifier(guessB3Identifier(entry));
  };

  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (!selected) return;
    mutation.mutate(
      {
        ticker: selected.ticker,
        kind: selected.kind,
        name: selected.name,
        b3_identifier: b3Identifier,
      },
      { onSuccess: (asset) => navigate(`/listed-assets/${asset.id}`) },
    );
  };

  const showResults = selected === null && debouncedQuery.trim().length >= 2;

  return (
    <div className="mx-auto max-w-lg space-y-6">
      <h1 className="text-xl font-semibold text-slate-100">Novo ativo</h1>
      <Panel title="Buscar na B3">
        <form onSubmit={submit} className="space-y-4">
          <div className="relative">
            <label className="block text-sm">
              <span className="mb-1 block text-xs text-slate-500">
                Ticker ou nome da empresa
              </span>
              <input
                autoComplete="off"
                placeholder="ex.: PETR4, MXRF11, VALE3…"
                value={query}
                onChange={(e) => {
                  setQuery(e.target.value);
                  setSelected(null);
                }}
                className="w-full rounded-md border border-white/10 bg-slate-900 px-3 py-2 text-slate-100 outline-none focus:border-accent-500"
              />
            </label>

            {showResults && (
              <ul className="absolute z-10 mt-1 max-h-64 w-full overflow-y-auto rounded-md border border-white/10 bg-slate-900 shadow-lg">
                {results.isLoading && (
                  <li className="px-3 py-2 text-sm text-slate-500">Buscando…</li>
                )}
                {results.data?.length === 0 && (
                  <li className="px-3 py-2 text-sm text-slate-500">Nenhum resultado.</li>
                )}
                {results.data?.map((entry) => (
                  <li key={entry.ticker}>
                    <button
                      type="button"
                      onClick={() => pick(entry)}
                      className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm hover:bg-slate-800"
                    >
                      <span className="font-medium text-slate-100">{entry.ticker}</span>
                      <span className="truncate text-slate-400">{entry.name}</span>
                      <span className="ml-auto shrink-0 rounded bg-slate-800 px-1.5 py-0.5 text-[10px] uppercase text-slate-400">
                        {assetKindLabel(entry.kind)}
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>

          {selected && (
            <>
              <div className="rounded-md border border-white/10 bg-slate-950/40 px-3 py-2 text-sm">
                <div className="font-medium text-slate-100">
                  {selected.ticker} · {assetKindLabel(selected.kind)}
                </div>
                <div className="text-slate-400">{selected.name}</div>
              </div>

              <label className="block text-sm">
                <span className="mb-1 block text-xs text-slate-500">
                  Identificador B3 (usado para buscar proventos)
                </span>
                <input
                  required
                  value={b3Identifier}
                  onChange={(e) => setB3Identifier(e.target.value)}
                  className="w-full rounded-md border border-white/10 bg-slate-900 px-3 py-2 text-slate-100 outline-none focus:border-accent-500"
                />
                <span className="mt-1 block text-xs text-slate-500">
                  {selected.kind === "FII"
                    ? "Preenchido automaticamente: ticker sem o sufixo 11."
                    : "Nome de pregão na B3 — confira se a busca de proventos não retornar nada."}
                </span>
              </label>

              <button
                type="submit"
                disabled={mutation.isPending}
                className="rounded-md bg-accent-500 px-4 py-2 text-sm font-medium text-slate-950 transition-colors hover:bg-accent-600 disabled:opacity-50"
              >
                {mutation.isPending ? "Criando…" : "Cadastrar ativo"}
              </button>
              {mutation.isError && (
                <p className="text-sm text-tax">{String(mutation.error)}</p>
              )}
            </>
          )}
        </form>
      </Panel>
    </div>
  );
}
