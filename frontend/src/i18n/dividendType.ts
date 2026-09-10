// Translates the backend's DividendType enum (English/mixed constants) to pt-BR for display.
import type { DividendType } from "../api/types.ts";

const LABELS_PT: Record<DividendType, string> = {
  DIVIDEND: "Dividendo",
  JCP: "JCP",
  FUND_INCOME: "Rendimento",
};

export function dividendTypeLabel(value: DividendType): string {
  return LABELS_PT[value];
}
