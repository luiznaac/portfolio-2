import {
  usePortfolioStatus,
  usePositions,
  useRefreshCatalog,
  useTickerQuotesList,
} from "../api/queries.ts";
import { CatalogRefreshButton, CatalogRefreshReport } from "../components/CatalogRefresh.tsx";
import { Chip } from "../components/Chip.tsx";
import { Panel } from "../components/Panel.tsx";
import { type Column, Table } from "../components/Table.tsx";
import { formatCurrency, formatIsoDate } from "../lib/format.ts";
import { type QuoteRow, quoteRows } from "../lib/quotes.ts";

const COLUMNS: Column<QuoteRow>[] = [
  {
    key: "ticker",
    header: "Ticker",
    render: (row) => <span className="font-mono font-medium">{row.ticker}</span>,
  },
  {
    key: "date",
    header: "Data",
    render: (row) =>
      row.date === undefined ? (
        <span className="text-faint">—</span>
      ) : (
        <span className="font-mono text-muted">{formatIsoDate(row.date)}</span>
      ),
  },
  {
    key: "close",
    header: "Fechamento",
    align: "right",
    render: (row) =>
      row.close === undefined ? (
        <span className="text-faint">—</span>
      ) : (
        <span className="font-mono">{formatCurrency(row.close)}</span>
      ),
  },
  {
    key: "source",
    header: "Fonte",
    render: (row) =>
      row.source === undefined ? <span className="text-faint">—</span> : row.source,
  },
  { key: "state", header: "Estado", render: (row) => <QuoteState row={row} /> },
];

// Freshness is the status aggregate's call (the backend's five-day rule), so the page only names
// the state the shell already measured. A failed read is its own state, never "sem cotação".
function QuoteState({ row }: { row: QuoteRow }) {
  if (row.failed) return <Chip tone="rose">erro na leitura</Chip>;
  if (row.close === undefined) return <Chip>sem cotação</Chip>;
  if (row.staleDays === undefined) return <Chip tone="green">ok</Chip>;
  const unit = row.staleDays === 1 ? "dia" : "dias";
  return <Chip tone="amber">{`desatualizada · há ${row.staleDays} ${unit}`}</Chip>;
}

// Ativos · Cotações (#381): the last close per held ticker with its date, source and freshness, plus
// the one combined catalogue-and-quotes refresh. The held tickers are the bounded list — the
// catalogue itself is not listed here.
export function Quotes() {
  const positions = usePositions();
  const status = usePortfolioStatus();
  const refresh = useRefreshCatalog();
  const tickers = (positions.data ?? []).map((position) => position.ticker).sort();
  const quotes = useTickerQuotesList(tickers);
  const quotesLoading = quotes.some((query) => query.isLoading);

  if (positions.isError) {
    return <p className="text-[13px] text-return-down">{positions.error.message}</p>;
  }

  if (status.isError) {
    return <p className="text-[13px] text-return-down">{status.error.message}</p>;
  }

  if (positions.data === undefined || status.data === undefined || quotesLoading) {
    return <p className="text-[13px] text-muted">Carregando…</p>;
  }

  const failedTickers = new Set(tickers.filter((_, index) => quotes[index]?.isError === true));
  const quotesByTicker = new Map(
    tickers.map((ticker, index) => [ticker, quotes[index]?.data ?? []]),
  );
  const rows = quoteRows(tickers, quotesByTicker, status.data.data.stale_quotes, failedTickers);
  const asOf = status.data.data.quotes_as_of;

  return (
    <section>
      <Panel
        title="Cotações"
        action={<CatalogRefreshButton refresh={refresh} testId="quotes-refresh" />}
      >
        <CatalogRefreshReport refresh={refresh} />

        {rows.length === 0 ? (
          <p className="text-[13px] text-muted">Nenhuma posição na carteira.</p>
        ) : (
          <div data-testid="quotes-table" className="mt-3">
            <Table columns={COLUMNS} rows={rows} rowKey={(row) => row.ticker} />
          </div>
        )}

        <p className="mt-2.5 font-mono text-label text-faint">
          {asOf === undefined ? "Sem cotações registradas" : `Cotações de ${formatIsoDate(asOf)}`} ·
          A cotação velha é uma pendência de acompanhamento, não uma trava de fechamento: o snapshot
          congela o último preço conhecido.
        </p>
      </Panel>
    </section>
  );
}
