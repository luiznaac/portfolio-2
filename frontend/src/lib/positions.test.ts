import { describe, expect, it } from "vitest";
import type { Position } from "../api/types.ts";
import { emptyTotals, lastPosition, mergePositions, sumTotals } from "./positions.ts";

function pos(date: string, principal: number, income: number, taxes: number): Position {
  return { date, principal, yield: income, taxes };
}

describe("mergePositions", () => {
  it("returns an empty series for no input", () => {
    expect(mergePositions([])).toEqual([]);
    expect(mergePositions([[], []])).toEqual([]);
  });

  it("passes a single series through, sorted by date", () => {
    const merged = mergePositions([[pos("2026-03-01", 10, 1, 0), pos("2026-01-01", 5, 0, 0)]]);
    expect(merged.map((p) => p.date)).toEqual(["2026-01-01", "2026-03-01"]);
  });

  it("sums principal, yield and taxes for the same date", () => {
    const merged = mergePositions([
      [pos("2026-01-01", 100, 10, 1)],
      [pos("2026-01-01", 200, 20, 2)],
    ]);
    expect(merged).toEqual([pos("2026-01-01", 300, 30, 3)]);
  });

  it("keeps dates that only one series has", () => {
    const merged = mergePositions([[pos("2026-01-01", 1, 0, 0)], [pos("2026-02-01", 2, 0, 0)]]);
    expect(merged.map((p) => p.date)).toEqual(["2026-01-01", "2026-02-01"]);
  });

  it("does not mutate the input", () => {
    const input = [pos("2026-01-01", 100, 10, 1), pos("2026-01-01", 200, 20, 2)];
    const series = [[input[0]], [input[1]]];
    mergePositions(series);
    expect(input[0]).toEqual(pos("2026-01-01", 100, 10, 1));
    expect(series[0][0].principal).toBe(100);
  });
});

describe("lastPosition", () => {
  it("returns undefined for an empty series", () => {
    expect(lastPosition([])).toBeUndefined();
  });

  it("returns the final element as stored, not the latest by date", () => {
    const list = [pos("2026-03-01", 1, 0, 0), pos("2026-01-01", 2, 0, 0)];
    expect(lastPosition(list)?.date).toBe("2026-01-01");
  });
});

describe("sumTotals", () => {
  it("starts from zero for an empty input", () => {
    expect(sumTotals([])).toEqual(emptyTotals);
  });

  it("skips undefined entries", () => {
    expect(sumTotals([undefined, pos("2026-01-01", 5, 1, 0), undefined])).toEqual({
      principal: 5,
      yield: 1,
      taxes: 0,
    });
  });

  it("does not share the accumulator with emptyTotals", () => {
    const totals = sumTotals([pos("2026-01-01", 5, 1, 0)]);
    expect(totals).not.toBe(emptyTotals);
    expect(emptyTotals).toEqual({ principal: 0, yield: 0, taxes: 0 });
  });
});
