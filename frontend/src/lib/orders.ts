// Pure Orders-screen helpers — no React imports here (see AGENTS.md). The page renders labels,
// side groups, totals, the broker clipboard text and the CSV; all four are unit-tested here.

import type {
  FundingSource,
  OrderPlan,
  OrderSide,
  Strategy,
  SuggestedOrder,
  SuggestedOrderStatus,
  TransferProposal,
  TransferStatus,
} from "../api/types.ts";
import { formatCurrency, formatNumber } from "./format.ts";

const SIDE_LABELS: Record<OrderSide, string> = {
  BUY: "Compra",
  SELL: "Venda",
  NEW_ENTRY: "Nova entrada",
  EXIT: "Saída total",
};

const STATUS_LABELS: Record<SuggestedOrderStatus, string> = {
  SUGGESTED: "Sugerido",
  EXECUTED: "Executado",
  IMPORTED: "Importado",
};

const TRANSFER_STATUS_LABELS: Record<TransferStatus, string> = {
  PENDING: "Pendente",
  APPROVED: "Aprovada",
  REJECTED: "Rejeitada",
};

export function orderSideLabel(side: OrderSide): string {
  return SIDE_LABELS[side];
}

export function orderStatusLabel(status: SuggestedOrderStatus): string {
  return STATUS_LABELS[status];
}

export function transferStatusLabel(status: TransferStatus): string {
  return TRANSFER_STATUS_LABELS[status];
}

// The inbox row's source kind comes from the strategies catalog: a Manual Strategy means the move
// draws on the legacy bucket. Undefined while the catalog has not resolved; an unknown source is not
// the bucket — the copy must not claim a legacy move it cannot see.
export function transferSourceIsBucket(
  transfer: TransferProposal,
  strategies: Strategy[] | undefined,
): boolean | undefined {
  if (strategies === undefined) return undefined;
  return (
    strategies.find((strategy) => strategy.id === transfer.from_strategy_id)?.kind === "MANUAL"
  );
}

// The caption beside a transfer row: "evita vender" only makes sense when a report slice's surplus
// funds the move; a bucket source moves legacy units, so no sale is at stake. Absent while the
// catalog is still loading — claiming the report copy before the source kind is known would be wrong.
export function transferCopy(
  transfer: TransferProposal,
  strategies: Strategy[] | undefined,
): string | undefined {
  if (transfer.amount === undefined) return "sem cotação";
  const fromBucket = transferSourceIsBucket(transfer, strategies);
  if (fromBucket === undefined) return undefined;
  return fromBucket
    ? `move ${formatCurrency(transfer.amount)} do legado`
    : `evita vender ${formatCurrency(transfer.amount)}`;
}

export interface OrderGroup {
  label: string;
  orders: SuggestedOrder[];
}

// The reference table's out-to-in sections; a side the plan did not emit is omitted rather than
// rendered as an empty group.
const GROUP_ORDER: { side: OrderSide; label: string }[] = [
  { side: "EXIT", label: "Saídas totais" },
  { side: "SELL", label: "Vendas" },
  { side: "BUY", label: "Compras" },
  { side: "NEW_ENTRY", label: "Novas entradas" },
];

export function groupOrders(orders: SuggestedOrder[]): OrderGroup[] {
  return GROUP_ORDER.map(({ side, label }) => ({
    label,
    orders: orders.filter((order) => order.side === side),
  })).filter((group) => group.orders.length > 0);
}

const BUY_SIDES: OrderSide[] = ["BUY", "NEW_ENTRY"];
const SELL_SIDES: OrderSide[] = ["SELL", "EXIT"];

// The buy/sell partition the backend's OrderSide.isBuy owns; one definition here so the totals and
// any future grouping cannot drift from each other.
export function isBuySide(side: OrderSide): boolean {
  return BUY_SIDES.includes(side);
}

export function isSellSide(side: OrderSide): boolean {
  return SELL_SIDES.includes(side);
}

// A match override's trade id as typed: a positive integer or nothing. Null keeps the apply button
// disabled rather than sending a NaN.
export function toTradeId(value: string): number | null {
  const trimmed = value.trim();
  if (!/^\d+$/.test(trimmed)) return null;
  const parsed = Number(trimmed);
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null;
}

export interface OrderTotals {
  buys: number;
  sells: number;
  cash: number;
}

// The summary strip's arithmetic: an unpriced order contributes nothing, because a plan without a
// quote has no R$ amount to show.
export function orderTotals(orders: SuggestedOrder[]): OrderTotals {
  const amount = (order: SuggestedOrder) => order.quantity * (order.suggested_price ?? 0);
  const buys = orders
    .filter((order) => isBuySide(order.side))
    .reduce((sum, order) => sum + amount(order), 0);
  const sells = orders
    .filter((order) => isSellSide(order.side))
    .reduce((sum, order) => sum + amount(order), 0);

  return { buys, sells, cash: buys - sells };
}

// "Copiar para a corretora": one line per order, in the reference list's order.
export function orderClipboardText(orders: SuggestedOrder[]): string {
  return orders
    .map((order) => {
      const price =
        order.suggested_price === undefined ? "—" : formatCurrency(order.suggested_price);
      return `${orderSideLabel(order.side)} ${order.ticker} ${order.quantity} @ ${price}`;
    })
    .join("\n");
}

const CSV_HEADER = ["Ticker", "Lado", "Quantidade", "Preco", "Valor", "Status", "Executado"];

// Semicolon-delimited and dot-decimal: the shape a pt-BR spreadsheet opens without a re-import
// dialog. A field carrying the delimiter or a quote is escaped the RFC 4180 way.
export function ordersCsv(orders: SuggestedOrder[]): string {
  const rows = orders.map((order) => [
    order.ticker,
    orderSideLabel(order.side),
    String(order.quantity),
    (order.suggested_price ?? 0).toFixed(2),
    (order.quantity * (order.suggested_price ?? 0)).toFixed(2),
    orderStatusLabel(order.status),
    String(order.executed_quantity),
  ]);

  return [CSV_HEADER, ...rows].map((row) => row.map(csvCell).join(";")).join("\n");
}

function csvCell(value: string): string {
  return /[;"\n]/.test(value) ? `"${value.replaceAll('"', '""')}"` : value;
}

// The order's display state. PARCIAL is derived from the executed quantity and is never a persisted
// lifecycle state: a partially matched order stays SUGGESTED on the wire, and only the screen names
// the partial execution. A quantity fully covered reads EXECUTED/IMPORTED from its persisted state.
export type OrderExecutionState = "EXECUTED" | "PARTIAL" | "UNEXECUTED";

export function orderExecutionState(order: SuggestedOrder): OrderExecutionState {
  if (order.executed_quantity <= 0) return "UNEXECUTED";
  if (order.executed_quantity < order.quantity) return "PARTIAL";
  return "EXECUTED";
}

// The chip label: PARCIAL for a partial execution, the persisted lifecycle label otherwise.
export function orderStateLabel(order: SuggestedOrder): string {
  return orderExecutionState(order) === "PARTIAL" ? "PARCIAL" : orderStatusLabel(order.status);
}

export interface OrderExecutionSummary {
  executed: SuggestedOrder[];
  partial: SuggestedOrder[];
  unexecuted: SuggestedOrder[];
}

// The month's execution buckets, in the progress rail's vocabulary: executed (fully matched),
// partial (PARCIAL, display-only) and unexecuted. An unexecuted or partial order never blocks the
// close, so this is a summary, not a gate.
export function orderExecutionSummary(orders: SuggestedOrder[]): OrderExecutionSummary {
  const summary: OrderExecutionSummary = { executed: [], partial: [], unexecuted: [] };
  for (const order of orders) {
    const state = orderExecutionState(order);
    if (state === "EXECUTED") summary.executed.push(order);
    else if (state === "PARTIAL") summary.partial.push(order);
    else summary.unexecuted.push(order);
  }
  return summary;
}

const FUNDING_SOURCE_LABELS: Record<FundingSource, string> = {
  NEUTRO: "Neutro",
  OUTSIDE_STRATEGY: "Fora da estratégia",
  SURPLUS: "Excedente",
};

export function fundingSourceLabel(source: FundingSource): string {
  return FUNDING_SOURCE_LABELS[source];
}

// The funding source's tone: a neutro sale is the neutral case, an outside-strategy sale the action
// blue, a surplus sale the amber surplus.
export type FundingTone = "neutral" | "blue" | "amber";

const FUNDING_TONES: Record<FundingSource, FundingTone> = {
  NEUTRO: "neutral",
  OUTSIDE_STRATEGY: "blue",
  SURPLUS: "amber",
};

export function fundingSourceTone(source: FundingSource): FundingTone {
  return FUNDING_TONES[source];
}

// The executed ratio the screen shows: matched units over the suggested quantity.
export function orderProgressLabel(order: SuggestedOrder): string {
  return `${formatNumber(order.executed_quantity)}/${formatNumber(order.quantity)}`;
}

export interface OrderStrategyContribution {
  strategy: string;
  // Signed whole units the plan moves for this (strategy, ticker) slice: positive adds, negative
  // sells down. Derived as target quantity − attributed quantity.
  quantity: number;
}

// What the computed plan says about one persisted order: which strategies the units belong to
// (attribution context) and, for a funding sell, why the sale exists (funding context). The
// persisted order read carries neither, so the screen joins the plan read by ticker.
export interface OrderContext {
  contributions: OrderStrategyContribution[];
  fundingSource?: FundingSource;
}

// A ticker can carry both a buy and a funding sell in one month (a slice below target buying while
// a NEUTRO slice sells), so the plan context is keyed by ticker and the order's trade direction.
export function orderContextKey(order: Pick<SuggestedOrder, "ticker" | "side">): string {
  return `${order.ticker}|${isBuySide(order.side) ? "BUY" : "SELL"}`;
}

export function orderContexts(plan: OrderPlan | null | undefined): Map<string, OrderContext> {
  const contexts = new Map<string, OrderContext>();
  if (plan === null || plan === undefined) return contexts;

  for (const move of plan.attribution) {
    const key = `${move.ticker}|${move.side === "ALLOCATE" ? "BUY" : "SELL"}`;
    const context = contexts.get(key) ?? { contributions: [] };
    context.contributions.push({
      strategy: move.strategy,
      quantity: move.side === "ALLOCATE" ? move.quantity : -move.quantity,
    });
    contexts.set(key, context);
  }

  for (const candidate of plan.candidates) {
    if (candidate.funding_source === undefined) continue;
    const key = orderContextKey(candidate);
    const context = contexts.get(key) ?? { contributions: [] };
    context.fundingSource = candidate.funding_source;
    contexts.set(key, context);
  }

  return contexts;
}

// The plan is computed on read and the orders are the persisted snapshot, so the two can drift after
// a capital or targets edit until the plan is recalculated. A computed candidate is only the row's
// context when it still matches the persisted order exactly — same side, same quantity.
export function planCoversOrder(
  plan: OrderPlan | null | undefined,
  order: SuggestedOrder,
): boolean {
  if (plan === null || plan === undefined) return false;
  return plan.candidates.some(
    (candidate) =>
      candidate.ticker === order.ticker &&
      candidate.side === order.side &&
      candidate.quantity === order.quantity,
  );
}

// The chip tone a display state wears. A partial execution carries the attention amber; every other
// row keeps the tone of its persisted lifecycle state.
export type OrderStateTone = "amber" | "blue" | "green";

const STATUS_TONES: Record<SuggestedOrderStatus, OrderStateTone> = {
  SUGGESTED: "amber",
  EXECUTED: "blue",
  IMPORTED: "green",
};

export function orderStateTone(order: SuggestedOrder): OrderStateTone {
  return orderExecutionState(order) === "PARTIAL" ? "amber" : STATUS_TONES[order.status];
}
