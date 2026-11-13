// Pure Carteira · Histórico helpers — no React imports here (see AGENTS.md). The closed-month list
// and the frozen detail render only the snapshot's own values; nothing here reads live allocation,
// quotes or trades, because a closed month is the record the close wrote, not a recomputation.

import type {
  ClassTotalSnapshot,
  Month,
  OrdersSnapshot,
  Snapshot,
  StrategySliceSnapshot,
} from "../api/types.ts";
import { formatLocalDateTime, formatTradeSide } from "./format.ts";
import { assetClassLabel, monthPeriodLabel } from "./month.ts";
import { type OrderStateTone, orderSideLabel, orderStateLabel, orderStateTone } from "./orders.ts";
import { portfolioHistoryDetail } from "./routes.ts";
import { strategyKindLabel } from "./strategy.ts";

export interface HistoryListRow {
  monthId: number;
  label: string;
  href: string;
  capital: number;
  floor: number;
  aportes: number;
}

// One record per closed month, in the server's newest-first order. The href is the frozen month's
// own route, so the list never opens a live read.
export function historyListRows(months: Month[]): HistoryListRow[] {
  return months.map((month) => ({
    monthId: month.id,
    label: monthPeriodLabel(month.period),
    href: portfolioHistoryDetail(month.period),
    capital: month.capital,
    floor: month.floor,
    aportes: month.aportes,
  }));
}

export interface SnapshotSummary {
  periodLabel: string;
  createdLabel: string;
  capital: number;
  floor: number;
  aportes: number;
  caixa: number;
  incomeNet: number;
  darf: number;
}

// The detail's stat row, read from the frozen state alone. DARF is the exempt meter's final tax for
// the closed month.
export function snapshotSummary(snapshot: Snapshot): SnapshotSummary {
  const { state } = snapshot;
  return {
    periodLabel: monthPeriodLabel(state.period),
    createdLabel: formatLocalDateTime(snapshot.created_at),
    capital: state.capital,
    floor: state.floor,
    aportes: state.aportes,
    caixa: state.caixa,
    incomeNet: state.income.net,
    darf: state.tax.meter.tax,
  };
}

export interface FrozenClassRow {
  key: string;
  label: string;
  target?: number;
  ideal: number;
  actual: number;
  deficit: number;
  drift: number;
}

export function frozenClassRows(classes: ClassTotalSnapshot[]): FrozenClassRow[] {
  return classes.map((row) => ({
    key: row.asset_class,
    label: assetClassLabel(row.asset_class),
    target: row.target,
    ideal: row.ideal,
    actual: row.actual,
    deficit: row.deficit,
    drift: row.drift,
  }));
}

export interface FrozenOrderRow {
  key: string;
  ticker: string;
  sideLabel: string;
  quantity: number;
  price?: number;
  stateLabel: string;
  stateTone: OrderStateTone;
}

// The month's persisted orders followed by the trades no suggestion planned. A partially executed
// order reads PARCIAL through the shared display rule; its persisted status stays untouched.
export function frozenOrderRows(orders: OrdersSnapshot): FrozenOrderRow[] {
  const planned = orders.orders.map((order) => ({
    key: `order-${order.id}`,
    ticker: order.ticker,
    sideLabel: orderSideLabel(order.side),
    quantity: order.quantity,
    price: order.suggested_price,
    stateLabel: orderStateLabel(order),
    stateTone: orderStateTone(order),
  }));
  const unplanned = orders.unplanned_trades.map((trade) => ({
    key: `trade-${trade.id}`,
    ticker: trade.ticker,
    sideLabel: formatTradeSide(trade.side),
    quantity: trade.quantity,
    price: undefined,
    stateLabel: "Não planejada",
    stateTone: "amber" as const,
  }));

  return [...planned, ...unplanned];
}

export interface FrozenSliceRow {
  strategyId: number;
  strategy: string;
  kindLabel: string;
  valueEnd: number;
  flow: number;
}

// One row per Strategy frozen at the close, the manual bucket included. A snapshot written before
// the strategy rung landed carries no slices and renders as an empty list.
export function frozenSliceRows(strategies: StrategySliceSnapshot[] | undefined): FrozenSliceRow[] {
  return (strategies ?? []).map((slice) => ({
    strategyId: slice.strategy_id,
    strategy: slice.strategy,
    kindLabel: strategyKindLabel(slice.kind),
    valueEnd: slice.value_end,
    flow: slice.flow,
  }));
}

export const FROZEN_CAPTION =
  "Holdings, atribuições, ordens, proventos e a apuração de imposto como estavam no fechamento. Nada aqui é recalculado depois.";
