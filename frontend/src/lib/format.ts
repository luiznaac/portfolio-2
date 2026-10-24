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

function percentFormatter(digits: number): Intl.NumberFormat {
  return new Intl.NumberFormat("pt-BR", {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  });
}

// A percentage value is already the number itself (1.5 = 1,50%), as the backend serialises it.
export function formatPercent(value: number | undefined, digits = 2): string {
  if (value === undefined) return "—";
  return `${percentFormatter(digits).format(value)}%`;
}

export function formatSignedPercent(value: number | undefined, digits = 2): string {
  if (value === undefined) return "—";
  const formatted = formatPercent(value, digits);
  return value > 0 ? `+${formatted}` : formatted;
}

const LOCAL_DATE_TIME = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/;

// A local date-time with no zone (the backend's snapshot `created_at`); parse the fields ourselves so
// the browser's timezone cannot shift the stamp.
export function formatLocalDateTime(value: string): string {
  const match = LOCAL_DATE_TIME.exec(value);
  if (!match) return value;
  const [, year, month, day, hour, minute] = match;
  return `${day}/${month}/${year} ${hour}:${minute}`;
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
