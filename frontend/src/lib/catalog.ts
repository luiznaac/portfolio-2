// Pure catalog helpers — no React imports here (see AGENTS.md). The classification reads the
// Alocação and Ativos screens need live here so the override-vs-BDI rule and its copy are
// unit-tested without rendering.

import type { AssetClass, CatalogEntry } from "../api/types.ts";

// A listed ticker is classified as one of the two class rungs; Renda Fixa is the consolidation
// engine's domain and is never a manual classification.
export type ClassifiableAssetClass = "ACOES" | "FIIS";

export const CLASSIFY_CLASSES: ClassifiableAssetClass[] = ["ACOES", "FIIS"];

const PICKER_LABELS: Record<ClassifiableAssetClass, string> = {
  ACOES: "Ações",
  FIIS: "FIIs",
};

// The short nouns the classification copy uses ("Ações", "FII") — distinct from the allocation
// screen's full label ("Fundos imob."), which would read wrong in "o catálogo agora diz …".
const NOUN_LABELS: Record<AssetClass, string> = {
  ACOES: "Ações",
  FIIS: "FII",
  RENDA_FIXA: "Renda fixa",
};

export function classifyLabel(assetClass: ClassifiableAssetClass): string {
  return PICKER_LABELS[assetClass];
}

// The class every screen plans and attributes with: the owner's manual classification outranks
// the BDI-derived one, and a missing row is unclassified rather than guessed.
export function effectiveAssetClass(entry: CatalogEntry | undefined): AssetClass | null {
  return entry?.asset_class_override ?? entry?.asset_class ?? null;
}

// The asset detail's manual-class line, present whenever an override exists: the owner's call
// alone, or beside what the catalog now believes when it disagrees. The override keeps winning in
// both cases, so the line always travels with the revert action.
export function classificationNote(entry: CatalogEntry): string | null {
  const override = entry.asset_class_override;
  if (override === undefined) return null;

  const derived = entry.asset_class;
  if (derived === undefined || derived === override) {
    return `classe manual: ${NOUN_LABELS[override]}`;
  }
  return `classe manual: ${NOUN_LABELS[override]} — o catálogo agora diz ${NOUN_LABELS[derived]}`;
}
