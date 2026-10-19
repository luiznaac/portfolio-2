import { describe, expect, it } from "vitest";
import type { AttributionSlice } from "../api/types.ts";
import { attributionPercent, remainingAttributable } from "./attribution.ts";

function slice(strategyId: number, quantity: number): AttributionSlice {
  return { strategy_id: strategyId, strategy_name: `Strategy ${strategyId}`, quantity };
}

describe("attributionPercent", () => {
  it("returns the slice's share of custody", () => {
    expect(attributionPercent(60, 100)).toBe(60);
  });

  it("is zero when custody is zero", () => {
    expect(attributionPercent(10, 0)).toBe(0);
  });
});

describe("remainingAttributable", () => {
  it("is the custody remainder for a new strategy", () => {
    expect(remainingAttributable(100, [slice(1, 60)])).toBe(40);
  });

  it("excludes the slice being replaced", () => {
    expect(remainingAttributable(100, [slice(1, 60), slice(2, 10)], 1)).toBe(90);
  });

  it("floors at zero when the position shrank below the attributed total", () => {
    expect(remainingAttributable(50, [slice(1, 60)])).toBe(0);
  });
});
