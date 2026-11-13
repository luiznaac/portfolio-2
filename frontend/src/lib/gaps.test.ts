import { describe, expect, it } from "vitest";
import type { Gap } from "../api/types.ts";
import { gapRows, isMissingBuy, openGaps, pendingProceedsGaps } from "./gaps.ts";

function gap(overrides: Partial<Gap> & Pick<Gap, "id" | "status">): Gap {
  return {
    ticker: "PETR4",
    date: "2026-01-10",
    quantity: 10,
    trade_watermark: 0,
    action_watermark: 0,
    ...overrides,
  };
}

describe("gaps", () => {
  const gaps = [
    gap({ id: 1, status: "OPEN", unit_price: 12 }),
    gap({ id: 2, status: "OPEN", realized_pending: true }),
    gap({ id: 3, status: "REPLACED", realized_pending: false }),
    gap({ id: 4, status: "OPEN", realized_pending: false, unit_price: 18 }),
  ];

  it("keeps only the open gaps", () => {
    expect(openGaps(gaps).map((item) => item.id)).toEqual([1, 2, 4]);
  });

  it("flags only an open missing sale with no proceeds as pending", () => {
    expect(pendingProceedsGaps(gaps).map((item) => item.id)).toEqual([2]);
  });

  it("tells a missing buy from a missing sale by the realized flag", () => {
    expect(isMissingBuy(gaps[0])).toBe(true);
    expect(isMissingBuy(gaps[2])).toBe(false);
  });
});

describe("gapRows", () => {
  it("lists every gap, open first, newest first inside each status", () => {
    const rows = gapRows([
      gap({ id: 1, status: "REPLACED", date: "2026-10-02" }),
      gap({ id: 2, status: "OPEN", date: "2026-09-18" }),
      gap({ id: 3, status: "OPEN", date: "2026-10-16" }),
      gap({ id: 4, status: "REPLACED", date: "2026-09-30" }),
    ]);

    expect(rows.map((row) => row.id)).toEqual([3, 2, 1, 4]);
  });

  it("names the missing-buy and pending-proceeds variants", () => {
    const rows = gapRows([
      gap({ id: 1, status: "OPEN", unit_price: 9 }),
      gap({ id: 2, status: "OPEN", realized_pending: true }),
      gap({ id: 3, status: "OPEN", realized_pending: false, unit_price: 18 }),
    ]);
    const byId = new Map(rows.map((row) => [row.id, row]));

    expect(byId.get(1)?.missingBuy).toBe(true);
    expect(byId.get(1)?.pending).toBe(false);
    expect(byId.get(2)?.missingBuy).toBe(false);
    expect(byId.get(2)?.pending).toBe(true);
    expect(byId.get(3)?.pending).toBe(false);
  });
});
