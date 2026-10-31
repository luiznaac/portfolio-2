import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { useRefreshCatalog } from "../api/queries.ts";
import type { Allocation, HeldTicker } from "../api/types.ts";
import { dataQualityCounts } from "../lib/dataQuality.ts";
import { formatNumber } from "../lib/format.ts";
import { CatalogRefreshButton, CatalogRefreshReport } from "./CatalogRefresh.tsx";
import { Panel } from "./Panel.tsx";

// The single place to see and fix what makes the book wrong: unpriced positions, held tickers with
// no class, and negative custody. Its header carries the one combined catalog-and-quotes refresh;
// the unpriced rows repeat that action, the unclassified rows open the classify dialog and the
// negative rows deep-link to the Histórico screen that adopts them.
export function DataQualityPanel({
  allocation,
  onClassify,
}: {
  allocation: Allocation;
  onClassify: (ticker: string) => void;
}) {
  const refresh = useRefreshCatalog();
  const counts = dataQualityCounts(allocation);
  const unpriced = allocation.unpriced ?? [];
  const unclassified = allocation.unclassified.tickers.map((ticker) => ({
    ticker: ticker.ticker,
    quantity: ticker.quantity ?? 0,
  }));
  const negative = allocation.negative ?? [];

  return (
    <Panel
      title="Dados da carteira"
      action={
        <CatalogRefreshButton
          refresh={refresh}
          testId="data-quality-refresh"
          label="Atualizar catálogo e cotações"
        />
      }
    >
      <div data-testid="data-quality-panel" className="space-y-4">
        <CatalogRefreshReport refresh={refresh} />

        <QualitySection
          testId="unpriced"
          label="Sem cotação"
          count={counts.unpriced}
          rows={unpriced}
          renderAction={(row) => (
            <CatalogRefreshButton
              refresh={refresh}
              testId={`refresh-${row.ticker}`}
              label="Atualizar"
              variant="ghost"
            />
          )}
        />

        <QualitySection
          testId="unclassified"
          label="Tickers sem classe"
          count={counts.unclassified}
          rows={unclassified}
          renderAction={(row) => (
            <button
              type="button"
              onClick={() => onClassify(row.ticker)}
              className="font-mono text-[10px] text-principal hover:underline"
            >
              Classificar
            </button>
          )}
        />

        <QualitySection
          testId="negative"
          label="Posições negativas"
          count={counts.negative}
          rows={negative}
          renderAction={() => (
            <Link to="/history" className="font-mono text-[10px] text-principal hover:underline">
              Histórico
            </Link>
          )}
        />

        {counts.total === 0 && (
          <p className="text-[12.5px] text-yield">Nenhum dado pendente na carteira.</p>
        )}
      </div>
    </Panel>
  );
}

function QualitySection({
  testId,
  label,
  count,
  rows,
  renderAction,
}: {
  testId: string;
  label: string;
  count: number;
  rows: HeldTicker[];
  renderAction: (row: HeldTicker) => ReactNode;
}) {
  return (
    <div data-testid={`data-quality-${testId}`}>
      <div className="flex items-center justify-between border-b border-line-soft pb-1.5">
        <span className="font-mono text-[11px] tracking-[.06em] text-ink-faint uppercase">
          {label}
        </span>
        <span data-testid={`data-quality-${testId}-count`} className="font-mono text-[12.5px]">
          {count}
        </span>
      </div>
      {rows.length > 0 && (
        <ul className="divide-y divide-line-soft">
          {rows.map((row) => (
            <li
              key={row.ticker}
              data-testid={`data-quality-row-${row.ticker}`}
              className="flex items-center justify-between gap-3 py-1.5 text-[12.5px]"
            >
              <span className="font-mono">{row.ticker}</span>
              <span className="text-ink-dim">{formatNumber(row.quantity)} un</span>
              {renderAction(row)}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
