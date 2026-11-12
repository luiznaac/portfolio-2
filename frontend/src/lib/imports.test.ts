import { describe, expect, it } from "vitest";
import type { Import, ImportStatus } from "../api/types.ts";
import {
  IMPORT_SOURCES,
  IMPORT_STATUSES,
  importAge,
  importSourceLabel,
  importStatusLabel,
  isParkedImport,
  newestFirst,
  parkedImports,
} from "./imports.ts";

function record(overrides: Partial<Import>): Import {
  return {
    id: 1,
    source: "B3_TRADES",
    file_name: "neg.xlsx",
    file_hash: "hash",
    status: "STAGED",
    destination: { object_type: "TRADES" },
    has_original: true,
    created_at: "2026-11-01T09:10:00",
    ...overrides,
  };
}

describe("import labels", () => {
  it("names every source and state", () => {
    expect(importSourceLabel("B3_TRADES")).toBe("B3 · Negócios");
    expect(importSourceLabel("B3_POSITION")).toBe("B3 · Custódia");
    expect(importSourceLabel("B3_MOVEMENTS")).toBe("B3 · Movimentações");
    expect(importSourceLabel("XP_REPORT")).toBe("XP · Relatório");
    expect(importStatusLabel("STAGED")).toBe("Aguardando revisão");
    expect(importStatusLabel("REVIEWED")).toBe("Revisada");
    expect(importStatusLabel("APPLIED")).toBe("Aplicada");
    expect(importStatusLabel("FAILED")).toBe("Falhou");
  });

  it("declares the sources and states in reading order", () => {
    expect(IMPORT_SOURCES).toEqual(["B3_TRADES", "B3_POSITION", "B3_MOVEMENTS", "XP_REPORT"]);
    expect(IMPORT_STATUSES).toEqual(["STAGED", "REVIEWED", "APPLIED", "FAILED"]);
  });
});

describe("parked sessions", () => {
  it("treats only a staged or reviewed import as a session", () => {
    const statuses: ImportStatus[] = ["STAGED", "REVIEWED", "APPLIED", "FAILED"];

    expect(statuses.map((status) => isParkedImport(record({ status })))).toEqual([
      true,
      true,
      false,
      false,
    ]);
  });

  it("filters the parked imports out of the log without mutating it", () => {
    const rows = [
      record({ id: 1, status: "STAGED" }),
      record({ id: 2, status: "APPLIED" }),
      record({ id: 3, status: "REVIEWED" }),
      record({ id: 4, status: "FAILED" }),
    ];

    expect(parkedImports(rows).map((row) => row.id)).toEqual([1, 3]);
    expect(rows).toHaveLength(4);
  });
});

describe("newestFirst", () => {
  it("sorts by the creation stamp, newest first, without mutating the input", () => {
    const rows = [
      record({ id: 1, created_at: "2026-10-01T09:00:00" }),
      record({ id: 2, created_at: "2026-11-01T09:00:00" }),
      record({ id: 3, created_at: "2026-10-15T09:00:00" }),
    ];

    expect(newestFirst(rows).map((row) => row.id)).toEqual([2, 3, 1]);
    expect(rows.map((row) => row.id)).toEqual([1, 2, 3]);
  });
});

describe("importAge", () => {
  const now = new Date(2026, 10, 12, 12, 0, 0);

  it("states minutes, hours and days relative to the given clock", () => {
    expect(importAge("2026-11-12T11:59:30", now)).toBe("agora");
    expect(importAge("2026-11-12T11:30:00", now)).toBe("há 30 min");
    expect(importAge("2026-11-12T09:00:00", now)).toBe("há 3 h");
    expect(importAge("2026-11-10T12:00:00", now)).toBe("há 2 d");
  });

  it("falls back to the date once the age passes a month", () => {
    expect(importAge("2026-10-01T09:00:00", now)).toBe("01/10/2026");
  });

  it("returns the raw value when it is not a timestamp", () => {
    expect(importAge("not-a-date", now)).toBe("not-a-date");
  });
});
