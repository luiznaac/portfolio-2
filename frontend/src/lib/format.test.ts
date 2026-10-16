import { describe, expect, it } from "vitest";
import { formatHealthStatus, formatNumber, formatTradeSide } from "./format.ts";

describe("formatHealthStatus", () => {
  it("formats a healthy result", () => {
    expect(formatHealthStatus(true)).toBe("healthy");
  });

  it("formats an unhealthy result", () => {
    expect(formatHealthStatus(false)).toBe("unhealthy");
  });
});

describe("formatNumber", () => {
  it("formats an integer", () => {
    expect(formatNumber(100)).toBe("100");
  });

  it("groups thousands in pt-BR", () => {
    expect(formatNumber(1500)).toBe("1.500");
  });
});

describe("formatTradeSide", () => {
  it("labels a buy", () => {
    expect(formatTradeSide("BUY")).toBe("Compra");
  });

  it("labels a sell", () => {
    expect(formatTradeSide("SELL")).toBe("Venda");
  });
});
