import { type ReactNode, useState } from "react";
import { Link } from "react-router-dom";
import { ApiError } from "../api/client.ts";
import {
  useAttribution,
  useGaps,
  usePosition,
  useRemoveAttribution,
  useRevertTickerClassification,
  useSetAttribution,
  useStrategies,
  useTicker,
  useTickerQuotes,
} from "../api/queries.ts";
import type { AssetClass, Gap, Position, Quote, Trade } from "../api/types.ts";
import { classificationNote, effectiveAssetClass } from "../lib/catalog.ts";
import { formatCurrency, formatIsoDate, formatNumber, formatTradeSide } from "../lib/format.ts";
import { openGaps } from "../lib/history.ts";
import { latestQuote, positionValue } from "../lib/position.ts";
import { ROUTES } from "../lib/routes.ts";
import { AttributionPanel } from "./AttributionPanel.tsx";
import { Badge } from "./Badge.tsx";
import { Button } from "./Button.tsx";
import { ClassBadge } from "./ClassBadge.tsx";
import { ClassifyTickerModal } from "./ClassifyTickerModal.tsx";
import { Kpi } from "./Kpi.tsx";
import { Panel } from "./Panel.tsx";
import { type Column, Table } from "./Table.tsx";

const TRADE_COLUMNS: Column<Trade>[] = [
  { key: "date", header: "Data", render: (trade) => formatIsoDate(trade.date) },
  {
    key: "side",
    header: "Lado",
    render: (trade) => (
      <Badge tone={trade.side === "BUY" ? "ok" : "warn"}>{formatTradeSide(trade.side)}</Badge>
    ),
  },
  {
    key: "quantity",
    header: "Qtd",
    align: "right",
    render: (trade) => formatNumber(trade.quantity),
  },
  {
    key: "unit_price",
    header: "Preço",
    align: "right",
    render: (trade) => formatCurrency(trade.unit_price),
  },
  {
    key: "value",
    header: "Valor",
    align: "right",
    render: (trade) => formatCurrency(trade.quantity * trade.unit_price),
  },
];

const QUOTE_COLUMNS: Column<Quote>[] = [
  { key: "date", header: "Data", render: (quote) => formatIsoDate(quote.date) },
  {
    key: "close",
    header: "Fechamento",
    align: "right",
    render: (quote) => formatCurrency(quote.close),
  },
];

const GAP_COLUMNS: Column<Gap>[] = [
  { key: "date", header: "Data", render: (gap) => formatIsoDate(gap.date) },
  {
    key: "quantity",
    header: "Qtd",
    align: "right",
    render: (gap) => formatNumber(gap.quantity),
  },
  {
    key: "kind",
    header: "Situação",
    render: (gap) =>
      gap.realized_pending === true ? (
        <Badge tone="warn">proventos pendentes</Badge>
      ) : (
        <Badge tone="info">compra ausente</Badge>
      ),
  },
];

const RECENT = 5;

// The ticker drawer (#378): the row's position detail, average price, attribution ledger, recent
// trades, quotes, open gaps and classification. It replaces the retired ticker-detail object and is
// reachable as a deep link (`/ativos/positions/:ticker`) from the table, Alocação and attention.
export function TickerDrawer({ ticker, onClose }: { ticker: string; onClose: () => void }) {
  const position = usePosition(ticker);
  const quotes = useTickerQuotes(ticker);
  const catalog = useTicker(ticker);
  const attribution = useAttribution(ticker);
  const strategies = useStrategies();
  const gaps = useGaps();
  const setSlice = useSetAttribution(ticker);
  const removeSlice = useRemoveAttribution(ticker);
  const revertClassification = useRevertTickerClassification(ticker);
  const [classifying, setClassifying] = useState(false);

  const catalogMissing = catalog.error instanceof ApiError && catalog.error.status === 404;
  const effectiveClass = effectiveAssetClass(catalog.data);
  const note = catalog.data === undefined ? null : classificationNote(catalog.data);
  // A failed catalog read is not an unclassified ticker: only a successful read (or the 404 that
  // says the row is missing) can justify offering the action.
  const canClassify =
    !catalog.isLoading && effectiveClass === null && (catalog.isSuccess || catalogMissing);

  return (
    <dialog
      open
      aria-modal="true"
      aria-label={`Detalhe de ${ticker}`}
      className="fixed inset-0 z-50 m-0 flex h-full w-full max-w-none items-stretch justify-end border-0 bg-transparent p-0"
    >
      <button
        type="button"
        aria-label="Fechar pelo fundo"
        onClick={onClose}
        className="absolute inset-0 bg-ground/70"
      />
      <aside
        data-testid="ticker-drawer"
        className="relative flex h-full w-[560px] max-w-full flex-col overflow-hidden border-l border-line bg-panel"
      >
        <div className="flex items-center justify-between border-b border-line px-4 py-3">
          <h3 className="font-mono text-[16px] font-bold">{ticker}</h3>
          <Button variant="ghost" onClick={onClose}>
            Fechar
          </Button>
        </div>

        <div className="flex-1 space-y-4 overflow-y-auto p-4">
          {position.isLoading ? (
            <p className="text-[13.5px] text-ink-dim">Carregando…</p>
          ) : position.error || !position.data ? (
            <Panel>
              <p className="text-[13.5px] text-ink-dim">
                {position.error instanceof ApiError && position.error.status === 404
                  ? "Ativo não encontrado."
                  : "Não foi possível carregar o ativo."}
              </p>
            </Panel>
          ) : (
            <DrawerBody
              current={position.data.position}
              trades={position.data.trades}
              quotes={quotes.data ?? []}
              gaps={gaps.data ?? []}
              attributionPanel={
                <AttributionPanel
                  attribution={attribution.data}
                  loading={attribution.isLoading}
                  loadError={attribution.isError ? attribution.error : null}
                  strategies={strategies.data ?? []}
                  strategiesLoading={strategies.isLoading}
                  pending={setSlice.isPending || removeSlice.isPending}
                  mutationError={setSlice.error ?? removeSlice.error}
                  onSave={(creation) => setSlice.mutateAsync(creation)}
                  onRemove={(strategyId) => removeSlice.mutateAsync(strategyId)}
                />
              }
              effectiveClass={effectiveClass}
              note={note}
              canClassify={canClassify}
              catalogLoading={catalog.isLoading}
              catalogError={catalog.isError && !catalogMissing ? catalog.error : null}
              onClassify={() => setClassifying(true)}
              revert={revertClassification}
            />
          )}
        </div>
      </aside>

      {classifying && <ClassifyTickerModal ticker={ticker} onClose={() => setClassifying(false)} />}
    </dialog>
  );
}

type RevertMutation = ReturnType<typeof useRevertTickerClassification>;

function DrawerBody({
  current,
  trades,
  quotes,
  gaps,
  attributionPanel,
  effectiveClass,
  note,
  canClassify,
  catalogLoading,
  catalogError,
  onClassify,
  revert,
}: {
  current: Position;
  trades: Trade[];
  quotes: Quote[];
  gaps: Gap[];
  attributionPanel: ReactNode;
  effectiveClass: AssetClass | null;
  note: string | null;
  canClassify: boolean;
  catalogLoading: boolean;
  catalogError: Error | null;
  onClassify: () => void;
  revert: RevertMutation;
}) {
  const close = latestQuote(quotes)?.close;
  const value = close === undefined ? undefined : positionValue(current.quantity, close);
  const recentTrades = [...trades].sort((left, right) => right.date.localeCompare(left.date));
  const recentQuotes = [...quotes]
    .sort((left, right) => right.date.localeCompare(left.date))
    .slice(0, RECENT);
  const tickerGaps = openGaps(gaps).filter((gap) => gap.ticker === current.ticker);

  return (
    <>
      <div className="grid grid-cols-3 gap-3">
        <Kpi size="lg" label="Custódia" value={formatNumber(current.quantity)} />
        <Kpi size="lg" label="Preço médio (fiscal)" value={formatCurrency(current.average_price)} />
        <Kpi
          size="lg"
          label="Valor de mercado"
          value={value === undefined ? undefined : formatCurrency(value)}
          hint={close === undefined ? "sem cotação" : `cotação ${formatCurrency(close)}`}
        />
      </div>

      {attributionPanel}

      <Panel title="Negócios recentes">
        {recentTrades.length === 0 ? (
          <p className="text-[13.5px] text-ink-dim">Nenhum negócio registrado.</p>
        ) : (
          <Table
            columns={TRADE_COLUMNS}
            rows={recentTrades.slice(0, RECENT)}
            rowKey={(trade) => trade.id}
          />
        )}
      </Panel>

      <Panel title="Cotações">
        {recentQuotes.length === 0 ? (
          <p className="text-[13.5px] text-ink-dim">Nenhuma cotação registrada.</p>
        ) : (
          <Table columns={QUOTE_COLUMNS} rows={recentQuotes} rowKey={(quote) => quote.date} />
        )}
      </Panel>

      <Panel title="Lacunas">
        {tickerGaps.length === 0 ? (
          <p className="text-[13.5px] text-ink-dim">Nenhuma lacuna aberta.</p>
        ) : (
          <>
            <Table columns={GAP_COLUMNS} rows={tickerGaps} rowKey={(gap) => gap.id} />
            <Link
              to={ROUTES.gaps}
              className="mt-2 inline-block font-mono text-[11px] text-principal hover:underline"
            >
              Ver lacunas
            </Link>
          </>
        )}
      </Panel>

      <Panel title="Classificação">
        {catalogLoading ? (
          <p className="text-[13.5px] text-ink-dim">Carregando…</p>
        ) : catalogError ? (
          <p className="text-[13.5px] text-tax">Não foi possível carregar a classificação.</p>
        ) : (
          <>
            <div className="flex flex-wrap items-center gap-2">
              <span data-testid="drawer-class">
                <ClassBadge assetClass={effectiveClass} />
              </span>
              {canClassify && <Button onClick={onClassify}>Classificar</Button>}
            </div>
            {note !== null && (
              <p className="mt-2 flex items-center gap-2 text-[12.5px] text-accent">
                {note}
                <Button variant="ghost" onClick={() => revert.mutate()} disabled={revert.isPending}>
                  Reverter
                </Button>
              </p>
            )}
            {revert.error && <p className="mt-1 text-[12.5px] text-tax">{revert.error.message}</p>}
          </>
        )}
      </Panel>
    </>
  );
}
