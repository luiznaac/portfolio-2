// Pure Eventos corporativos helpers — no React imports here (see AGENTS.md). The page stays a thin
// renderer: the pt-BR labels, the one-line detail and the review split are computed and tested here.

import type { CorporateAction, CorporateActionKind, CorporateActionStatus } from "../api/types.ts";
import { formatCurrency, formatNumber } from "./format.ts";

const KIND_LABELS: Record<CorporateActionKind, string> = {
  SPLIT: "Desdobramento",
  REVERSE_SPLIT: "Grupamento",
  BONUS_SHARES: "Bonificação em ações",
  TICKER_CHANGE: "Troca de código",
  RIGHTS_ISSUE: "Subscrição",
  RIGHTS_ASSIGNMENT: "Cessão",
  INCORPORATION: "Incorporação",
  MERGER: "Fusão",
  SPIN_OFF: "Cisão",
  CAPITAL_RETURN: "Restituição",
  AMORTIZATION: "Amortização",
  CASH_BONUS: "Bonificação em dinheiro",
  OTHER: "Outro",
};

const STATUS_LABELS: Record<CorporateActionStatus, string> = {
  APPLIED: "Aplicado",
  NEEDS_REVIEW: "Revisar",
};

export function corporateActionKindLabel(kind: CorporateActionKind): string {
  return KIND_LABELS[kind];
}

export function corporateActionStatusLabel(status: CorporateActionStatus): string {
  return STATUS_LABELS[status];
}

// The one-line explanation of what the event did: the factor for a quantity event, the cash per
// share for a cash-only one, the new code for a remap, and the export's own label for a row that is
// waiting for a human.
export function corporateActionDetail(action: CorporateAction): string {
  switch (action.kind) {
    case "SPLIT":
      return action.ratio ? `×${formatNumber(action.ratio)}` : "fator ausente";
    case "REVERSE_SPLIT":
      return action.ratio ? `÷${formatNumber(action.ratio)}` : "fator ausente";
    case "BONUS_SHARES":
      return action.ratio ? `×${formatNumber(1 + action.ratio)}` : "fator ausente";
    case "CAPITAL_RETURN":
    case "AMORTIZATION":
    case "CASH_BONUS":
      return action.declared_value_per_share
        ? `${formatCurrency(action.declared_value_per_share)}/ação`
        : "valor ausente";
    case "TICKER_CHANGE":
      return action.target_ticker ? `→ ${action.target_ticker}` : "código ausente";
    default:
      return action.raw_type ?? "—";
  }
}

export interface CorporateActionRow {
  key: number;
  date: string;
  ticker: string;
  kindLabel: string;
  detail: string;
  status: CorporateActionStatus;
  statusLabel: string;
  isReview: boolean;
  source: string;
}

export function corporateActionRows(actions: CorporateAction[]): CorporateActionRow[] {
  return actions.map((action) => ({
    key: action.id,
    date: action.ex_date,
    ticker: action.ticker,
    kindLabel: corporateActionKindLabel(action.kind),
    detail: corporateActionDetail(action),
    status: action.status,
    statusLabel: corporateActionStatusLabel(action.status),
    isReview: action.status === "NEEDS_REVIEW",
    source: action.process_number ?? action.raw_type ?? "—",
  }));
}

// The review queue is the subset of the log the engine refused to apply on its own.
export function reviewQueue(actions: CorporateAction[]): CorporateAction[] {
  return actions.filter((action) => action.status === "NEEDS_REVIEW");
}
