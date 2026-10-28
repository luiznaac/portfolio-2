import type { ImportRow } from "../api/types.ts";

// The review hint shown per staged row. `warning` is a row-level problem the parser flagged; a
// `duplicate` repeats an earlier row in the same file; `new` is everything else.
//
// This is a pre-apply hint, not the dedup verdict: the ledger-level dedup (natural tuple +
// occurrence ordinal + file hash, institution included) is the backend's job at apply (#153). The
// client can reproduce the full tuple — including the institution name, which a staged row carries —
// within the file it just uploaded, but it cannot match a staged row against the ledger faithfully,
// because a `Trade` carries an `institution_id` and the name-to-id normalization lives on the
// backend.
export type ImportRowSituation = "new" | "duplicate" | "warning";

export interface StagedRow {
  row: ImportRow;
  situation: ImportRowSituation;
}

export type SituationCounts = Record<ImportRowSituation, number>;

function rowKey(row: ImportRow): string {
  return [
    row.date ?? "",
    row.side,
    row.venue,
    row.ticker.toUpperCase(),
    row.quantity,
    row.unit_price,
    row.institution_name.toUpperCase(),
  ].join("\u0001");
}

export function deriveStagedRows(rows: ImportRow[]): StagedRow[] {
  const seen = new Set<string>();

  return rows.map((row) => {
    if (row.error) return { row, situation: "warning" };

    const key = rowKey(row);
    const situation: ImportRowSituation = seen.has(key) ? "duplicate" : "new";
    seen.add(key);
    return { row, situation };
  });
}

export function summarizeSituations(staged: StagedRow[]): SituationCounts {
  const counts: SituationCounts = { new: 0, duplicate: 0, warning: 0 };
  for (const { situation } of staged) counts[situation] += 1;
  return counts;
}
