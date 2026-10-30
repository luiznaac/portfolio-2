import { describe, expect, it } from "vitest";
import type { Import, MovementsImportResult } from "../api/types.ts";
import { movementsImportSummary } from "./movements.ts";

const importRecord: Import = {
  id: 3,
  source: "B3_MOVEMENTS",
  file_name: "movimentacao.xlsx",
  file_hash: "hash",
  status: "APPLIED",
  created_at: "2026-10-14T10:00:00",
};

function result(overrides: Partial<MovementsImportResult>): MovementsImportResult {
  return { trades: 0, incomes: 0, corporate_actions: [], reuploaded: false, ...overrides };
}

describe("movementsImportSummary", () => {
  it("names the import date when a re-upload added nothing", () => {
    const summary = movementsImportSummary(
      result({ reuploaded: true, trade_import: importRecord }),
    );

    expect(summary).toBe("Arquivo já importado em 14/10/2026 10:00 — nenhum lançamento novo.");
  });

  it("reports nothing new without a date when a fresh file absorbed nothing", () => {
    expect(movementsImportSummary(result({}))).toBe("Nenhum lançamento novo.");
  });

  it("lists the absorbed counts when the upload changed something", () => {
    const summary = movementsImportSummary(
      result({
        trades: 2,
        incomes: 1,
        corporate_actions: [
          {
            id: 1,
            ticker: "PETR4",
            kind: "SPLIT",
            ex_date: "2026-10-20",
            ordinal: 1,
            source: "B3_MOVEMENTS",
            status: "APPLIED",
            is_applied: true,
          },
        ],
      }),
    );

    expect(summary).toBe("2 negócio(s), 1 provento(s) e 1 evento(s) processado(s).");
  });
});
