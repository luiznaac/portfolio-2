import type { ImportRow, ImportRowSituation } from "../api/types.ts";

// The review's counts, read straight from the server's per-row `situation`. The backend computes
// each value with the same natural tuple + occurrence walk apply uses, so the screen never
// re-derives a verdict from the row fields.
export type SituationCounts = Record<ImportRowSituation, number>;

export function summarizeSituations(rows: ImportRow[]): SituationCounts {
  const counts: SituationCounts = {
    new: 0,
    duplicate_in_file: 0,
    duplicate_in_ledger: 0,
    error: 0,
  };

  for (const row of rows) counts[row.situation] += 1;

  return counts;
}
