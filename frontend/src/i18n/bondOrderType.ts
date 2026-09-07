// Translates the backend's BondOrderType enum (English constants) to pt-BR for
// display. Same pattern as shougong's i18n/partOfSpeech.ts.
import type { BondOrderType } from "../api/types.ts";

const LABELS_PT: Record<BondOrderType, string> = {
  BUY: "Compra",
  SELL: "Venda",
  FULL_REDEMPTION: "Resgate total",
  MATURITY: "Vencimento",
  DEPOSIT: "Depósito",
  WITHDRAWAL: "Saque",
  FULL_WITHDRAWAL: "Saque total",
};

export function bondOrderTypeLabel(value: BondOrderType): string {
  return LABELS_PT[value];
}
