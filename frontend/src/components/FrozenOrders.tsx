import type { OrdersSnapshot } from "../api/types.ts";
import { formatCurrency, formatNumber } from "../lib/format.ts";
import { type FrozenOrderRow, frozenOrderRows } from "../lib/portfolioHistory.ts";
import { Chip } from "./Chip.tsx";
import { type Column, Table } from "./Table.tsx";

const COLUMNS: Column<FrozenOrderRow>[] = [
  {
    key: "ticker",
    header: "Ativo",
    render: (row) => <span className="font-mono font-semibold text-text">{row.ticker}</span>,
  },
  { key: "side", header: "Lado", render: (row) => <Chip tone="neutral">{row.sideLabel}</Chip> },
  {
    key: "quantity",
    header: "Qtd.",
    align: "right",
    render: (row) => <span className="font-mono">{formatNumber(row.quantity)}</span>,
  },
  {
    key: "price",
    header: "Preço",
    align: "right",
    render: (row) =>
      row.price === undefined ? (
        <span className="text-faint">—</span>
      ) : (
        <span className="font-mono">{formatCurrency(row.price)}</span>
      ),
  },
  {
    key: "state",
    header: "Estado",
    render: (row) => <Chip tone={row.stateTone}>{row.stateLabel}</Chip>,
  },
];

// The plan a snapshot froze, rendered with the same PARCIAL display rule as the live Orders screen.
// The close screen, the closed state on Mês atual and the frozen month detail read this one table.
export function FrozenOrders({
  orders,
  testId,
  title,
}: {
  orders: OrdersSnapshot;
  testId?: string;
  title?: string;
}) {
  const rows = frozenOrderRows(orders);

  return (
    <div data-testid={testId}>
      {title !== undefined && (
        <p className="mb-1.5 font-mono text-[10.5px] tracking-[.08em] text-ink-faint uppercase">
          {title}
        </p>
      )}
      {rows.length === 0 ? (
        <p className="text-[12px] text-ink-dim">Nenhuma ordem no snapshot.</p>
      ) : (
        <Table columns={COLUMNS} rows={rows} rowKey={(row) => row.key} />
      )}
    </div>
  );
}
