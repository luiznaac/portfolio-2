import { describe, expect, it } from "vitest";
import type { Quote } from "../api/types.ts";
import { latestQuote, positionValue } from "./position.ts";

function quote(date: string, close: number): Quote {
  return { ticker: "PETR4", date, close, source: "COTAHIST" };
}

describe("latestQuote", () => {
  it("returns undefined for an empty series", () => {
    expect(latestQuote([])).toBeUndefined();
  });

  it("returns the quote with the latest date, not the last element", () => {
    const quotes = [quote("2026-10-10", 38), quote("2026-10-14", 40), quote("2026-10-12", 39)];

    expect(latestQuote(quotes)?.date).toBe("2026-10-14");
    expect(latestQuote(quotes)?.close).toBe(40);
  });

  it("returns the only quote in a single-element series", () => {
    expect(latestQuote([quote("2026-10-14", 40)])?.close).toBe(40);
  });
});

describe("positionValue", () => {
  it("multiplies the quantity by the close", () => {
    expect(positionValue(100, 40)).toBe(4000);
  });

  it("is zero for an empty position", () => {
    expect(positionValue(0, 40)).toBe(0);
  });
});
