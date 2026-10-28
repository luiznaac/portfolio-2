// Pure Proventos helpers — no React imports here (see AGENTS.md). The page stays a thin renderer:
// the pt-BR labels, the table rows and the forecast total are computed and unit-tested here.

import type { Income, IncomeStatus, IncomeType } from "../api/types.ts";

const TYPE_LABELS: Record<IncomeType, string> = {
  DIVIDEND: "Dividendo",
  JCP: "JCP",
  FUND_INCOME: "Rendimento",
};

export function incomeTypeLabel(type: IncomeType): string {
  return TYPE_LABELS[type];
}

const STATUS_LABELS: Record<IncomeStatus, string> = {
  ANNOUNCED: "Anunciado",
  PAYABLE: "Pagável",
  RECEIVED: "Recebido",
  RECONCILED: "Conciliado",
};

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
