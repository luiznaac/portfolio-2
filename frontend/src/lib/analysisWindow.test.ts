import { describe, expect, it } from "vitest";
import {
  addMonths,
  clampPeriod,
  defaultAnchor,
  shiftAnchor,
  windowDayRange,
  windowMonthKeys,
  windowMonths,
  windowRange,
  windowRangeLabel,
} from "./analysisWindow.ts";

describe("addMonths", () => {
  it("moves within a year and across a year boundary", () => {
    expect(addMonths("2026-10", 1)).toBe("2026-11");
    expect(addMonths("2026-01", -1)).toBe("2025-12");
    expect(addMonths("2026-03", -12)).toBe("2025-03");
    expect(addMonths("2026-03", 12)).toBe("2027-03");
  });
});

describe("windowRange", () => {
  it("ends every preset on the anchor month", () => {
    expect(windowRange("1m", "2026-10")).toEqual({ start: "2026-10", end: "2026-10" });
    expect(windowRange("3m", "2026-10")).toEqual({ start: "2026-08", end: "2026-10" });
    expect(windowRange("6m", "2026-10")).toEqual({ start: "2026-05", end: "2026-10" });
    expect(windowRange("12m", "2026-10")).toEqual({ start: "2025-11", end: "2026-10" });
  });

  it("opens the year preset on January of the anchor year", () => {
    expect(windowRange("year", "2026-10")).toEqual({ start: "2026-01", end: "2026-10" });
  });

  it("crosses the year boundary for a short anchor", () => {
    expect(windowRange("12m", "2026-01")).toEqual({ start: "2025-02", end: "2026-01" });
  });
});

describe("defaultAnchor", () => {
  it("prefers the last closed month when it sits inside the available span", () => {
    expect(defaultAnchor(["2025-01", "2025-02", "2026-01"], "2025-02")).toBe("2025-02");
  });

  it("falls back to the newest available month when there is no close", () => {
    expect(defaultAnchor(["2025-01", "2025-02"])).toBe("2025-02");
    expect(defaultAnchor(["2025-01"], "2024-12")).toBe("2025-01");
  });

  it("has no anchor when no month is available", () => {
    expect(defaultAnchor([])).toBeUndefined();
  });
});

describe("clampPeriod and shiftAnchor", () => {
  const bounds = { first: "2025-01", last: "2026-10" };

  it("clamps an anchor to the available span", () => {
    expect(clampPeriod("2024-06", bounds)).toBe("2025-01");
    expect(clampPeriod("2027-03", bounds)).toBe("2026-10");
    expect(clampPeriod("2026-05", bounds)).toBe("2026-05");
  });

  it("shifts one month at a time and stops at the span edges", () => {
    expect(shiftAnchor("2026-05", -1, bounds)).toBe("2026-04");
    expect(shiftAnchor("2026-05", 1, bounds)).toBe("2026-06");
    expect(shiftAnchor("2025-01", -1, bounds)).toBe("2025-01");
    expect(shiftAnchor("2026-10", 1, bounds)).toBe("2026-10");
  });
});

describe("windowRangeLabel", () => {
  it("renders both window edges with the shared month labels", () => {
    expect(windowRangeLabel({ start: "2025-03", end: "2026-02" })).toBe("mar/2025 – fev/2026");
  });
});

describe("windowMonthKeys", () => {
  it("lists every month of the window, both edges included", () => {
    expect(windowMonthKeys({ start: "2025-11", end: "2026-02" })).toEqual([
      "2025-11",
      "2025-12",
      "2026-01",
      "2026-02",
    ]);
  });

  it("lists a single month for a one-month window", () => {
    expect(windowMonthKeys({ start: "2026-10", end: "2026-10" })).toEqual(["2026-10"]);
  });
});

describe("windowDayRange", () => {
  it("turns the month window into inclusive ISO day bounds", () => {
    expect(windowDayRange({ start: "2026-01", end: "2026-03" })).toEqual({
      from: "2026-01-01",
      to: "2026-03-31",
    });
  });

  it("ends February on the leap year's 29th", () => {
    expect(windowDayRange({ start: "2024-02", end: "2024-02" })).toEqual({
      from: "2024-02-01",
      to: "2024-02-29",
    });
  });
});

describe("windowMonths", () => {
  it("keeps the months inside the range, both edges included", () => {
    const months = [{ period: "2025-10" }, { period: "2025-11" }, { period: "2026-02" }];
    expect(windowMonths(months, { start: "2025-11", end: "2026-01" })).toEqual([
      { period: "2025-11" },
    ]);
  });
});
