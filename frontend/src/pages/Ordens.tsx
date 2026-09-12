import { useApplyTransfer, useOrderPlan, useStrategies } from "../api/queries.ts";
import type { Order, OrderKind, TransferSuggestion } from "../api/types.ts";
import { Panel } from "../components/Panel.tsx";
import { formatBRL } from "../lib/money.ts";

const KIND_LABELS: Record<OrderKind, string> = {
  BUY: "Comprar",
  SELL: "Vender",
  FULL_EXIT: "Zerar posição",
  NEW_ENTRY: "Entrada nova",
};

const KIND_COLOR: Record<OrderKind, string> = {
  BUY: "text-principal",
  NEW_ENTRY: "text-principal",
  SELL: "text-accent-500",
  FULL_EXIT: "text-tax",
};

export function Ordens() {
  const plan = useOrderPlan();

  if (plan.isLoading) return <p className="text-slate-400">Carregando…</p>;
  if (plan.error) return <p className="text-tax">Falha ao carregar: {String(plan.error)}</p>;
  if (!plan.data) return null;

  const { orders, transfer_suggestions: transfers, sale_ceiling: ceiling } = plan.data;
  const exceeded = ceiling.month_sold > ceiling.limit;

  return (
    <div className="space-y-6">
      <h1 className="text-xl font-semibold text-slate-100">Ordens</h1>

      <Panel title="Teto de isenção de ações (R$20.000/mês)">
        <div className="space-y-2">
          <div className="h-3 overflow-hidden rounded-full bg-slate-800">
            <div
              className={`h-full ${exceeded ? "bg-tax" : "bg-accent-500"}`}
              style={{ width: `${Math.min(100, (ceiling.month_sold / ceiling.limit) * 100)}%` }}
            />
          </div>
          <p className="text-sm text-slate-300">
            Vendido no mês:{" "}
            <span className="tabular-nums text-slate-100">{formatBRL(ceiling.month_sold)}</span>
            {" · "}
            {exceeded ? (
              <span className="text-tax">teto estourado — ganho vira tributável a 15%</span>
            ) : (
              <>
                falta{" "}
                <span className="tabular-nums text-slate-100">{formatBRL(ceiling.remaining)}</span>{" "}
                para estourar
              </>
            )}
          </p>
          <p className="text-xs text-slate-500">FIIs não têm isenção — não entram neste teto.</p>
        </div>
      </Panel>

      {transfers.length > 0 && (
        <Panel title={`Transferências sugeridas (${transfers.length})`}>
          <ul className="divide-y divide-white/5">
            {transfers.map((t, i) => (
              <TransferRow
                key={`${t.ticker}-${t.from_strategy_id}-${t.to_strategy_id}-${i}`}
                transfer={t}
              />
            ))}
          </ul>
        </Panel>
      )}

      <Panel title={`Lista de ordens (${orders.length})`} action={<ExportButton orders={orders} />}>
        {orders.length === 0 ? (
          <p className="text-sm text-slate-500">Carteira já está no alvo — nada para negociar.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm tabular-nums">
              <thead>
                <tr className="text-left text-xs uppercase tracking-wide text-slate-500">
                  <th className="py-2 pr-4 font-medium">Ticker</th>
                  <th className="py-2 pr-4 font-medium">Ação</th>
                  <th className="py-2 pr-4 text-right font-medium">Qtd.</th>
                  <th className="py-2 pr-4 text-right font-medium">Estimado</th>
                  <th className="py-2 font-medium">Estratégias</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-white/5">
                {orders.map((o) => (
                  <OrderRow key={o.listed_asset_id} order={o} />
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Panel>
    </div>
  );
}

function TransferRow({ transfer }: { transfer: TransferSuggestion }) {
  const mutation = useApplyTransfer();
  const strategies = useStrategies();
  const nameById = new Map((strategies.data ?? []).map((s) => [s.id, s.name]));
  const fromName = nameById.get(transfer.from_strategy_id) ?? transfer.from_strategy_id;
  const toName = nameById.get(transfer.to_strategy_id) ?? transfer.to_strategy_id;

  const apply = () => {
    mutation.mutate({
      listed_asset_id: transfer.listed_asset_id,
      from_strategy_id: transfer.from_strategy_id,
      to_strategy_id: transfer.to_strategy_id,
      quantity: transfer.quantity,
      date: new Date().toISOString().slice(0, 10),
    });
  };

  return (
    <li className="flex flex-wrap items-center gap-3 py-2.5 text-sm">
      <span className="font-medium text-slate-100">{transfer.ticker}</span>
      <span className="text-slate-400">
        {fromName} → {toName}
      </span>
      <span className="tabular-nums text-slate-300">{transfer.quantity} cotas</span>
      <button
        type="button"
        onClick={apply}
        disabled={mutation.isPending}
        className="ml-auto rounded-md bg-accent-500 px-3 py-1 text-xs font-medium text-slate-950 transition-colors hover:bg-accent-600 disabled:opacity-50"
      >
        {mutation.isPending ? "Aplicando…" : "Aplicar"}
      </button>
      {mutation.isError && <p className="w-full text-xs text-tax">{String(mutation.error)}</p>}
    </li>
  );
}

function OrderRow({ order }: { order: Order }) {
  return (
    <tr>
      <td className="py-2 pr-4 text-slate-200">
        {order.ticker}
        {order.is_fii && (
          <span className="ml-1.5 rounded bg-slate-800 px-1 py-0.5 text-[10px] text-slate-500">
            FII
          </span>
        )}
        {order.day_trade_risk && (
          <span className="ml-1.5 rounded bg-tax/15 px-1 py-0.5 text-[10px] text-tax">
            day trade
          </span>
        )}
      </td>
      <td className={`py-2 pr-4 font-medium ${KIND_COLOR[order.kind]}`}>
        {KIND_LABELS[order.kind]}
      </td>
      <td className="py-2 pr-4 text-right text-slate-200">{order.quantity}</td>
      <td className="py-2 pr-4 text-right text-slate-200">{formatBRL(order.notional)}</td>
      <td className="py-2 text-slate-400">
        {order.contributions.map((c) => (
          <span key={c.strategy_id} className="mr-2 whitespace-nowrap">
            {c.strategy_name} ({c.delta > 0 ? "+" : ""}
            {c.delta})
          </span>
        ))}
      </td>
    </tr>
  );
}

function ExportButton({ orders }: { orders: Order[] }) {
  const download = () => {
    const header = "ticker,acao,quantidade,estimado";
    const lines = orders.map(
      (o) => `${o.ticker},${KIND_LABELS[o.kind]},${o.quantity},${o.notional.toFixed(2)}`,
    );
    const csv = [header, ...lines].join("\n");
    const blob = new Blob([csv], { type: "text/csv;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = "ordens.csv";
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <button
      type="button"
      onClick={download}
      disabled={orders.length === 0}
      className="rounded-md bg-slate-800 px-3 py-1.5 text-xs font-medium text-slate-300 transition-colors hover:text-white disabled:opacity-50"
    >
      Exportar CSV
    </button>
  );
}
