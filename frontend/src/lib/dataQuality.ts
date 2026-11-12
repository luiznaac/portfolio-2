// Pure data-quality derivations for the "Dados da carteira" panel.
// Counts come from the allocation read and the GAP list, so a stale read never invents a number: a
// missing list is an empty one.

import type { Allocation, Gap } from "../api/types.ts";
import { openGaps } from "./history.ts";

export interface DataQualityCounts {
  unpriced: number;
  unclassified: number;
  negative: number;
  lacunas: number;
  total: number;
}

export function dataQualityCounts(
  allocation: Allocation | null,
  gaps: Gap[] = [],
): DataQualityCounts {
  const unpriced = allocation?.unpriced?.length ?? 0;
  const unclassified = allocation?.unclassified.tickers.length ?? 0;
  const negative = allocation?.negative?.length ?? 0;
  const lacunas = openGaps(gaps).length;
  return {
    unpriced,
    unclassified,
    negative,
    lacunas,
    total: unpriced + unclassified + negative + lacunas,
  };
}

export function dataQualitySummary(counts: DataQualityCounts): string {
  return [
    `${counts.unpriced} sem cotação`,
    `${counts.unclassified} sem classe`,
    `${counts.negative} ${counts.negative === 1 ? "negativa" : "negativas"}`,
    `${counts.lacunas} ${counts.lacunas === 1 ? "lacuna" : "lacunas"}`,
  ].join(" · ");
}
