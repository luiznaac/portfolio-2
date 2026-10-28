import { useState } from "react";
import { Link } from "react-router-dom";
import {
  useDecideTransfer,
  useOrders,
  useOverrideOrderMatch,
  useRefreshOrders,
  useStrategies,
} from "../api/queries.ts";
import type { OrderList, Strategy, SuggestedOrder, TransferProposal } from "../api/types.ts";
import { Button } from "../components/Button.tsx";
import { Chip, type ChipTone } from "../components/Chip.tsx";
import { Panel } from "../components/Panel.tsx";
import { type Column, Table } from "../components/Table.tsx";
import { formatCurrency, formatNumber } from "../lib/format.ts";
import { monthPeriodLabel } from "../lib/month.ts";
import {
  groupOrders,
  orderClipboardText,
  orderSideLabel,
  orderStatusLabel,
  orderTotals,
  ordersCsv,
  toTradeId,
  transferCopy,
  transferStatusLabel,
} from "../lib/orders.ts";

// The month's Orders screen: the persisted plan (never recomputed on read), the Attribution
// Transfer inbox with its approve/reject decisions, and the copy/CSV exits. "Recalcular" is the one
// action that recomputes and rewrites the plan.
export function Orders() {
  const orders = useOrders();

  return (
    <section>
      <h1 className="mb-4 font-cond text-[24px] font-bold tracking-[.005em]">Ordens</h1>

      {orders.isLoading ? (
        <p className="text-[13.5px] text-ink-dim">Carregando…</p>
      ) : orders.isError ? (
        <p className="text-[13.5px] text-tax">{orders.error.message}</p>
      ) : orders.data ? (
        <OrdersView list={orders.data} />
      ) : (
        <Panel title="Sem mês aberto">
          <p className="text-[13.5px] text-ink-dim">
            Abra um mês em{" "}
            <Link to="/month" className="text-principal underline">
              Capital e alvos
            </Link>{" "}
            para calcular o plano de ordens.
          </p>
        </Panel>
      )}
    </section>
  );
}

function OrdersView({ list }: { list: OrderList }) {
  const refresh = useRefreshOrders();
  // The catalog resolves a transfer row's source kind: a MANUAL Strategy means a legacy move.
  const strategies = useStrategies().data;
  const groups = groupOrders(list.orders);
  const totals = orderTotals(list.orders);
  const pending = list.transfers.filter((transfer) => transfer.status === "PENDING");
  const decided = list.transfers.filter((transfer) => transfer.status !== "PENDING");

  function copyOrders() {
    void navigator.clipboard.writeText(orderClipboardText(list.orders));
  }

  function exportCsv() {
    const blob = new Blob([ordersCsv(list.orders)], {
      type: "text/csv;charset=utf-8",
    });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `ordens-${list.period}.csv`;
    link.click();
    URL.revokeObjectURL(url);
  }

  return (
    <div data-testid="orders-view">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <span className="font-cond text-[15px] font-semibold">{monthPeriodLabel(list.period)}</span>
        <div className="flex flex-wrap items-center gap-2">
          <Button
            variant="primary"
            data-testid="orders-refresh"
            onClick={() => refresh.mutate()}
            disabled={refresh.isPending}
          >
            {refresh.isPending ? "Recalculando…" : "Recalcular"}
          </Button>
          <Button
            data-testid="orders-copy"
            onClick={copyOrders}
            disabled={list.orders.length === 0}
          >
            Copiar
          </Button>
          <Button data-testid="orders-csv" onClick={exportCsv} disabled={list.orders.length === 0}>
            CSV
          </Button>
        </div>
      </div>

      {refresh.error && <p className="mb-3 text-[12.5px] text-tax">{refresh.error.message}</p>}

      <div className="mb-4 grid grid-cols-2 gap-3 md:grid-cols-4">
        <Summary label="Compras" value={formatCurrency(totals.buys)} />
        <Summary label="Vendas" value={formatCurrency(totals.sells)} />
        <Summary label="Caixa necessário" value={formatCurrency(totals.cash)} />
        <Summary label="Ordens" value={formatNumber(list.orders.length)} />
      </div>

      {list.transfers.length > 0 && (
        <div className="mb-4">
          <Panel title="Transferências a decidir">
            {pending.length === 0 ? (
              <p className="text-[13px] text-ink-dim">Todas decididas.</p>
            ) : (
              <div className="divide-y divide-line-soft">
                {pending.map((transfer) => (
                  <TransferRow key={transfer.id} transfer={transfer} strategies={strategies} />
                ))}
              </div>
            )}
            {decided.length > 0 && (
              <div className="mt-3 border-t border-line-soft pt-2">
                <h3 className="mb-1 font-mono text-[10px] tracking-[.1em] text-ink-faint uppercase">
                  Decididas ({decided.length})
                </h3>
                <div className="divide-y divide-line-soft">
                  {decided.map((transfer) => (
                    <TransferRow key={transfer.id} transfer={transfer} strategies={strategies} />
                  ))}
                </div>
              </div>
            )}
          </Panel>
        </div>
      )}

      <div className="space-y-4">
        {groups.length === 0 ? (
          <Panel title="Lista de ordens">
            <p className="text-[13px] text-ink-dim">
              Nenhum plano persistido para o mês. Use Recalcular para gerar a lista.
            </p>
          </Panel>
        ) : (
          groups.map((group) => (
            <div key={group.label}>
              <h2 className="mb-2 font-cond text-[13px] font-bold tracking-[.04em] text-ink-dim uppercase">
                {group.label}
              </h2>
              <Table columns={ORDER_COLUMNS} rows={group.orders} rowKey={(order) => order.id} />
            </div>
          ))
        )}
      </div>

      {list.unplanned_trades.length > 0 && (
        <div className="mt-4">
          <Panel title="Negócios fora do plano">
            <Table
              columns={UNPLANNED_COLUMNS}
              rows={list.unplanned_trades}
              rowKey={(trade) => trade.id}
            />
          </Panel>
        </div>
      )}
    </div>
  );
}

function Summary({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border border-line bg-surface px-3 py-2.5">
      <div className="font-mono text-[10px] tracking-[.1em] text-ink-faint uppercase">{label}</div>
      <div className="mt-1 font-mono text-[16px] text-ink">{value}</div>
    </div>
  );
}

function TransferRow({
  transfer,
  strategies,
}: {
  transfer: TransferProposal;
  strategies: Strategy[] | undefined;
}) {
  const decide = useDecideTransfer();
  const isPending = transfer.status === "PENDING";

  return (
    <div className="flex flex-wrap items-center justify-between gap-3 py-2.5">
      <div>
        <div className="flex flex-wrap items-center gap-2">
          <span className="font-mono text-[12.5px] font-semibold text-ink">{transfer.ticker}</span>
          <Chip>{transfer.from_strategy}</Chip>
          <span className="text-ink-faint">→</span>
          <Chip tone="blue">{transfer.to_strategy}</Chip>
          <span className="text-[11.5px] text-ink-faint">{transferCopy(transfer, strategies)}</span>
        </div>
        <div className="mt-1 text-[12px] text-ink-dim">
          {formatNumber(transfer.quantity)} cotas
          {transfer.auto_approved && " · aprovada automaticamente"}
        </div>
      </div>
      <div className="flex items-center gap-2">
        {isPending ? (
          <>
            <Button
              variant="primary"
              data-testid={`transfer-approve-${transfer.id}`}
              onClick={() => decide.mutate({ id: transfer.id, decision: "approve" })}
              disabled={decide.isPending}
            >
              Aprovar
            </Button>
            <Button
              variant="ghost"
              data-testid={`transfer-reject-${transfer.id}`}
              onClick={() => decide.mutate({ id: transfer.id, decision: "reject" })}
              disabled={decide.isPending}
            >
              Rejeitar
            </Button>
          </>
        ) : (
          <Chip tone={transfer.status === "APPROVED" ? "green" : "rose"}>
            {transferStatusLabel(transfer.status)}
          </Chip>
        )}
      </div>
    </div>
  );
}

const STATUS_TONES: Record<SuggestedOrder["status"], ChipTone> = {
  SUGGESTED: "amber",
  EXECUTED: "blue",
  IMPORTED: "green",
};

const ORDER_COLUMNS: Column<SuggestedOrder>[] = [
  {
    key: "ticker",
    header: "Ticker",
    render: (order) => (
      <span className="font-mono text-[12.5px] font-semibold text-ink">{order.ticker}</span>
    ),
  },
  {
    key: "side",
    header: "Tipo",
    render: (order) => <Chip tone="neutral">{orderSideLabel(order.side)}</Chip>,
  },
  {
    key: "quantity",
    header: "Qtd.",
    align: "right",
    render: (order) => <span className="font-mono">{formatNumber(order.quantity)}</span>,
  },
  {
    key: "price",
    header: "Preço ref.",
    align: "right",
    render: (order) =>
      order.suggested_price === undefined ? (
        <span className="text-ink-faint">—</span>
      ) : (
        <span className="font-mono">{formatCurrency(order.suggested_price)}</span>
      ),
  },
  {
    key: "amount",
    header: "Valor",
    align: "right",
    render: (order) => (
      <span className="font-mono">
        {order.suggested_price === undefined
          ? "—"
          : formatCurrency(order.quantity * order.suggested_price)}
      </span>
    ),
  },
  {
    key: "status",
    header: "Ciclo",
    render: (order) => (
      <Chip tone={STATUS_TONES[order.status]}>{orderStatusLabel(order.status)}</Chip>
    ),
  },
  {
    key: "executed",
    header: "Executado",
    align: "right",
    render: (order) => <MatchCell order={order} />,
  },
];

// The lifecycle match is overridable: the executed figures carry a control that retargets the order
// at a specific trade (or clears the override). The server validates the trade's ticker and side.
function MatchCell({ order }: { order: SuggestedOrder }) {
  const [editing, setEditing] = useState(false);
  const [tradeId, setTradeId] = useState("");
  const override = useOverrideOrderMatch();
  const parsed = toTradeId(tradeId);

  if (!editing) {
    return (
      <span className="inline-flex items-center justify-end gap-2">
        <span className="font-mono text-[11.5px] text-ink-dim">
          {formatNumber(order.executed_quantity)}/{formatNumber(order.quantity)}
        </span>
        <Button
          variant="ghost"
          className="px-1.5 py-0.5 text-[11px]"
          onClick={() => setEditing(true)}
        >
          Ajustar
        </Button>
      </span>
    );
  }

  return (
    <span className="inline-flex items-center justify-end gap-1">
      <input
        type="number"
        min={1}
        value={tradeId}
        aria-label={`Negócio para ${order.ticker}`}
        onChange={(event) => setTradeId(event.target.value)}
        className="w-16 rounded-sm border border-line bg-surface-2 px-1.5 py-0.5 text-right font-mono text-[11.5px] text-ink"
      />
      <Button
        variant="primary"
        className="px-2 py-0.5 text-[11px]"
        disabled={parsed === null || override.isPending}
        onClick={() =>
          override.mutate(
            { orderId: order.id, tradeId: parsed },
            { onSuccess: () => setEditing(false) },
          )
        }
      >
        Aplicar
      </Button>
      <Button
        variant="ghost"
        className="px-2 py-0.5 text-[11px]"
        disabled={override.isPending}
        onClick={() =>
          override.mutate(
            { orderId: order.id, tradeId: null },
            { onSuccess: () => setEditing(false) },
          )
        }
      >
        Limpar
      </Button>
      <Button
        variant="ghost"
        className="px-1.5 py-0.5 text-[11px]"
        onClick={() => setEditing(false)}
      >
        ×
      </Button>
    </span>
  );
}

const UNPLANNED_COLUMNS: Column<OrderList["unplanned_trades"][number]>[] = [
  {
    key: "ticker",
    header: "Ticker",
    render: (trade) => <span className="font-mono text-[12.5px] text-ink">{trade.ticker}</span>,
  },
  {
    key: "side",
    header: "Lado",
    render: (trade) => <Chip tone="amber">{orderSideLabel(trade.side)}</Chip>,
  },
  {
    key: "quantity",
    header: "Qtd.",
    align: "right",
    render: (trade) => <span className="font-mono">{formatNumber(trade.quantity)}</span>,
  },
];
