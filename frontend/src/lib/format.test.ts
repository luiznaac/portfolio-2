import { describe, expect, it } from "vitest";
import {
  formatCurrency,
  formatHealthStatus,
  formatIsoDate,
  formatLocalDateTime,
  formatNumber,
  formatPercent,
  formatSignedPercent,
  formatTradeSide,
  isWeekend,
} from "./format.ts";

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

describe("formatCurrency", () => {
  it("renders a BRL amount with two decimals", () => {
    expect(formatCurrency(1234.5)).toBe("R$ 1.234,50");
  });

  it("renders zero", () => {
    expect(formatCurrency(0)).toBe("R$ 0,00");
  });
});

describe("formatIsoDate", () => {
  it("renders the ISO date without shifting it through UTC", () => {
    expect(formatIsoDate("2026-11-30")).toBe("30/11/2026");
    expect(formatIsoDate("2026-01-05")).toBe("05/01/2026");
    expect(formatIsoDate("not-a-date")).toBe("not-a-date");
  });
});

describe("formatLocalDateTime", () => {
  it("formats the backend's local date-time without a timezone shift", () => {
    expect(formatLocalDateTime("2026-10-31T18:00:00")).toBe("31/10/2026 18:00");
  });

  it("falls back to the raw value when the shape is unexpected", () => {
    expect(formatLocalDateTime("not-a-date")).toBe("not-a-date");
  });
});

describe("formatPercent", () => {
  it("renders a pt-BR percentage with two decimals", () => {
    expect(formatPercent(1.5)).toBe("1,50%");
  });

  it("renders an em dash when the value is absent", () => {
    expect(formatPercent(undefined)).toBe("—");
  });

  it("honours a custom precision", () => {
    expect(formatPercent(12.3456, 1)).toBe("12,3%");
  });
});

describe("formatSignedPercent", () => {
  it("marks a gain with a plus sign", () => {
    expect(formatSignedPercent(1.5)).toBe("+1,50%");
  });

  it("keeps the minus sign on a loss", () => {
    expect(formatSignedPercent(-0.75)).toBe("-0,75%");
  });

  it("leaves zero unsigned", () => {
    expect(formatSignedPercent(0)).toBe("0,00%");
  });
});

describe("isWeekend", () => {
  it("knows a weekend without a timezone shift", () => {
    expect(isWeekend("2026-10-24")).toBe(true);
    expect(isWeekend("2026-10-25")).toBe(true);
    expect(isWeekend("2026-10-23")).toBe(false);
    expect(isWeekend("not-a-date")).toBe(false);
  });
});
