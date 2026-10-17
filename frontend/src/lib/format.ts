// Pure formatting helpers — no React imports here (see AGENTS.md). This is what gets unit-tested
// in src/lib/**, since these repos don't have component-render tests yet.

import type { TradeSide } from "../api/types.ts";

export function formatHealthStatus(isHealthy: boolean): string {
  return isHealthy ? "healthy" : "unhealthy";
}

const NUMBER = new Intl.NumberFormat("pt-BR");

export function formatNumber(value: number): string {
  return NUMBER.format(value);
}

const CURRENCY = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });

// Intl renders the pt-BR currency with a non-breaking space after the symbol; normalise it to a
// regular space so the string is what it looks like.
export function formatCurrency(value: number): string {
  return CURRENCY.format(value).replace(/\u00a0/g, " ");
}

export function formatTradeSide(side: TradeSide): string {
  return side === "BUY" ? "Compra" : "Venda";
}
