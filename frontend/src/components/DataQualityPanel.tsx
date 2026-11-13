import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { useGaps, useRefreshCatalog } from "../api/queries.ts";
import type { Allocation, HeldTicker } from "../api/types.ts";
import { dataQualityCounts } from "../lib/dataQuality.ts";
import { formatNumber } from "../lib/format.ts";
import { pendingProceedsGaps } from "../lib/gaps.ts";
import { ROUTES, positionDetail } from "../lib/routes.ts";
import { BenchmarkRefresh } from "./BenchmarkRefresh.tsx";
import { CatalogRefreshButton, CatalogRefreshReport } from "./CatalogRefresh.tsx";
import { Panel } from "./Panel.tsx";

// The single place to see and fix what makes the book wrong: unpriced positions, held tickers with
// no class, negative custody and open GAPs. Its header carries the one combined catalog-and-quotes
// refresh; the unpriced rows repeat that action, the unclassified rows and the bulk action link to
// the Ativos · Posições classification surface, the negative rows deep-link to Ativos · Negócios
// ("Reconstruir histórico", which adopts them), and the lacuna rows to Ativos · Lacunas.
export function DataQualityPanel({ allocation }: { allocation: Allocation }) {
  const refresh = useRefreshCatalog();
  const gaps = useGaps();
  const counts = dataQualityCounts(allocation, gaps.data ?? []);
  const pendingProceeds = pendingProceedsGaps(gaps.data ?? []).length;
  const unpriced = allocation.unpriced ?? [];
  const unclassified = allocation.unclassified.tickers.map((ticker) => ({
    ticker: ticker.ticker,
    quantity: ticker.quantity ?? 0,
  }));
  const negative = allocation.negative ?? [];

  return (
    <>
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
            headerAction={
              counts.unclassified > 0 ? (
                <Link
                  to={ROUTES.positions}
                  data-testid="bulk-classify-open"
                  className="font-mono text-[10px] text-principal hover:underline"
                >
                  Classificar pendentes
                </Link>
              ) : undefined
            }
            renderAction={(row) => (
              <Link
                to={positionDetail(row.ticker)}
                className="font-mono text-[10px] text-principal hover:underline"
              >
                Classificar
              </Link>
            )}
          />

          <QualitySection
            testId="negative"
            label="Posições negativas"
            count={counts.negative}
            rows={negative}
            renderAction={() => (
              <Link
                to={ROUTES.trades}
                data-testid="negative-rebuild-link"
                className="font-mono text-[10px] text-principal hover:underline"
              >
                Reconstruir histórico
              </Link>
            )}
          />

          <div data-testid="data-quality-lacunas">
            <div className="flex items-center justify-between border-b border-line-soft pb-1.5">
              <span className="font-mono text-[11px] tracking-[.06em] text-ink-faint uppercase">
                Lacunas
              </span>
              <span className="flex items-center gap-3">
                {counts.lacunas > 0 && (
                  <Link
                    to={ROUTES.gaps}
                    className="font-mono text-[10px] text-principal hover:underline"
                  >
                    Revisar
                  </Link>
                )}
                <span data-testid="data-quality-lacunas-count" className="font-mono text-[12.5px]">
                  {counts.lacunas}
                </span>
              </span>
            </div>
            {pendingProceeds > 0 && (
              <p className="py-1.5 text-[12.5px] text-ink-dim">
                {pendingProceeds} com proventos pendentes
              </p>
            )}
          </div>

          <BenchmarkRefresh />

          {counts.total === 0 && (
            <p className="text-[12.5px] text-yield">Nenhum dado pendente na carteira.</p>
          )}
        </div>
      </Panel>
    </>
  );
}

function QualitySection({
  testId,
  label,
  count,
  rows,
  headerAction,
  renderAction,
}: {
  testId: string;
  label: string;
  count: number;
  rows: HeldTicker[];
  headerAction?: ReactNode;
  renderAction: (row: HeldTicker) => ReactNode;
}) {
  return (
    <div data-testid={`data-quality-${testId}`}>
      <div className="flex items-center justify-between border-b border-line-soft pb-1.5">
        <span className="font-mono text-[11px] tracking-[.06em] text-ink-faint uppercase">
          {label}
        </span>
        <span className="flex items-center gap-3">
          {headerAction}
          <span data-testid={`data-quality-${testId}-count`} className="font-mono text-[12.5px]">
            {count}
          </span>
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
