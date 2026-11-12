// Pure Proventos helpers — no React imports here (see AGENTS.md). The page stays a thin renderer:
// the pt-BR labels, the table rows and the forecast total are computed and unit-tested here.

import type { Income, IncomeStatus, IncomeType } from "../api/types.ts";
import { type WindowRange, inWindow, windowMonthKeys } from "./analysisWindow.ts";
import { monthKey } from "./dates.ts";

const TYPE_LABELS: Record<IncomeType, string> = {
  DIVIDEND: "Dividendo",
  JCP: "JCP",
  FUND_INCOME: "Rendimento",
};

export function incomeTypeLabel(type: IncomeType): string {
  return TYPE_LABELS[type];
}

// The lifecycle labels in reading order; `satisfies` keeps the map exhaustive over the union, so a
// new status is a compile error until it gets a label, and the filter options derive from the same
// declaration instead of restating the key set.
const STATUS_LABELS = {
  ANNOUNCED: "Anunciado",
  PAYABLE: "Pagável",
  RECEIVED: "Recebido",
  RECONCILED: "Conciliado",
} satisfies Record<IncomeStatus, string>;

export const INCOME_STATUSES = Object.keys(STATUS_LABELS) as IncomeStatus[];

export function incomeStatusLabel(status: IncomeStatus): string {
  return STATUS_LABELS[status];
}

export interface IncomeRow {
  key: number;
  date: string;
  ticker: string;
  typeLabel: string;
  status: IncomeStatus;
  statusLabel: string;
  gross: number;
  retained: number;
  net: number;
}

export function incomeRows(incomes: Income[]): IncomeRow[] {
  return incomes.map((income) => ({
    key: income.id,
    date: income.date,
    ticker: income.ticker,
    typeLabel: incomeTypeLabel(income.type),
    status: income.status,
    statusLabel: incomeStatusLabel(income.status),
    gross: income.gross_amount,
    retained: income.retained_amount,
    net: income.net_amount,
  }));
}

// The forecast total: what the movements will pay, net of withholding.
export function netTotal(incomes: Income[]): number {
  return incomes.reduce((total, income) => total + income.net_amount, 0);
}

// Soonest first, for the "Próximos proventos" panel; the input is never mutated.
export function byPayableDate(incomes: Income[]): Income[] {
  return [...incomes].sort((a, b) => a.date.localeCompare(b.date));
}

// Announced and payable are the forward-looking stages: money the portfolio still owes the owner.
// Received and reconciled are settled, so they only count inside the window they were paid in.
export function isOpenIncome(status: IncomeStatus): boolean {
  return status === "ANNOUNCED" || status === "PAYABLE";
}

export interface IncomeMonthBar {
  period: string;
  value: number;
}

export interface IncomeWindowView {
  // Every open row, soonest first, regardless of the window — a known payable never disappears.
  open: Income[];
  // Settled rows paid inside the window, newest first.
  received: Income[];
  receivedTotal: number;
  toReceiveTotal: number;
  next?: Income;
  upcomingOutsideRange: boolean;
  byMonth: IncomeMonthBar[];
  monthsInWindow: number;
}

function paidInRange(income: { date: string }, range: WindowRange): boolean {
  return inWindow(monthKey(income.date), range);
}

// The Análise window read for Proventos: the settled rows the window holds feed the KPIs and the
// monthly chart, while "a receber" stays forward-looking and may sit outside the window entirely.
export function incomeWindowView(incomes: Income[], range: WindowRange): IncomeWindowView {
  const open = incomes
    .filter((income) => isOpenIncome(income.status))
    .sort((a, b) => a.date.localeCompare(b.date) || a.id - b.id);
  const received = incomes
    .filter((income) => !isOpenIncome(income.status) && paidInRange(income, range))
    .sort((a, b) => b.date.localeCompare(a.date) || b.id - a.id);
  const months = windowMonthKeys(range);

  return {
    open,
    received,
    receivedTotal: received.reduce((total, income) => total + income.net_amount, 0),
    toReceiveTotal: open.reduce((total, income) => total + income.net_amount, 0),
    next: open[0],
    upcomingOutsideRange: open.some((income) => !paidInRange(income, range)),
    byMonth: months.map((period) => ({
      period,
      value: received
        .filter((income) => monthKey(income.date) === period)
        .reduce((total, income) => total + income.net_amount, 0),
    })),
    monthsInWindow: months.length,
  };
}

export interface IncomeLedgerRow extends IncomeRow {
  outsideWindow: boolean;
  focused: boolean;
}

// The ledger's reading order: every receivable row first, then the settled window rows. A receivable
// paid outside the window keeps its place and is flagged "fora do recorte"; `focusId` is the id an
// attention deep link carries, so the destination can highlight that row.
export function incomeLedgerRows(
  open: IncomeRow[],
  received: IncomeRow[],
  range: WindowRange,
  focusId?: number,
): IncomeLedgerRow[] {
  return [
    ...open.map((row) => toLedgerRow(row, range, focusId)),
    ...received.map((row) => toLedgerRow(row, range, focusId)),
  ];
}

function toLedgerRow(row: IncomeRow, range: WindowRange, focusId?: number): IncomeLedgerRow {
  return {
    ...row,
    outsideWindow: !paidInRange(row, range),
    focused: row.key === focusId,
  };
}
