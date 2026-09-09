import { useQueries } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { api } from "../api/client.ts";
import {
  keys,
  useBonds,
  useCheckingAccounts,
  useListedAssets,
  useScheduleConsolidations,
} from "../api/queries.ts";
import type { Position } from "../api/types.ts";
import { assetKindLabel } from "../i18n/assetKind.ts";
import { Panel } from "../components/Panel.tsx";
import { PortfolioSummary } from "../components/PortfolioSummary.tsx";
import { PositionChart } from "../components/PositionChart.tsx";
import { formatBRL } from "../lib/money.ts";
import { formatDate } from "../lib/format.ts";
import { lastPosition, mergePositions, sumTotals } from "../lib/positions.ts";

export function Dashboard() {
  const bonds = useBonds();
  const accounts = useCheckingAccounts();
  const listedAssets = useListedAssets();
  const schedule = useScheduleConsolidations();

  // Client-side aggregation: the backend has no whole-portfolio endpoint, so we
  // fan out one positions request per product (à la shougong's listAll*).
  const bondPositions = useQueries({
    queries: (bonds.data ?? []).map((b) => ({
      queryKey: keys.bondPositions(b.id),
      queryFn: () => api.bondPositions(b.id),
    })),
  });
  const accountPositions = useQueries({
    queries: (accounts.data ?? []).map((a) => ({
      queryKey: keys.checkingAccountPositions(a.id),
      queryFn: () => api.checkingAccountPositions(a.id),
    })),
  });
  const listedAssetPositions = useQueries({
    queries: (listedAssets.data ?? []).map((a) => ({
      queryKey: keys.listedAssetPositions(a.id),
      queryFn: () => api.listedAssetPositions(a.id),
    })),
  });

  if (bonds.isLoading || accounts.isLoading || listedAssets.isLoading)
    return <p className="text-slate-400">Carregando…</p>;
  if (bonds.error)
    return <p className="text-tax">Falha ao carregar: {String(bonds.error)}</p>;

  const allSeries: Position[][] = [
    ...bondPositions.map((q) => q.data ?? []),
    ...accountPositions.map((q) => q.data ?? []),
    ...listedAssetPositions.map((q) => q.data ?? []),
  ];
  const merged = mergePositions(allSeries);
  const totals = sumTotals(allSeries.map(lastPosition));

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold text-slate-100">Painel</h1>
        <button
          onClick={() => schedule.mutate()}
          disabled={schedule.isPending}
          className="rounded-md bg-accent-500 px-3 py-1.5 text-sm font-medium text-slate-950 transition-colors hover:bg-accent-600 disabled:opacity-50"
        >
          {schedule.isPending ? "Agendando…" : "Consolidar tudo"}
        </button>
      </div>
      {schedule.isError && (
        <p className="text-sm text-tax">{String(schedule.error)}</p>
      )}
      {schedule.isSuccess && (
        <p className="text-sm text-yield">Consolidações agendadas no chameidor.</p>
      )}

      <PortfolioSummary totals={totals} />

      <Panel title="Valor líquido ao longo do tempo">
        <PositionChart positions={merged} />
      </Panel>

      <div className="grid gap-6 lg:grid-cols-3">
        <Panel title={`Títulos (${bonds.data?.length ?? 0})`}>
          <ProductList
            empty="Nenhum título. Cadastre um em “Novo título”."
            items={(bonds.data ?? []).map((b, i) => ({
              to: `/bonds/${b.id}`,
              name: b.name,
              tag: b.index_id ?? "pré",
              position: lastPosition(bondPositions[i]?.data ?? []),
            }))}
          />
        </Panel>
        <Panel title={`Contas correntes (${accounts.data?.length ?? 0})`}>
          <ProductList
            empty="Nenhuma conta. Cadastre uma em “Nova conta”."
            items={(accounts.data ?? []).map((a, i) => ({
              to: `/checking-accounts/${a.id}`,
              name: a.name,
              tag: a.index_id,
              position: lastPosition(accountPositions[i]?.data ?? []),
            }))}
          />
        </Panel>
        <Panel title={`Ativos listados (${listedAssets.data?.length ?? 0})`}>
          <ProductList
            empty="Nenhum ativo. Cadastre um em “Novo ativo”."
            items={(listedAssets.data ?? []).map((a, i) => ({
              to: `/listed-assets/${a.id}`,
              name: `${a.ticker} · ${a.name}`,
              tag: assetKindLabel(a.kind),
              position: lastPosition(listedAssetPositions[i]?.data ?? []),
            }))}
          />
        </Panel>
      </div>
    </div>
  );
}

interface Row {
  to: string;
  name: string;
  tag: string;
  position?: Position;
}

function ProductList({ items, empty }: { items: Row[]; empty: string }) {
  if (items.length === 0)
    return <p className="text-sm text-slate-500">{empty}</p>;

  return (
    <ul className="divide-y divide-white/5">
      {items.map((it) => {
        const net = it.position
          ? it.position.principal + it.position.yield - it.position.taxes
          : null;
        return (
          <li key={it.to}>
            <Link
              to={it.to}
              className="flex items-center gap-3 py-2.5 transition-colors hover:text-white"
            >
              <span className="rounded bg-slate-800 px-1.5 py-0.5 text-[10px] font-medium uppercase text-slate-400">
                {it.tag}
              </span>
              <span className="min-w-0 flex-1 truncate text-sm text-slate-300">
                {it.name}
              </span>
              <span className="shrink-0 text-right text-sm tabular-nums text-slate-200">
                {net == null ? "—" : formatBRL(net)}
                {it.position && (
                  <span className="block text-[10px] text-slate-500">
                    {formatDate(it.position.date)}
                  </span>
                )}
              </span>
            </Link>
          </li>
        );
      })}
    </ul>
  );
}
