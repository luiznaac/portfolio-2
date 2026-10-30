// Pure data-quality derivations for the "Dados da carteira" panel and the Cockpit's single card.
// Counts come from the allocation read, so a stale read never invents a number: a missing list is
// an empty one.

import type { Allocation } from "../api/types.ts";

export interface DataQualityCounts {
  unpriced: number;
  unclassified: number;
  negative: number;
  total: number;
}

export function dataQualityCounts(allocation: Allocation | null): DataQualityCounts {
  const unpriced = allocation?.unpriced?.length ?? 0;
  const unclassified = allocation?.unclassified.tickers.length ?? 0;
  const negative = allocation?.negative?.length ?? 0;
  return {
    unpriced,
    unclassified,
    negative,
    total: unpriced + unclassified + negative,
  };
}

export function dataQualitySummary(counts: DataQualityCounts): string {
  return [
    `${counts.unpriced} sem cotação`,
    `${counts.unclassified} sem classe`,
    `${counts.negative} ${counts.negative === 1 ? "negativa" : "negativas"}`,
  ].join(" · ");
}
