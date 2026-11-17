// Pure Eventos corporativos helpers — no React imports here (see AGENTS.md). The page stays a thin
// renderer: the pt-BR labels, the one-line detail and the review split are computed and tested here.

import type {
  CorporateAction,
  CorporateActionKind,
  CorporateActionResolution,
  CorporateActionSource,
  CorporateActionStatus,
} from "../api/types.ts";
import { formatCurrency, formatNumber } from "./format.ts";
import { importSourceLabel } from "./imports.ts";

const KIND_LABELS: Record<CorporateActionKind, string> = {
  SPLIT: "Desdobramento",
  REVERSE_SPLIT: "Grupamento",
  BONUS_SHARES: "Bonificação em ações",
  TICKER_CHANGE: "Troca de código",
  RIGHTS_ISSUE: "Subscrição",
  RIGHTS_ASSIGNMENT: "Cessão",
  RIGHTS_ALLOTMENT: "Provisão de direitos",
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

// The Movimentações label is the import source's own; only the engine's derived fallback needs a
// name of its own.
export function corporateActionSourceLabel(source: CorporateActionSource): string {
  return source === "DERIVED" ? "Derivado" : importSourceLabel(source);
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
    case "RIGHTS_ALLOTMENT":
      // The row's ticker is the rights paper. A real allotment names the underlying and factor; the
      // derived fallback has neither, so it states the granted units it stood in for.
      if (action.target_ticker) {
        return `${action.target_ticker}${action.ratio ? ` ×${formatNumber(action.ratio)}` : ""}`;
      }
      return action.granted_quantity !== undefined
        ? `+${formatNumber(action.granted_quantity)} un`
        : "origem ausente";
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
    // A manual kind the owner acknowledged is settled without the engine's math, so the history
    // says "Tratado" instead of claiming an application that never ran.
    statusLabel:
      action.status === "APPLIED" && isManualReview(action)
        ? "Tratado"
        : corporateActionStatusLabel(action.status),
    isReview: action.status === "NEEDS_REVIEW",
    // The derived allotment is the engine's own fallback, so the history names it instead of a
    // provenance column the export never carried.
    source:
      action.source === "DERIVED"
        ? corporateActionSourceLabel(action.source)
        : (action.process_number ?? action.raw_type ?? "—"),
  }));
}

// The review queue is the subset of the log the engine refused to apply on its own.
export function reviewQueue(actions: CorporateAction[]): CorporateAction[] {
  return actions.filter((action) => action.status === "NEEDS_REVIEW");
}

// The applied history is everything the engine settled or the owner acknowledged; it is the log
// beside the queue.
export function appliedHistory(actions: CorporateAction[]): CorporateAction[] {
  return actions.filter((action) => action.status === "APPLIED");
}

// The template fields the resolve form offers for a parked event. A manual kind offers none — its
// decision is the acknowledgement itself.
export type ResolutionField = "ratio" | "declared_value_per_share" | "target_ticker";

export const RESOLUTION_FIELD_LABELS: Record<ResolutionField, string> = {
  ratio: "Fator",
  declared_value_per_share: "Valor por ação",
  target_ticker: "Novo código",
};

// One table for every kind: whether the engine can apply it at all (mirrors the backend's
// requiresReview) and which missing columns the resolve form asks for. A new kind is one row here,
// so the manual set, the form and the reason text cannot drift apart.
interface KindRule {
  manual: boolean;
  fields: ResolutionField[];
}

const KIND_RULES: Record<CorporateActionKind, KindRule> = {
  SPLIT: { manual: false, fields: ["ratio"] },
  REVERSE_SPLIT: { manual: false, fields: ["ratio"] },
  BONUS_SHARES: { manual: false, fields: ["ratio", "declared_value_per_share"] },
  TICKER_CHANGE: { manual: false, fields: ["target_ticker"] },
  CAPITAL_RETURN: { manual: false, fields: ["declared_value_per_share"] },
  AMORTIZATION: { manual: false, fields: ["declared_value_per_share"] },
  CASH_BONUS: { manual: false, fields: ["declared_value_per_share"] },
  RIGHTS_ISSUE: { manual: true, fields: [] },
  RIGHTS_ASSIGNMENT: { manual: true, fields: [] },
  RIGHTS_ALLOTMENT: { manual: false, fields: ["ratio", "target_ticker"] },
  INCORPORATION: { manual: true, fields: [] },
  MERGER: { manual: true, fields: [] },
  SPIN_OFF: { manual: true, fields: [] },
  OTHER: { manual: true, fields: [] },
};

export function isManualReview(action: CorporateAction): boolean {
  return KIND_RULES[action.kind].manual;
}

export function resolutionFields(action: CorporateAction): ResolutionField[] {
  return KIND_RULES[action.kind].fields;
}

// A parked column is unusable when absent or non-positive (the engine's own test is "positive").
function isNonPositive(value: number | undefined): boolean {
  return value === undefined || value <= 0;
}

// A ticker change carrying its new code was parked because the source ticker is absent from the
// catalog; the row has to point at the repair before its resolve can land.
export function catalogBlocked(action: CorporateAction): boolean {
  return action.kind === "TICKER_CHANGE" && action.target_ticker !== undefined;
}

// Why the engine parked the event, in the owner's words — enough context to decide what to supply.
export function reviewReason(action: CorporateAction): string {
  const rule = KIND_RULES[action.kind];
  if (rule.manual) {
    return `Sem efeito automático: o motor não aplica ${corporateActionKindLabel(action.kind)} sozinho.`;
  }
  if (action.kind === "TICKER_CHANGE") {
    return action.target_ticker
      ? "O ticker de origem não está no catálogo; atualize o catálogo em Ativos · Posições e aplique."
      : "A exportação não trouxe o novo código.";
  }
  const factorMissing = rule.fields.includes("ratio") && isNonPositive(action.ratio);
  const valueMissing =
    rule.fields.includes("declared_value_per_share") &&
    isNonPositive(action.declared_value_per_share);
  if (factorMissing && valueMissing) return "A exportação não trouxe o fator e o valor por ação.";
  if (factorMissing) return "A exportação não trouxe o fator.";
  if (valueMissing) return "A exportação não trouxe o valor por ação.";
  return "A exportação não trouxe os dados do evento.";
}

export type ResolutionDraft = Partial<Record<ResolutionField, string>>;

// The form opens with whatever the export already brought, so only the missing column is typed — a
// bonus shares row that has its factor keeps it while the owner supplies the per-share value.
export function initialDraft(action: CorporateAction): ResolutionDraft {
  const draft: ResolutionDraft = {};
  if (action.ratio !== undefined) draft.ratio = String(action.ratio);
  if (action.declared_value_per_share !== undefined) {
    draft.declared_value_per_share = String(action.declared_value_per_share);
  }
  if (action.target_ticker !== undefined) draft.target_ticker = action.target_ticker;
  return draft;
}

// A Brazilian decimal comma is the owner's natural input; the wire body is a plain number.
function parseAmount(raw: string | undefined): number {
  const value = (raw ?? "").trim().replace(",", ".");
  return value === "" ? Number.NaN : Number(value);
}

export function resolutionReady(action: CorporateAction, draft: ResolutionDraft): boolean {
  return resolutionFields(action).every((field) => {
    if (field === "target_ticker") return (draft[field] ?? "").trim().length > 0;
    const amount = parseAmount(draft[field]);
    return Number.isFinite(amount) && amount > 0;
  });
}

export function resolutionPayload(
  action: CorporateAction,
  draft: ResolutionDraft,
): CorporateActionResolution {
  const resolution: CorporateActionResolution = {};
  for (const field of resolutionFields(action)) {
    if (field === "target_ticker") {
      const ticker = (draft.target_ticker ?? "").trim();
      if (ticker !== "") resolution.target_ticker = ticker.toUpperCase();
      continue;
    }
    const amount = parseAmount(draft[field]);
    if (Number.isFinite(amount)) resolution[field] = amount;
  }
  return resolution;
}
