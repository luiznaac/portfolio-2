import { describe, expect, it } from "vitest";
import type { ImportRow, ImportRowSituation } from "../api/types.ts";
import { summarizeSituations } from "./importSituations.ts";

function row(id: number, situation: ImportRowSituation): ImportRow {
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
  };
}

describe("summarizeSituations", () => {
  it("counts the four server situations", () => {
    const rows = [
      row(1, "new"),
      row(2, "new"),
      row(3, "duplicate_in_ledger"),
      row(4, "duplicate_in_file"),
      row(5, "error"),
    ];

    expect(summarizeSituations(rows)).toEqual({
      new: 2,
      duplicate_in_file: 1,
      duplicate_in_ledger: 1,
      error: 1,
    });
  });

  it("returns zeros for an import with no rows", () => {
    expect(summarizeSituations([])).toEqual({
      new: 0,
      duplicate_in_file: 0,
      duplicate_in_ledger: 0,
      error: 0,
    });
  });
});
