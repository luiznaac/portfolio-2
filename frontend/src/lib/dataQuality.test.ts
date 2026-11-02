import { describe, expect, it } from "vitest";
import type { Allocation, Gap } from "../api/types.ts";
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

const gaps = [
  {
    id: 1,
    ticker: "BCFF11",
    date: "2026-01-10",
    quantity: 10,
    status: "OPEN",
    trade_watermark: 0,
    action_watermark: 0,
  },
  {
    id: 2,
    ticker: "VALE3",
    date: "2026-01-10",
    quantity: 5,
    status: "OPEN",
    realized_pending: true,
    trade_watermark: 0,
    action_watermark: 0,
  },
  {
    id: 3,
    ticker: "ITUB4",
    date: "2026-01-10",
    quantity: 5,
    status: "REPLACED",
    realized_pending: false,
    trade_watermark: 0,
    action_watermark: 0,
  },
] as unknown as Gap[];

describe("data quality", () => {
  it("counts the unpriced, unclassified, negative and lacuna rows independently", () => {
    expect(dataQualityCounts(allocation, gaps)).toEqual({
      unpriced: 2,
      unclassified: 1,
      negative: 1,
      lacunas: 2,
      total: 6,
    });
  });

  it("treats a missing allocation as an empty book rather than an error", () => {
    expect(dataQualityCounts(null)).toEqual({
      unpriced: 0,
      unclassified: 0,
      negative: 0,
      lacunas: 0,
      total: 0,
    });
  });

  it("summarises the four counts in one line", () => {
    expect(dataQualitySummary(dataQualityCounts(allocation, gaps))).toBe(
      "2 sem cotação · 1 sem classe · 1 negativa · 2 lacunas",
    );
  });
});
