import { describe, expect, it } from "vitest";
import type { ImportRow, ImportRowOutcome, ImportRowSituation } from "../api/types.ts";
import { rejectedBatch, summarizeOutcomes } from "./importSituations.ts";

function row(id: number, situation: ImportRowSituation, outcome?: ImportRowOutcome): ImportRow {
  return {
    id,
    import_id: 1,
    date: "2026-10-14",
    side: "BUY",
    venue: "VISTA",
    ticker: "PETR4",
    quantity: 100,
    unit_price: 48.92,
    institution_name: "XP INVESTIMENTOS",
    situation,
    outcome,
  };
}

describe("import row outcomes", () => {
  it("counts the apply-time outcomes the server persisted", () => {
    const rows = [
      row(1, "duplicate_in_ledger", "skipped_duplicate"),
      row(2, "duplicate_in_ledger", "applied"),
      row(3, "duplicate_in_ledger", "applied"),
      row(4, "error", "rejected"),
    ];

    expect(summarizeOutcomes(rows)).toEqual({
      applied: 2,
      rejected: 1,
      skipped_duplicate: 1,
    });
  });

  it("treats a review-time error row as the retry batch before apply runs", () => {
    const rows = [row(1, "new"), row(2, "error")];

    expect(rejectedBatch(rows).map((entry) => entry.id)).toEqual([2]);
  });

  it("keeps exactly the rows apply rejected as the retry batch afterwards", () => {
    const rows = [
      row(1, "duplicate_in_ledger", "applied"),
      row(2, "duplicate_in_ledger", "skipped_duplicate"),
      row(3, "error", "rejected"),
    ];

    expect(rejectedBatch(rows).map((entry) => entry.id)).toEqual([3]);
  });
});
