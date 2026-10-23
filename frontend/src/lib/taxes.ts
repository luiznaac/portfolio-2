// Pure Impostos helpers — no React imports here (see AGENTS.md). The tax ledger labels, the DARF
// deadline formatting, the meter's progress and the editable harvest-pair form live here so their
// rules are unit-tested rather than buried in the page.

import type {
  ExemptionMeter,
  HarvestPairCreation,
  HarvestProposal,
  RealizedKind,
  RealizedResult,
  StockTaxReport,
} from "../api/types.ts";
import { formatIsoDate, isWeekend } from "./format.ts";

const REALIZED_KIND_LABELS: Record<RealizedKind, string> = {
  SWING: "Swing",
  DAY_TRADE: "Day trade",
  FII: "FIIs",
};

export function realizedKindLabel(kind: RealizedKind): string {
  return REALIZED_KIND_LABELS[kind];
}

export interface RealizedRow {
  kind: RealizedKind;
  kindLabel: string;
  grossSales: number;
  gains: number;
  losses: number;
  result: number;
}

// Three rows, in the server's enum order — the ledger shows a kind with no movement as zero rather
// than hiding it, so the month's whole tax picture stays comparable.
export function realizedRows(realized: RealizedResult[]): RealizedRow[] {
  return realized.map((row) => ({
    kind: row.kind,
    kindLabel: realizedKindLabel(row.kind),
    grossSales: row.gross_sales,
    gains: row.gains,
    losses: row.losses,
    result: row.result,
  }));
}

// The DARF 6015 deadline the month's tax is due by (the last business day of the following month).
export function darfDeadlineLabel(tax: StockTaxReport): string {
  return formatIsoDate(tax.darf_due_date);
}

// The meter's fill, capped at 100%: a month over the R$20k cliff is already lost, not "overfull".
export function meterProgress(meter: ExemptionMeter): number {
  if (meter.exemption_limit <= 0) return 100;
  return Math.min(100, (meter.gross_sales / meter.exemption_limit) * 100);
}

export function meterLabel(meter: ExemptionMeter): string {
  return meter.exceeded ? "Teto estourado" : "Dentro da isenção";
}

export interface HarvestPairFormState {
  sellDate: string;
  sellQuantity: string;
  sellPrice: string;
  rebuyDate: string;
  rebuyQuantity: string;
  rebuyPrice: string;
}

// Seeds the editable pair from the engine's suggestion: the whole suggested pair is the default, and
// the owner can change either leg before accepting.
export function harvestPairForm(proposal: HarvestProposal): HarvestPairFormState {
  return {
    sellDate: proposal.sell.date,
    sellQuantity: String(proposal.sell.quantity),
    sellPrice: String(proposal.sell.price),
    rebuyDate: proposal.rebuy.date,
    rebuyQuantity: String(proposal.rebuy.quantity),
    rebuyPrice: String(proposal.rebuy.price),
  };
}

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

function positive(value: string): number | null {
  const parsed = Number(value.trim());
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
}

// Null while the edited pair is incomplete or invalid, so the page keeps accept disabled. The
// backend repeats these checks — this is the fast feedback, not the guard.
export function toHarvestPairCreation(
  proposal: HarvestProposal,
  form: HarvestPairFormState,
): HarvestPairCreation | null {
  if (!ISO_DATE.test(form.sellDate) || !ISO_DATE.test(form.rebuyDate)) return null;
  if (form.rebuyDate <= form.sellDate) return null;
  if (isWeekend(form.sellDate) || isWeekend(form.rebuyDate)) return null;

  const sellQuantity = positive(form.sellQuantity);
  const sellPrice = positive(form.sellPrice);
  const rebuyQuantity = positive(form.rebuyQuantity);
  const rebuyPrice = positive(form.rebuyPrice);
  if (
    sellQuantity === null ||
    sellPrice === null ||
    rebuyQuantity === null ||
    rebuyPrice === null ||
    sellQuantity < 1 ||
    rebuyQuantity < 1
  ) {
    return null;
  }

  return {
    ticker: proposal.ticker,
    strategy_id: proposal.strategy_id,
    sell_date: form.sellDate,
    sell_quantity: sellQuantity,
    sell_price: sellPrice,
    rebuy_date: form.rebuyDate,
    rebuy_quantity: rebuyQuantity,
    rebuy_price: rebuyPrice,
  };
}
