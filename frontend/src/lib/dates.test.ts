import { describe, expect, it } from "vitest";
import { addDays, daysBetween, monthKey, previousMonthKey, todayIso } from "./dates.ts";

describe("todayIso", () => {
  it("renders the local calendar day, not the UTC one", () => {
    expect(todayIso(new Date(2026, 9, 24, 23, 30))).toBe("2026-10-24");
  });

  it("zero-pads month and day", () => {
    expect(todayIso(new Date(2026, 0, 5, 1, 0))).toBe("2026-01-05");
  });
});

describe("monthKey", () => {
  it("takes the year and month off an ISO date", () => {
    expect(monthKey("2026-10-24")).toBe("2026-10");
  });

  it("returns the input when it is not an ISO date", () => {
    expect(monthKey("outubro")).toBe("outubro");
  });
});

describe("addDays", () => {
  it("walks forward across a month boundary", () => {
    expect(addDays("2026-10-28", 7)).toBe("2026-11-04");
  });

  it("walks backward across a year boundary", () => {
    expect(addDays("2026-01-02", -3)).toBe("2025-12-30");
  });

  it("returns the same day for zero", () => {
    expect(addDays("2026-10-24", 0)).toBe("2026-10-24");
  });
});

describe("daysBetween", () => {
  it("counts forward days", () => {
    expect(daysBetween("2026-10-24", "2026-10-31")).toBe(7);
  });

  it("is negative when the target is in the past", () => {
    expect(daysBetween("2026-10-24", "2026-10-20")).toBe(-4);
  });

  it("is zero for the same day", () => {
    expect(daysBetween("2026-10-24", "2026-10-24")).toBe(0);
  });
});

describe("previousMonthKey", () => {
  it("walks back one month", () => {
    expect(previousMonthKey("2026-10-24")).toBe("2026-09");
  });

  it("walks from January to the previous December", () => {
    expect(previousMonthKey("2026-01-01")).toBe("2025-12");
  });
});
