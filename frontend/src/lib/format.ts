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

const ISO_DATE = /^(\d{4})-(\d{2})-(\d{2})$/;

// Split the ISO date ourselves: `new Date("2026-11-30")` parses as UTC midnight and renders the
// previous day in a negative-offset browser.
export function formatIsoDate(iso: string): string {
  const match = ISO_DATE.exec(iso);
  return match ? `${match[3]}/${match[2]}/${match[1]}` : iso;
}

// A business day is a weekday here: the app has no exchange-holiday calendar, so callers state the
// holiday caveat where the date surfaces.
export function isWeekend(iso: string): boolean {
  const match = ISO_DATE.exec(iso);
  if (!match) return false;
  const day = new Date(
    Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3])),
  ).getUTCDay();
  return day === 0 || day === 6;
}
