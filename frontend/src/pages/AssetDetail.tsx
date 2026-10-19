import { Link, useParams } from "react-router-dom";
import { ApiError } from "../api/client.ts";
import {
  useAttribution,
  usePosition,
  useRemoveAttribution,
  useSetAttribution,
  useStrategies,
  useTickerQuotes,
} from "../api/queries.ts";
import type { Trade } from "../api/types.ts";
import { AttributionPanel } from "../components/AttributionPanel.tsx";
import { Badge } from "../components/Badge.tsx";
import { Panel } from "../components/Panel.tsx";
import { type Column, Table } from "../components/Table.tsx";
import { formatCurrency, formatNumber, formatTradeSide } from "../lib/format.ts";
import { latestQuote, positionValue } from "../lib/position.ts";

const KPI_LABEL = "font-mono text-[10px] tracking-[.09em] text-ink-faint uppercase";
const BACK_LINK =
  "inline-flex items-center gap-1.5 rounded-md border border-line bg-surface-2 px-3 py-1.5 text-[12.5px] text-ink hover:border-surface-3 hover:bg-surface-3";

const COLUMNS: Column<Trade>[] = [
  { key: "date", header: "Data", render: (trade) => trade.date },
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

function Kpi({ label, value, hint }: { label: string; value?: string; hint?: string }) {
  return (
    <div className="rounded-lg border border-line bg-surface px-4 py-3.5">
      <div className={KPI_LABEL}>{label}</div>
      <div className="mt-1 font-cond text-[22px] font-bold">{value ?? "—"}</div>
      {hint !== undefined && <div className="mt-0.5 text-[11.5px] text-ink-faint">{hint}</div>}
    </div>
  );
}

// The Ativos detail: the stored position (quantity + fiscal average + market value), the
// attribution ledger, and the trade history.
export function AssetDetail() {
  const { ticker = "" } = useParams();
  const position = usePosition(ticker);
  const quotes = useTickerQuotes(ticker);
  const attribution = useAttribution(ticker);
  const strategies = useStrategies();
  const setSlice = useSetAttribution(ticker);
  const removeSlice = useRemoveAttribution(ticker);

  if (position.isLoading) return <p className="text-[13.5px] text-ink-dim">Carregando…</p>;

  if (position.error || !position.data) {
    // A 404 means the ticker has no position; any other failure is a real error, not a missing
    // asset, and must not be reported as one.
    const notFound =
      !position.error || (position.error instanceof ApiError && position.error.status === 404);
    return (
      <section>
        <h1 className="mb-4 font-mono text-[24px] font-bold">{ticker}</h1>
        <Panel>
          <p className="text-[13.5px] text-ink-dim">
            {notFound ? "Ativo não encontrado." : "Não foi possível carregar o ativo."}
          </p>
        </Panel>
      </section>
    );
  }

  const { position: current, trades } = position.data;
  const close = latestQuote(quotes.data ?? [])?.close;
  const value = close === undefined ? undefined : positionValue(current.quantity, close);

  return (
    <section>
      <div className="mb-4 flex items-start justify-between gap-3">
        <div>
          <h1 className="font-mono text-[24px] font-bold tracking-normal">{current.ticker}</h1>
          <p className="text-[13px] text-ink-dim">
            <Link to="/trades" className="text-principal hover:underline">
              todos os ativos
            </Link>
          </p>
        </div>
        <Link to="/trades" className={BACK_LINK}>
          ← Voltar
        </Link>
      </div>

      <div className="mb-4 grid grid-cols-3 gap-3">
        <Kpi label="Custódia" value={formatNumber(current.quantity)} />
        <Kpi label="Preço médio (fiscal)" value={formatCurrency(current.average_price)} />
        <Kpi
          label="Valor de mercado"
          value={value === undefined ? undefined : formatCurrency(value)}
          hint={close === undefined ? "sem cotação" : `cotação ${formatCurrency(close)}`}
        />
      </div>

      <div className="mb-4" data-testid="attribution-panel">
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
      </div>

      <Panel title="Negócios">
        {trades.length === 0 ? (
          <p className="text-[13.5px] text-ink-dim">Nenhum negócio registrado.</p>
        ) : (
          <Table columns={COLUMNS} rows={trades} rowKey={(trade) => trade.id} />
        )}
      </Panel>
    </section>
  );
}
