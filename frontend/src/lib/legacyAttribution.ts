// Pure helpers for the bulk legacy action — no React imports here (see AGENTS.md). The banner's
// visibility, the preview totals and the skip links are unit-tested in legacyAttribution.test.ts.

import type {
  Allocation,
  AssetClass,
  LegacyAttributionPreview,
  LegacyAttributionSkip,
  LegacyAttributionSkipReason,
} from "../api/types.ts";

// The custody the bulk action targets: the outside rows of every class. The unclassified line is a
// data-quality state, not outside custody, so it never counts here.
export function outsideCustody(allocation: Allocation): number {
  return allocation.classes.reduce((sum, cls) => sum + cls.outside.actual, 0);
}

// The page-level action appears while a class still holds outside Tickers. The ticker list, not its
// value: a Ticker with no stored close is worth zero but still has custody to attribute.
export function shouldShowBanner(allocation: Allocation): boolean {
  return allocation.classes.some((cls) => cls.outside.tickers.length > 0);
}

export function previewAddCount(preview: LegacyAttributionPreview): number {
  return preview.groups.reduce((total, group) => total + group.adds.length, 0);
}

export function previewTotalValue(preview: LegacyAttributionPreview): number {
  return preview.groups.reduce(
    (total, group) => total + group.adds.reduce((sum, add) => sum + add.value, 0),
    0,
  );
}

export function skipReasonLabel(reason: LegacyAttributionSkipReason): string {
  switch (reason) {
    case "UNCLASSIFIED":
      return "Sem classe";
    case "NO_BUCKET":
      return "Sem estratégia manual";
    case "HAS_ATTRIBUTION":
      return "Já tem atribuição";
    case "WRITE_FAILED":
      return "Falha ao atribuir";
  }
}

export interface SkipAction {
  to: string;
  label: string;
}

// A class without a bucket is one setup gap, not one per Ticker: the preview groups its outside
// Tickers under the class so the Estratégias link appears once.
export interface NoBucketSkip {
  assetClass: AssetClass | null | undefined;
  tickers: string[];
}

export function noBucketSkips(skips: LegacyAttributionSkip[]): NoBucketSkip[] {
  const groups = new Map<string, NoBucketSkip>();
  for (const skip of skips) {
    if (skip.reason !== "NO_BUCKET") continue;
    const key = skip.asset_class ?? "";
    const group = groups.get(key) ?? { assetClass: skip.asset_class, tickers: [] };
    group.tickers.push(skip.ticker);
    groups.set(key, group);
  }
  return [...groups.values()];
}

// The action each skip offers: a data fix (classify) or a setup step (create the bucket). A write
// failure has no destination — the server's message explains it inline.
export function skipAction(skip: LegacyAttributionSkip): SkipAction | undefined {
  switch (skip.reason) {
    case "UNCLASSIFIED":
      return { to: `/trades/${encodeURIComponent(skip.ticker)}`, label: "Classificar" };
    case "NO_BUCKET":
      return { to: "/strategies", label: "Criar estratégia" };
    case "HAS_ATTRIBUTION":
      return { to: `/trades/${encodeURIComponent(skip.ticker)}`, label: "Ver ativo" };
    case "WRITE_FAILED":
      return undefined;
  }
}
