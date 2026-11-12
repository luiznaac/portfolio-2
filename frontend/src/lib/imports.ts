// Pure import-log helpers — no React imports here. The inbox and the history share one vocabulary
// (source and state labels), one definition of the parked session and one age reading, so the two
// screens cannot drift apart.

import type { Import, ImportSource, ImportStatus } from "../api/types.ts";
import { formatIsoDate } from "./format.ts";

const SOURCE_LABELS = {
  B3_TRADES: "B3 · Negócios",
  B3_POSITION: "B3 · Custódia",
  B3_MOVEMENTS: "B3 · Movimentações",
  XP_REPORT: "XP · Relatório",
} satisfies Record<ImportSource, string>;

export const IMPORT_SOURCES = Object.keys(SOURCE_LABELS) as ImportSource[];

export function importSourceLabel(source: ImportSource): string {
  return SOURCE_LABELS[source];
}

// What each source carries, in the card's own words. Kept beside the labels so the Fontes cards
// never restate a source's shape.
const SOURCE_DETAILS = {
  B3_TRADES: "Extrato de negócios fechados",
  B3_POSITION: "Snapshot de posição em custódia",
  B3_MOVEMENTS: "Negócios, proventos e eventos",
  XP_REPORT: "Relatório mensal por estratégia",
} satisfies Record<ImportSource, string>;

export function importSourceDetail(source: ImportSource): string {
  return SOURCE_DETAILS[source];
}

// The lifecycle labels in reading order; `satisfies` keeps the map exhaustive over the union, so a
// new status is a compile error until it gets a label, and the filter options derive from the same
// declaration instead of restating the key set.
const STATUS_LABELS = {
  STAGED: "Aguardando revisão",
  REVIEWED: "Revisada",
  APPLIED: "Aplicada",
  FAILED: "Falhou",
} satisfies Record<ImportStatus, string>;

export const IMPORT_STATUSES = Object.keys(STATUS_LABELS) as ImportStatus[];

export function importStatusLabel(status: ImportStatus): string {
  return STATUS_LABELS[status];
}

// A parked import is the assistant session: staged or reviewed still waits for a decision. An
// applied or failed record is history, not a session to resume.
export function isParkedImport(record: Import): boolean {
  return record.status === "STAGED" || record.status === "REVIEWED";
}

export function parkedImports(rows: readonly Import[]): Import[] {
  return rows.filter(isParkedImport);
}

export function newestFirst(rows: readonly Import[]): Import[] {
  return [...rows].sort((a, b) => b.created_at.localeCompare(a.created_at) || b.id - a.id);
}

const LOCAL_DATE_TIME = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?/;

// The record's age as a short console label. Parse the local fields ourselves (the backend's
// `created_at` has no zone) so the browser's timezone cannot shift the reading; past a month the
// exact date is more useful than a day count.
export function importAge(createdAt: string, now: Date = new Date()): string {
  const match = LOCAL_DATE_TIME.exec(createdAt);
  if (!match) return createdAt;

  const [, year, month, day, hour, minute, second] = match;
  const created = new Date(
    Number(year),
    Number(month) - 1,
    Number(day),
    Number(hour),
    Number(minute),
    Number(second ?? 0),
  );
  const minutes = Math.floor((now.getTime() - created.getTime()) / 60_000);

  if (minutes < 1) return "agora";
  if (minutes < 60) return `há ${minutes} min`;

  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `há ${hours} h`;

  const days = Math.floor(hours / 24);
  if (days < 30) return `há ${days} d`;

  return formatIsoDate(createdAt.slice(0, 10));
}
