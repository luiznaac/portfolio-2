import { useBenchmarkStatus, useRefreshBenchmarks } from "../api/queries.ts";
import { formatLocalDateTime } from "../lib/format.ts";
import { BENCHMARK_INDEXES } from "../lib/performance.ts";
import { Button } from "./Button.tsx";

// The stored benchmark series' freshness, in the same "Dados da carteira" panel as the catalog
// refresh. The action re-checks every index over its stored span; there is no scheduler, so an old
// series stays visible instead of silently going stale.
export function BenchmarkRefresh() {
  const status = useBenchmarkStatus();
  const refresh = useRefreshBenchmarks();
  const latest = refresh.data ?? status.data;
  const rows = BENCHMARK_INDEXES.map(
    (index) => latest?.indexes.find((row) => row.index === index) ?? { index },
  );

  return (
    <div data-testid="benchmark-refresh">
      <div className="flex items-center justify-between border-b border-line-soft pb-1.5">
        <span className="font-mono text-[11px] tracking-[.06em] text-ink-faint uppercase">
          Benchmarks
        </span>
        <Button
          variant="ghost"
          onClick={() => refresh.mutate()}
          disabled={refresh.isPending}
          data-testid="benchmark-refresh-button"
          className="px-0 text-[10px] text-principal hover:bg-transparent hover:underline"
        >
          {refresh.isPending ? "Atualizando…" : "Atualizar benchmarks"}
        </Button>
      </div>

      <ul className="divide-y divide-line-soft">
        {rows.map((row) => (
          <li
            key={row.index}
            data-testid={`benchmark-status-${row.index}`}
            className="flex items-center justify-between gap-3 py-1.5 text-[12.5px]"
          >
            <span className="font-mono">{row.index}</span>
            <span className="text-ink-dim">{lastFetchLabel(row.last_fetched_at)}</span>
          </li>
        ))}
      </ul>

      {status.isError && <p className="pt-1 text-[12px] text-tax">{status.error.message}</p>}
      {refresh.error && <p className="pt-1 text-[12px] text-tax">{refresh.error.message}</p>}
    </div>
  );
}

function lastFetchLabel(lastFetchedAt: string | undefined): string {
  return lastFetchedAt === undefined
    ? "sem busca"
    : `última busca · ${formatLocalDateTime(lastFetchedAt)}`;
}
