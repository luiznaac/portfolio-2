import { useMemo } from "react";
import { useHydrateIndex, useIndexValues, useIndexes } from "../api/queries.ts";
import type { IndexId, IndexValue } from "../api/types.ts";
import { Panel } from "../components/Panel.tsx";
import { indexColorVar, indexDescription } from "../i18n/indexId.ts";
import { formatDate } from "../lib/format.ts";

export function Indexes() {
  const indexes = useIndexes();

  if (indexes.isLoading) return <p className="text-slate-400">Carregando…</p>;
  if (indexes.error) return <p className="text-tax">Falha ao carregar: {String(indexes.error)}</p>;

  return (
    <div className="space-y-6">
      <h1 className="text-xl font-semibold text-slate-100">Índices</h1>
      <div className="grid gap-6 md:grid-cols-3">
        {(indexes.data ?? []).map((idx) => (
          <IndexCard key={idx.id} id={idx.id} />
        ))}
      </div>
    </div>
  );
}

function IndexCard({ id }: { id: IndexId }) {
  const values = useIndexValues(id);
  const hydrate = useHydrateIndex();
  const list = values.data ?? [];
  const latest = list[list.length - 1];

  return (
    <Panel
      title={id}
      action={
        <button
          type="button"
          onClick={() => hydrate.mutate(id)}
          disabled={hydrate.isPending}
          className="rounded-md bg-slate-800 px-2.5 py-1 text-xs font-medium text-slate-300 transition-colors hover:text-white disabled:opacity-50"
        >
          {hydrate.isPending ? "…" : "Hidratar"}
        </button>
      }
    >
      <p className="text-xs text-slate-500">{indexDescription(id)}</p>
      <Sparkline values={list} color={indexColorVar(id)} />
      <dl className="mt-2 space-y-1 text-sm">
        <div className="flex justify-between">
          <dt className="text-slate-500">Registros</dt>
          <dd className="tabular-nums text-slate-300">{list.length}</dd>
        </div>
        {latest && (
          <div className="flex justify-between">
            <dt className="text-slate-500">Último ({formatDate(latest.date)})</dt>
            <dd className="tabular-nums text-slate-300">{latest.value}</dd>
          </div>
        )}
      </dl>
      {hydrate.isSuccess && (
        <p className="mt-2 text-xs text-yield">+{hydrate.data.count} valores importados.</p>
      )}
    </Panel>
  );
}

function Sparkline({ values, color }: { values: IndexValue[]; color: string }) {
  const points = useMemo(() => {
    const tail = values.slice(-120);
    if (tail.length < 2) return "";
    const ys = tail.map((v) => v.value);
    const min = Math.min(...ys);
    const max = Math.max(...ys);
    const span = max - min || 1;
    return tail
      .map((v, i) => `${(i / (tail.length - 1)) * 100},${100 - ((v.value - min) / span) * 100}`)
      .join(" ");
  }, [values]);

  if (!points) return <p className="mt-2 text-xs text-slate-600">Sem série ainda.</p>;

  return (
    <svg
      viewBox="0 0 100 100"
      preserveAspectRatio="none"
      className="mt-2 h-16 w-full"
      role="img"
      aria-label="Série histórica do índice"
    >
      <polyline
        points={points}
        fill="none"
        stroke={color}
        strokeWidth="1.5"
        vectorEffect="non-scaling-stroke"
      />
    </svg>
  );
}
