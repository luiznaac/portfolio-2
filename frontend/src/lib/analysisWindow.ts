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

export function windowMonths<T extends { period: string }>(months: T[], range: WindowRange): T[] {
  return months.filter((month) => inWindow(month.period, range));
}
