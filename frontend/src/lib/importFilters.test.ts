import { describe, expect, it } from "vitest";
import type { Import } from "../api/types.ts";
import { IMPORT_FILTER_FIELDS, filterImports, serverImportFilters } from "./importFilters.ts";

function record(overrides: Partial<Import>): Import {
  return {
    id: 1,
    source: "B3_TRADES",
    file_name: "negocios.xlsx",
    file_hash: "hash",
    status: "STAGED",
    destination: { object_type: "TRADES" },
    has_original: true,
    created_at: "2026-11-01T09:10:00",
    ...overrides,
  };
}

const rows = [
  record({ id: 1, file_name: "negocios.xlsx", source: "B3_TRADES", status: "STAGED" }),
  record({ id: 2, file_name: "posicao.xlsx", source: "B3_POSITION", status: "APPLIED" }),
  record({ id: 3, file_name: "negocios-outubro.xlsx", source: "B3_MOVEMENTS", status: "APPLIED" }),
  record({ id: 4, file_name: "relatorio.csv", source: "XP_REPORT", status: "FAILED" }),
];

describe("IMPORT_FILTER_FIELDS", () => {
  it("declares the one bar both import lists share: arquivo, fonte and estado", () => {
    expect(IMPORT_FILTER_FIELDS.map((field) => field.control.key)).toEqual([
      "file",
      "source",
      "status",
    ]);
    expect(IMPORT_FILTER_FIELDS.map((field) => field.control.label)).toEqual([
      "Arquivo",
      "Fonte",
      "Estado",
    ]);
  });
});

describe("serverImportFilters", () => {
  it("sends only the source and state the shared bar selected", () => {
    expect(serverImportFilters({ file: "neg", source: "B3_POSITION", status: "APPLIED" })).toEqual({
      source: "B3_POSITION",
      status: "APPLIED",
    });
  });

  it("drops the file text and every blank value", () => {
    expect(serverImportFilters({ file: "neg", source: "", status: "  " })).toEqual({});
  });
});

describe("filterImports", () => {
  it("narrows by the file name, the source and the state together", () => {
    expect(
      filterImports(rows, { file: "neg", source: "", status: "APPLIED" }).map((row) => row.id),
    ).toEqual([3]);
    expect(
      filterImports(rows, { file: "", source: "B3_POSITION", status: "" }).map((row) => row.id),
    ).toEqual([2]);
    expect(
      filterImports(rows, { file: "NEG", source: "", status: "" }).map((row) => row.id),
    ).toEqual([1, 3]);
  });

  it("keeps every row while the bar is blank", () => {
    expect(filterImports(rows, { file: "", source: "", status: "" })).toEqual(rows);
  });
});
