import { describe, expect, it } from "vitest";
import type { ImportRow } from "../api/types.ts";
import { deriveStagedRows, summarizeSituations } from "./importSituations.ts";

function row(overrides: Partial<ImportRow>): ImportRow {
  return {
    id: 1,
    import_id: 1,
    date: "2026-10-14",
    side: "BUY",
    venue: "VISTA",
    ticker: "PETR4",
    quantity: 100,
    unit_price: 48.92,
    institution_name: "XP INVESTIMENTOS",
    ...overrides,
  };
}

describe("deriveStagedRows", () => {
  it("classifies a row with an error as a warning", () => {
    const staged = deriveStagedRows([row({ error: "data inválida" })]);

    expect(staged.map((s) => s.situation)).toEqual(["warning"]);
  });

  it("classifies the first of two identical in-file rows as new and the second as duplicate", () => {
    const staged = deriveStagedRows([row({ id: 1 }), row({ id: 2 })]);

    expect(staged.map((s) => s.situation)).toEqual(["new", "duplicate"]);
  });

  it("keeps same-tuple rows at different institutions as new", () => {
    const staged = deriveStagedRows([
      row({ id: 1, institution_name: "XP INVESTIMENTOS" }),
      row({ id: 2, institution_name: "CLEAR CORRETORA" }),
    ]);

    expect(staged.map((s) => s.situation)).toEqual(["new", "new"]);
  });

  it("classifies a row differing from the others as new", () => {
    const staged = deriveStagedRows([row({ id: 1 }), row({ id: 2, ticker: "VALE3" })]);

    expect(staged.map((s) => s.situation)).toEqual(["new", "new"]);
  });
});

describe("summarizeSituations", () => {
  it("counts rows per situation", () => {
    const staged = deriveStagedRows([
      row({ id: 1 }),
      row({ id: 2 }),
      row({ id: 3, error: "sem data" }),
      row({ id: 4, ticker: "VALE3" }),
    ]);

    expect(summarizeSituations(staged)).toEqual({ new: 2, duplicate: 1, warning: 1 });
  });
});
