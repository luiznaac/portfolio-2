// Translates the backend's AssetClass enum (user policy, not a product property) to pt-BR.
import type { AssetClass, FixedIncomeSubClass } from "../api/types.ts";

const CLASS_LABELS_PT: Record<AssetClass, string> = {
  ACOES: "Ações",
  REAL_STATE: "Real State",
  RENDA_FIXA: "Renda Fixa",
  ALTERNATIVOS: "Alternativos",
  CAIXA: "Caixa",
};

export const ASSET_CLASSES: AssetClass[] = [
  "ACOES",
  "REAL_STATE",
  "RENDA_FIXA",
  "ALTERNATIVOS",
  "CAIXA",
];

export function assetClassLabel(value: AssetClass): string {
  return CLASS_LABELS_PT[value];
}

const SUBCLASS_LABELS_PT: Record<FixedIncomeSubClass, string> = {
  POS_FIXADO: "Pós-fixado",
  PRE_FIXADO: "Pré-fixado",
  INFLACAO: "Inflação",
};

export const FIXED_INCOME_SUBCLASSES: FixedIncomeSubClass[] = [
  "POS_FIXADO",
  "PRE_FIXADO",
  "INFLACAO",
];

export function fixedIncomeSubClassLabel(value: FixedIncomeSubClass): string {
  return SUBCLASS_LABELS_PT[value];
}
