// The Análise area's shared period rail: one window (preset + month anchor) that Performance,
// Proventos and Impostos all read. Pure — no React imports here (see AGENTS.md) — so the month
// arithmetic and the clamp at the data edges stay unit-tested instead of living in the page.

import { monthPeriodLabel } from "./month.ts";

export type WindowPreset = "1m" | "3m" | "6m" | "12m" | "year";

export interface WindowPresetOption {
  key: WindowPreset;
  label: string;
}

// The rail's presets, in display order. The year preset is calendar-shaped (January through the
// anchor), while the others are rolling windows ending on the anchor.
export const WINDOW_PRESETS: WindowPresetOption[] = [
  { key: "1m", label: "Mês" },
  { key: "3m", label: "3 meses" },
  { key: "6m", label: "6 meses" },
  { key: "12m", label: "12 meses" },
  { key: "year", label: "Ano" },
];

export const DEFAULT_WINDOW_PRESET: WindowPreset = "12m";

export interface WindowRange {
  start: string;
  end: string;
}

export interface WindowBounds {
  first: string;
  last: string;
}

const PERIOD = /^(\d{4})-(\d{2})$/;

function monthIndex(period: string): number {
  const match = PERIOD.exec(period);
  if (match === null) return Number.NaN;
  return Number(match[1]) * 12 + (Number(match[2]) - 1);
}

function fromIndex(index: number): string {
  const year = Math.floor(index / 12);
  const month = (index % 12) + 1;
  return `${year}-${String(month).padStart(2, "0")}`;
}

export function addMonths(period: string, delta: number): string {
  return fromIndex(monthIndex(period) + delta);
}

// The month anchor is bounded by the months the read actually holds: shifting before the first
// point or past the last close would describe a window with no data.
export function clampPeriod(period: string, bounds: WindowBounds): string {
  const index = monthIndex(period);
  const first = monthIndex(bounds.first);
  const last = monthIndex(bounds.last);
  return fromIndex(Math.min(Math.max(index, first), last));
}

export function shiftAnchor(anchor: string, delta: number, bounds: WindowBounds): string {
  return clampPeriod(addMonths(anchor, delta), bounds);
}

// The rail opens on 12 months ending the last closed month; the computed history has no close, so
// it falls back to the newest month the series carries. A closed month outside the available span
// is ignored rather than widening the bounds.
export function defaultAnchor(periods: string[], lastClosedPeriod?: string): string | undefined {
  if (periods.length === 0) return undefined;
  const sorted = [...periods].sort();
  const last = sorted[sorted.length - 1];
  if (lastClosedPeriod !== undefined && lastClosedPeriod >= sorted[0] && lastClosedPeriod <= last) {
    return lastClosedPeriod;
  }
  return last;
}

export function windowBounds(periods: string[]): WindowBounds | undefined {
  if (periods.length === 0) return undefined;
  const sorted = [...periods].sort();
  return { first: sorted[0], last: sorted[sorted.length - 1] };
}

export function windowRange(preset: WindowPreset, anchor: string): WindowRange {
  if (preset === "year") return { start: `${anchor.slice(0, 4)}-01`, end: anchor };
  const span = Number(preset.slice(0, -1));
  return { start: addMonths(anchor, -(span - 1)), end: anchor };
}

// The one rendering of a window's two edges, shared by the rail and the page's KPI hints.
export function windowRangeLabel(range: WindowRange): string {
  return `${monthPeriodLabel(range.start)} – ${monthPeriodLabel(range.end)}`;
}

export function inWindow(period: string, range: WindowRange): boolean {
  return period >= range.start && period <= range.end;
}

// The window's calendar months in order, both edges included — the income chart plots every month
// of the window, including the ones that paid nothing.
export function windowMonthKeys(range: WindowRange): string[] {
  const keys: string[] = [];
  if (range.start > range.end) return keys;
  let cursor = range.start;
  while (cursor <= range.end) {
    keys.push(cursor);
    cursor = addMonths(cursor, 1);
  }
  return keys;
}

// The same window expressed as the inclusive ISO day bounds the income read's `from`/`to` filters
// take: the first day of the start month through the last day of the end month.
export function windowDayRange(range: WindowRange): { from: string; to: string } {
  return { from: `${range.start}-01`, to: `${range.end}-${daysInMonth(range.end)}` };
}

function daysInMonth(period: string): string {
  const match = PERIOD.exec(period);
  if (match === null) return "31";
  const days = new Date(Date.UTC(Number(match[1]), Number(match[2]), 0)).getUTCDate();
  return String(days).padStart(2, "0");
}

export function windowMonths<T extends { period: string }>(months: T[], range: WindowRange): T[] {
  return months.filter((month) => inWindow(month.period, range));
}
