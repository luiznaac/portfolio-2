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

export function formatTradeSide(side: TradeSide): string {
  return side === "BUY" ? "Compra" : "Venda";
}
