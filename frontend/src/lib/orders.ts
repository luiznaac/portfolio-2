// Pure Orders-screen helpers — no React imports here (see AGENTS.md). The page renders labels,
// side groups, totals, the broker clipboard text and the CSV; all four are unit-tested here.

import type {
  OrderSide,
  SuggestedOrder,
  SuggestedOrderStatus,
  TransferStatus,
} from "../api/types.ts";
import { formatCurrency } from "./format.ts";

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
