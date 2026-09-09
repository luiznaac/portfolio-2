// Translates the backend's AssetKind enum (English constants) to pt-BR for display.
import type { AssetKind } from "../api/types.ts";

const LABELS_PT: Record<AssetKind, string> = {
  STOCK: "Ação",
  FII: "FII",
  ETF: "ETF",
  BDR: "BDR",
};

export function assetKindLabel(value: AssetKind): string {
  return LABELS_PT[value];
}
