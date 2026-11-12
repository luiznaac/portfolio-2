import type { ImportRow, ImportRowOutcome, ImportRowSituation } from "../api/types.ts";

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

// The apply-time counts the server reports and persists per row.
export type OutcomeCounts = Record<ImportRowOutcome, number>;

export function summarizeOutcomes(rows: ImportRow[]): OutcomeCounts {
  const counts: OutcomeCounts = {
    applied: 0,
    rejected: 0,
    skipped_duplicate: 0,
  };

  for (const row of rows) {
    if (row.outcome !== undefined) counts[row.outcome] += 1;
  }

  return counts;
}

// The rows that did not enter the ledger: a review-time error before apply runs, an applied
// import's persisted rejection afterwards. They are the retry batch, never a failed batch.
export function rejectedBatch(rows: ImportRow[]): ImportRow[] {
  return rows.filter(
    (row) => row.outcome === "rejected" || (row.outcome === undefined && row.situation === "error"),
  );
}
