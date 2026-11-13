import { useState } from "react";
import { useGaps, usePriceGap } from "../api/queries.ts";
import { Button } from "../components/Button.tsx";
import { Chip } from "../components/Chip.tsx";
import { Panel } from "../components/Panel.tsx";
import { type Column, Table } from "../components/Table.tsx";
import { formatCurrency, formatIsoDate, formatNumber } from "../lib/format.ts";
import { type GapRow, gapRows } from "../lib/gaps.ts";

// The realized-result state the row carries. A replaced gap is resolved by the trade that filled it;
// an open missing buy has only an approximate market price; an open missing sale's proceeds stay
// pending until the owner informs them.
function GapOutcome({ row }: { row: GapRow }) {
  if (row.status === "REPLACED" || (!row.missingBuy && !row.pending)) {
    return <Chip tone="green">resolvido</Chip>;
  }
  if (row.missingBuy) {
    return (
      <span data-testid={`gap-approximate-${row.ticker}`}>
        <Chip tone="amber">aproximado</Chip>
      </span>
    );
  }
  return (
    <span data-testid={`gap-pending-${row.id}`}>
      <Chip tone="amber">pendente</Chip>
    </span>
  );
}

function GapStatus({ status }: { status: GapRow["status"] }) {
  return status === "OPEN" ? <Chip tone="rose">aberta</Chip> : <Chip>substituída</Chip>;
}

// An open missing sale is priced here: the draft is the user's typed proceeds per unit and the read
// is the fallback, so an external price change is not masked by a stale copy. Pricing flips
// `realized_pending` and the invalidated read re-renders the row as resolved without a page reload.
function GapPrice({ row }: { row: GapRow }) {
  const priceGap = usePriceGap();
  const [draft, setDraft] = useState<string | null>(null);
  const value = draft ?? (row.unitPrice === undefined ? "" : String(row.unitPrice));

  if (row.missingBuy || row.status === "REPLACED") {
    return row.unitPrice === undefined ? (
      <span className="text-faint">—</span>
    ) : (
      <span className="font-mono">{formatCurrency(row.unitPrice)}</span>
    );
  }

  return (
    <span className="flex items-center justify-end gap-2">
      <input
        aria-label={`Proventos de ${row.ticker}`}
        data-testid={`gap-price-${row.id}`}
        type="number"
        min="0"
        step="0.01"
        value={value}
        onChange={(event) => setDraft(event.target.value)}
        className="w-24 rounded-sm border border-line bg-panel-2 px-2 py-1 text-right font-mono text-num"
      />
      <Button
        data-testid={`gap-price-save-${row.id}`}
        variant="primary"
        onClick={() =>
          priceGap.mutate(
            { id: row.id, unitPrice: Number(value) },
            { onSuccess: () => setDraft(null) },
          )
        }
        disabled={value.trim().length === 0 || !(Number(value) >= 0) || priceGap.isPending}
      >
        Salvar
      </Button>
    </span>
  );
}

const COLUMNS: Column<GapRow>[] = [
  {
    key: "ticker",
    header: "Ticker",
    render: (row) => <span className="font-mono font-medium">{row.ticker}</span>,
  },
  {
    key: "date",
    header: "Data",
    render: (row) => <span className="font-mono text-muted">{formatIsoDate(row.date)}</span>,
  },
  {
    key: "quantity",
    header: "Quantidade",
    align: "right",
    render: (row) => <span className="font-mono">{formatNumber(row.quantity)}</span>,
  },
  { key: "outcome", header: "Resultado", render: (row) => <GapOutcome row={row} /> },
  { key: "status", header: "Estado", render: (row) => <GapStatus status={row.status} /> },
  { key: "price", header: "Preço", align: "right", render: (row) => <GapPrice row={row} /> },
];

// Ativos · Lacunas (#381): every custody delta the ledger cannot explain, open first, with the
// realized-result pending state, the gap's status and its pricing. Adoption seeds, the backfill
// replay and the custody cross-check live on Ativos · Negócios ("Reconstruir histórico").
export function Gaps() {
  const gaps = useGaps();

  if (gaps.isLoading) return <p className="text-[13px] text-muted">Carregando…</p>;

  if (gaps.isError) {
    return <p className="text-[13px] text-return-down">{gaps.error.message}</p>;
  }

  const rows = gapRows(gaps.data ?? []);

  return (
    <section>
      <Panel title="Lacunas">
        <p className="mb-3 text-[12.5px] text-muted">
          Uma lacuna é um delta de custódia que o livro não explica: uma compra faltante entra pelo
          preço de mercado (aproximado) e uma venda faltante espera os proventos que você informar.
        </p>

        {rows.length === 0 ? (
          <p className="text-[13px] text-return-up">Nenhuma lacuna registrada.</p>
        ) : (
          <div data-testid="gaps-table">
            <Table columns={COLUMNS} rows={rows} rowKey={(row) => row.id} />
          </div>
        )}
      </Panel>
    </section>
  );
}
