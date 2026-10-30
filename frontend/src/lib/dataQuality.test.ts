import { describe, expect, it } from "vitest";
import type { Allocation } from "../api/types.ts";
import { dataQualityCounts, dataQualitySummary } from "./dataQuality.ts";

const allocation = {
  unclassified: {
    actual: 100,
    tickers: [{ ticker: "ZZZZ9", actual: 100, quantity: 10 }],
  },
  unpriced: [
    { ticker: "VALE3", quantity: 5 },
    { ticker: "PETR4", quantity: 10 },
  ],
  negative: [{ ticker: "BCFF11", quantity: -10 }],
} as unknown as Allocation;

describe("data quality", () => {
  it("counts the unpriced, unclassified and negative rows independently", () => {
    expect(dataQualityCounts(allocation)).toEqual({
      unpriced: 2,
      unclassified: 1,
      negative: 1,
      total: 4,
    });
  });

  it("treats a missing allocation as an empty book rather than an error", () => {
    expect(dataQualityCounts(null)).toEqual({
      unpriced: 0,
      unclassified: 0,
      negative: 0,
      total: 0,
    });
  });

  it("summarises the three counts in one line", () => {
    expect(dataQualitySummary(dataQualityCounts(allocation))).toBe(
      "2 sem cotação · 1 sem classe · 1 negativa",
    );
  });
});
