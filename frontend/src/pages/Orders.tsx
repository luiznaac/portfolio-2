import { useState } from "react";
import { Link } from "react-router-dom";
import {
  useOrderPlan,
  useOrders,
  useOverrideOrderMatch,
  useRefreshOrders,
} from "../api/queries.ts";
import type { OrderList, SuggestedOrder } from "../api/types.ts";
import { Button } from "../components/Button.tsx";
import { Chip } from "../components/Chip.tsx";
import { Panel } from "../components/Panel.tsx";
import { type Column, Table } from "../components/Table.tsx";
import { formatCurrency, formatNumber, formatSignedValue } from "../lib/format.ts";
import { monthPeriodLabel } from "../lib/month.ts";
import {
  type OrderContext,
  fundingSourceLabel,
  fundingSourceTone,
  groupOrders,
  orderClipboardText,
  orderContextKey,
  orderContexts,
  orderProgressLabel,
  orderSideLabel,
  orderStateLabel,
  orderStateTone,
  orderTotals,
  ordersCsv,
  planCoversOrder,
  toTradeId,
} from "../lib/orders.ts";
import { ROUTES } from "../lib/routes.ts";

// The month's Orders screen: the persisted plan (never recomputed on read) and the copy/CSV exits.
// The Attribution Transfer inbox lives on its own surface (Plano / Mês · Transferências).
// "Recalcular" is the one action that recomputes and rewrites the plan.
export function Orders() {
  const orders = useOrders();

  return (
    <section>
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
            <Link to={ROUTES.plan} className="text-principal underline">
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
  // The persisted list carries no funding/attribution context, so a row's context joins the full
  // computed plan (its attribution moves) by ticker. The join only holds while the computed plan
  // still matches the persisted order — a stale plan renders no context rather than the wrong one.
  const plan = useOrderPlan("FULL").data;
  const contexts = orderContexts(plan);
  const contextFor = (order: SuggestedOrder) =>
    planCoversOrder(plan, order) ? contexts.get(orderContextKey(order)) : undefined;
  const groups = groupOrders(list.orders);
  const totals = orderTotals(list.orders);

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

      <div className="space-y-4">
        {groups.length > 0 && (
          <p className="text-[11.5px] text-ink-faint">
            PARCIAL é só exibição: a ordem segue Sugerida até ser executada por inteiro.
          </p>
        )}
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
              <Table
                columns={orderColumns(contextFor)}
                rows={group.orders}
                rowKey={(order) => order.id}
              />
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

function orderColumns(
  contextFor: (order: SuggestedOrder) => OrderContext | undefined,
): Column<SuggestedOrder>[] {
  return [
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
        <span data-testid={`order-state-${order.ticker}`}>
          <Chip tone={orderStateTone(order)}>{orderStateLabel(order)}</Chip>
        </span>
      ),
    },
    {
      key: "context",
      header: "Contexto",
      render: (order) => <ContextCell order={order} context={contextFor(order)} />,
    },
    {
      key: "executed",
      header: "Executado",
      align: "right",
      render: (order) => <MatchCell order={order} />,
    },
  ];
}

// The plan's context for one order: the strategies the units belong to (attribution) and, for a
// funding sell, why the sale exists. Both come from the computed plan; the persisted order knows
// neither.
function ContextCell({
  order,
  context,
}: {
  order: SuggestedOrder;
  context: OrderContext | undefined;
}) {
  if (context === undefined) {
    return (
      <span data-testid={`order-context-${order.ticker}`} className="text-ink-faint">
        —
      </span>
    );
  }

  return (
    <span
      data-testid={`order-context-${order.ticker}`}
      className="inline-flex flex-wrap items-center gap-1"
    >
      {context.contributions.map((contribution) => (
        <Chip key={contribution.strategy}>
          {`${contribution.strategy} ${formatSignedValue(contribution.quantity, formatNumber)}`}
        </Chip>
      ))}
      {context.fundingSource !== undefined && (
        <Chip tone={fundingSourceTone(context.fundingSource)}>
          {fundingSourceLabel(context.fundingSource)}
        </Chip>
      )}
    </span>
  );
}

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
        <span
          data-testid={`order-executed-${order.ticker}`}
          className="font-mono text-[11.5px] text-ink-dim"
        >
          {orderProgressLabel(order)}
        </span>
        <Button
          variant="ghost"
          data-testid={`order-match-edit-${order.ticker}`}
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
        data-testid={`order-match-apply-${order.ticker}`}
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
