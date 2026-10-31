import type { TickerRefreshFailure, TickerRefreshResult } from "../api/types.ts";
import { formatIsoDate } from "../lib/format.ts";
import { Button } from "./Button.tsx";

// The shape both the panel and the import result need from `useRefreshCatalog`; keeping it
// structural lets the presentational controls stay free of the query-hook type.
interface RefreshControl {
  mutate: () => void;
  isPending: boolean;
  data?: TickerRefreshResult;
  error: Error | null;
}

// One action owns both halves: the COTAHIST file feeds the catalog and the stored closes. The same
// control appears in the data panel's header, beside an unpriced row and on the import result.
export function CatalogRefreshButton({
  refresh,
  label = "Atualizar catálogo e cotações",
  variant = "primary",
  testId = "catalog-refresh",
}: {
  refresh: RefreshControl;
  label?: string;
  variant?: "default" | "primary" | "ghost" | "danger";
  testId?: string;
}) {
  return (
    <Button
      variant={variant}
      onClick={() => refresh.mutate()}
      disabled={refresh.isPending}
      data-testid={testId}
    >
      {refresh.isPending ? "Atualizando…" : label}
    </Button>
  );
}

// The refresh's honest report: what it wrote, the close date it used and every part that failed.
export function CatalogRefreshReport({ refresh }: { refresh: RefreshControl }) {
  if (refresh.error) {
    return <p className="text-[12.5px] text-tax">{refresh.error.message}</p>;
  }
  if (!refresh.data) return null;

  const { catalog, quotes, as_of: asOf, failed } = refresh.data;

  return (
    <div data-testid="catalog-refresh-report" className="space-y-1.5 text-[12.5px]">
      <p className="text-ink-dim">
        Catálogo: {catalog} · Cotações: {quotes} · Fechamento:{" "}
        {asOf === undefined ? "—" : formatIsoDate(asOf)}
      </p>
      {failed.length > 0 && (
        <ul data-testid="catalog-refresh-failures" className="space-y-0.5 text-tax">
          {failed.map((failure) => (
            <li key={failureKey(failure)}>{failureLabel(failure)}</li>
          ))}
        </ul>
      )}
    </div>
  );
}

function failureKey(failure: TickerRefreshFailure): string {
  return failure.kind === "SOURCE" ? `source:${failure.source}` : `ticker:${failure.ticker}`;
}

function failureLabel(failure: TickerRefreshFailure): string {
  const name = failure.kind === "SOURCE" ? failure.source : failure.ticker;
  return `${name}: ${failure.reason}`;
}
