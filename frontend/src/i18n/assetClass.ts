// Translates the backend's AssetClass enum (user policy, not a product property) to pt-BR.
import type { AssetClass, FixedIncomeSubClass } from "../api/types.ts";

const CLASS_LABELS_PT: Record<AssetClass, string> = {
  STOCKS: "Ações",
  REAL_ESTATE: "Real State",
  FIXED_INCOME: "Renda Fixa",
  ALTERNATIVES: "Alternativos",
  CASH: "Caixa",
};

export const ASSET_CLASSES: AssetClass[] = [
  "STOCKS",
  "REAL_ESTATE",
  "FIXED_INCOME",
  "ALTERNATIVES",
  "CASH",
];

export function assetClassLabel(value: AssetClass): string {
  return CLASS_LABELS_PT[value];
}

const SUBCLASS_LABELS_PT: Record<FixedIncomeSubClass, string> = {
  FLOATING_RATE: "Pós-fixado",
  FIXED_RATE: "Pré-fixado",
  INFLATION_LINKED: "Inflação",
};

export const FIXED_INCOME_SUBCLASSES: FixedIncomeSubClass[] = [
  "FLOATING_RATE",
  "FIXED_RATE",
  "INFLATION_LINKED",
];

export function fixedIncomeSubClassLabel(value: FixedIncomeSubClass): string {
  return SUBCLASS_LABELS_PT[value];
}
