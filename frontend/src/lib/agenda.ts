// Pure Agenda helpers — no React imports here (see AGENTS.md). The agenda has a manual lifecycle:
// each item is dismissed by the owner and remembered in localStorage, so this module owns the item
// derivation and the read-set serialisation; the component owns the storage boundary.

import type { Bond, Month } from "../api/types.ts";
import { addDays, daysBetween, monthKey } from "./dates.ts";
import { formatIsoDate } from "./format.ts";
import { monthPeriodLabel } from "./month.ts";

export type AgendaKind = "MATURITY" | "CLOSE";

export interface AgendaItem {
  id: string;
  kind: AgendaKind;
  date: string;
  title: string;
  detail: string;
  to: string;
}

// RF maturities enter the agenda a week ahead and stay until the owner marks them read.
const MATURITY_LEAD_DAYS = 7;

function maturityDetail(maturityDate: string, today: string): string {
  const days = daysBetween(today, maturityDate);
  if (days < 0) return `Venceu em ${formatIsoDate(maturityDate)}`;
  if (days === 0) return "Vence hoje";
  return `Vence em ${days} ${days === 1 ? "dia" : "dias"}`;
}

// The agenda: fixed-income maturities inside the lead window (overdue ones included, since the
// redemption is a manual step) plus the close reminder. The reminder appears on day 1 of the month
// for the month that should be closed: while that month is still open it reads "pendente", and
// once it is closed the latest closed month keeps a "concluído" notice — closing does not clear it,
// only the owner's mark-as-read does, which is the manual half of "until closed AND dismissed".
export function agendaItems(
  bonds: Bond[],
  month: Month | null,
  closedMonths: Month[],
  today: string,
): AgendaItem[] {
  const leadLimit = addDays(today, MATURITY_LEAD_DAYS);
  const currentMonth = monthKey(today);
  const items: AgendaItem[] = [];

  for (const bond of bonds) {
    if (bond.maturity_date > leadLimit) continue;
    items.push({
      id: `maturity:${bond.id}:${bond.maturity_date}`,
      kind: "MATURITY",
      date: bond.maturity_date,
      title: `Vencimento de ${bond.name}`,
      detail: maturityDetail(bond.maturity_date, today),
      to: "/fixed-income",
    });
  }

  const overdueOpen = month !== null && month.status === "OPEN" && month.period < currentMonth;
  // The backend lists closed months newest first: the first one is the close that just happened
  // (or the latest one), which is what the completed notice points at.
  const latestClosed = closedMonths[0];
  if (overdueOpen && month !== null) {
    items.push({
      // A distinct id from the completed notice: dismissing "pendente" must not swallow the
      // post-close "concluído", which needs its own mark-as-read.
      id: `close-pending:${month.period}`,
      kind: "CLOSE",
      date: `${currentMonth}-01`,
      title: `Fechamento de ${monthPeriodLabel(month.period)} pendente`,
      detail: "O mês segue aberto",
      to: "/fechamento",
    });
  } else if (latestClosed !== undefined) {
    items.push({
      id: `close-done:${latestClosed.period}`,
      kind: "CLOSE",
      date: `${currentMonth}-01`,
      title: `Fechamento de ${monthPeriodLabel(latestClosed.period)} concluído`,
      detail: "Marque como lida para tirar da agenda",
      to: "/fechamento",
    });
  }

  return items.sort((a, b) => a.date.localeCompare(b.date) || a.kind.localeCompare(b.kind));
}

// The dismissed-item store: a JSON array of ids. Anything unreadable is treated as "nothing read"
// rather than crashing the page.
export function readAgendaIds(raw: string | null): Set<string> {
  if (raw === null) return new Set();
  try {
    const parsed: unknown = JSON.parse(raw);
    if (!Array.isArray(parsed)) return new Set();
    return new Set(parsed.filter((value): value is string => typeof value === "string"));
  } catch {
    return new Set();
  }
}

export function serializeAgendaIds(ids: Iterable<string>): string {
  return JSON.stringify([...ids]);
}

export function dismissAgendaItem(ids: ReadonlySet<string>, id: string): Set<string> {
  const next = new Set(ids);
  next.add(id);
  return next;
}
