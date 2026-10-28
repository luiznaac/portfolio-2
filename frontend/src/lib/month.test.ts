import { describe, expect, it } from "vitest";
import type { Month, Targets } from "../api/types.ts";
import {
  MONTH_OPTIONS,
  type StrategyTargetRow,
  addStrategyRow,
  addTickerRow,
  assetClassLabel,
  defaultClosedMonthId,
  emptyMonthForm,
  emptyTargetsForm,
  monthFormFrom,
  monthPeriodLabel,
  monthStatusLabel,
  overplannedStrategyClasses,
  periodMonth,
  periodYear,
  periodYearOptions,
  remainingLegacyShares,
  removeStrategyRow,
  removeTickerRow,
  targetsFormFrom,
  toMonthCreation,
  toMonthUpdate,
  toTargets,
} from "./month.ts";

const month: Month = {
  id: 7,
  period: "2026-09",
  capital: 245000,
  floor: 20000,
  aportes: 0,
  status: "OPEN",
};

const storedTargets: Targets = {
  classes: [{ asset_class: "ACOES", percentage: 50 }],
  strategies: [{ asset_class: "ACOES", strategy: "Top Dividendos", percentage: 34 }],
  sub_classes: [{ sub_class: "FLOATING_RATE", percentage: 55 }],
  tickers: [{ strategy: "Top Dividendos", ticker: "VALE3", percentage: 12 }],
};

describe("month form", () => {
  it("starts empty", () => {
    expect(emptyMonthForm()).toEqual({ period: "", capital: "", floor: "" });
  });

  it("seeds the form from an open month", () => {
    expect(monthFormFrom(month)).toEqual({ period: "2026-09", capital: "245000", floor: "20000" });
  });

  it("builds a creation payload from a complete form", () => {
    expect(toMonthCreation({ period: "2026-09", capital: "245000", floor: "20000" })).toEqual({
      period: "2026-09",
      capital: 245000,
      floor: 20000,
    });
  });

  it("rejects a period that is not YYYY-MM", () => {
    expect(toMonthCreation({ period: "2026-13", capital: "1", floor: "0" })).toBeNull();
    expect(toMonthCreation({ period: "26-09", capital: "1", floor: "0" })).toBeNull();
    expect(toMonthCreation({ period: "", capital: "1", floor: "0" })).toBeNull();
  });

  it("rejects a blank or negative capital", () => {
    expect(toMonthCreation({ period: "2026-09", capital: "", floor: "0" })).toBeNull();
    expect(toMonthCreation({ period: "2026-09", capital: "-1", floor: "0" })).toBeNull();
  });

  it("rejects a negative floor but accepts an empty one as zero", () => {
    expect(toMonthCreation({ period: "2026-09", capital: "100", floor: "-1" })).toBeNull();
    expect(toMonthCreation({ period: "2026-09", capital: "100", floor: "" })).toEqual({
      period: "2026-09",
      capital: 100,
      floor: 0,
    });
  });

  it("builds an update payload without the period", () => {
    expect(toMonthUpdate({ period: "2026-09", capital: "250000", floor: "25000" })).toEqual({
      capital: 250000,
      floor: 25000,
    });
  });

  it("rejects an invalid update", () => {
    expect(toMonthUpdate({ period: "2026-09", capital: "abc", floor: "25000" })).toBeNull();
  });
});

// The opener's period is picked from concrete month/year options rather than a native
// `<input type="month">`: Firefox (and Safari) render that as a plain text box with no picker.
describe("month opener period", () => {
  it("defaults a blank form to the given period", () => {
    expect(emptyMonthForm("2026-10")).toEqual({ period: "2026-10", capital: "", floor: "" });
  });

  it("splits a period into its month and year parts", () => {
    expect(periodYear("2026-10")).toBe("2026");
    expect(periodMonth("2026-10")).toBe("10");
  });

  it("gives a blank part for a blank period", () => {
    expect(periodYear("")).toBe("");
    expect(periodMonth("")).toBe("");
  });

  it("lists the twelve months as two-digit option values", () => {
    expect(MONTH_OPTIONS).toHaveLength(12);
    expect(MONTH_OPTIONS[0]).toEqual({ value: "01", label: "jan" });
    expect(MONTH_OPTIONS[11]).toEqual({ value: "12", label: "dez" });
  });

  it("offers the years around today", () => {
    expect(periodYearOptions("2026-10", "2026-10-24")).toEqual([
      "2024",
      "2025",
      "2026",
      "2027",
      "2028",
    ]);
  });

  it("keeps a selected year outside today's window", () => {
    expect(periodYearOptions("2032-01", "2026-10-24")).toContain("2032");
  });
});

describe("defaultClosedMonthId", () => {
  it("selects the newest closed month the backend lists first", () => {
    const months: Month[] = [
      {
        id: 9,
        period: "2026-09",
        capital: 240_000,
        floor: 20_000,
        status: "CLOSED",
        aportes: 2_000,
      },
      {
        id: 8,
        period: "2026-08",
        capital: 236_000,
        floor: 18_000,
        status: "CLOSED",
        aportes: -1_000,
      },
    ];

    expect(defaultClosedMonthId(months)).toBe(9);
  });

  it("returns null when no month has been closed", () => {
    expect(defaultClosedMonthId([])).toBeNull();
  });
});

describe("month labels", () => {
  it("renders a period as month abbreviation and year", () => {
    expect(monthPeriodLabel("2026-09")).toBe("set/2026");
    expect(monthPeriodLabel("2026-01")).toBe("jan/2026");
  });

  it("falls back to the raw period when it is malformed", () => {
    expect(monthPeriodLabel("nope")).toBe("nope");
  });

  it("renders the status in pt-BR", () => {
    expect(monthStatusLabel("OPEN")).toBe("Aberto");
    expect(monthStatusLabel("CLOSED")).toBe("Fechado");
  });
});

describe("targets form", () => {
  it("seeds a row for every class and every RF sub-class, with no strategies or tickers", () => {
    const form = emptyTargetsForm();

    expect(form.classes.map((row) => row.assetClass)).toEqual(["ACOES", "FIIS", "RENDA_FIXA"]);
    expect(form.subClasses.map((row) => row.subClass)).toEqual([
      "FIXED_RATE",
      "FLOATING_RATE",
      "INFLATION_LINKED",
    ]);
    expect(form.strategies).toEqual([]);
    expect(form.tickers).toEqual([]);
    expect(form.classes.every((row) => row.percentage === "")).toBe(true);
  });

  it("parses filled rows and drops blank or nameless ones", () => {
    const form = {
      classes: [
        { assetClass: "ACOES" as const, percentage: "50" },
        { assetClass: "FIIS" as const, percentage: "" },
        { assetClass: "RENDA_FIXA" as const, percentage: "0" },
      ],
      strategies: [
        {
          id: "s1",
          assetClass: "ACOES" as const,
          strategy: "Top Dividendos",
          percentage: "34",
        },
        { id: "s2", assetClass: "ACOES" as const, strategy: "  ", percentage: "10" },
      ],
      subClasses: [{ subClass: "FLOATING_RATE" as const, percentage: "55" }],
      tickers: [
        { id: "t1", strategy: "Top Dividendos", ticker: "vale3", percentage: "12" },
        { id: "t2", strategy: "Top Dividendos", ticker: "", percentage: "5" },
      ],
    };

    expect(toTargets(form)).toEqual({
      classes: [
        { asset_class: "ACOES", percentage: 50 },
        { asset_class: "RENDA_FIXA", percentage: 0 },
      ],
      strategies: [{ asset_class: "ACOES", strategy: "Top Dividendos", percentage: 34 }],
      sub_classes: [{ sub_class: "FLOATING_RATE", percentage: 55 }],
      tickers: [{ strategy: "Top Dividendos", ticker: "VALE3", percentage: 12 }],
    });
  });

  it("fills the fixed rows from stored targets and keeps the strategies and tickers", () => {
    const form = targetsFormFrom(storedTargets);

    expect(form.classes).toEqual([
      { assetClass: "ACOES", percentage: "50" },
      { assetClass: "FIIS", percentage: "" },
      { assetClass: "RENDA_FIXA", percentage: "" },
    ]);
    expect(form.subClasses).toEqual([
      { subClass: "FIXED_RATE", percentage: "" },
      { subClass: "FLOATING_RATE", percentage: "55" },
      { subClass: "INFLATION_LINKED", percentage: "" },
    ]);
    expect(form.strategies).toEqual([
      { id: expect.any(String), assetClass: "ACOES", strategy: "Top Dividendos", percentage: "34" },
    ]);
    expect(form.tickers).toEqual([
      { id: expect.any(String), strategy: "Top Dividendos", ticker: "VALE3", percentage: "12" },
    ]);
  });
});

describe("assetClassLabel", () => {
  it("renders each class in pt-BR", () => {
    expect(assetClassLabel("ACOES")).toBe("Ações");
    expect(assetClassLabel("FIIS")).toBe("Fundos imob.");
    expect(assetClassLabel("RENDA_FIXA")).toBe("Renda fixa");
  });
});

describe("targets form rows", () => {
  const strategyRow = (strategy: string, id = strategy) => ({
    id,
    assetClass: "ACOES" as const,
    strategy,
    percentage: "",
  });

  it("appends a blank strategy row", () => {
    const [row] = addStrategyRow(emptyTargetsForm()).strategies;

    expect(row).toMatchObject({ assetClass: "ACOES", strategy: "", percentage: "" });
    expect(row.id).toBeTruthy();
  });

  it("removes a strategy row by index and leaves the rest", () => {
    const form = { ...emptyTargetsForm(), strategies: [strategyRow("A"), strategyRow("B")] };

    expect(removeStrategyRow(form, 0).strategies).toEqual([strategyRow("B")]);
  });

  it("appends a ticker row under the given strategy", () => {
    const [row] = addTickerRow(emptyTargetsForm(), "Top Dividendos").tickers;

    expect(row).toMatchObject({ strategy: "Top Dividendos", ticker: "", percentage: "" });
    expect(row.id).toBeTruthy();
  });

  it("removes a ticker row by index and leaves the rest", () => {
    const form = {
      ...emptyTargetsForm(),
      tickers: [
        { id: "v", strategy: "Top Dividendos", ticker: "VALE3", percentage: "" },
        { id: "i", strategy: "Top Dividendos", ticker: "ITUB4", percentage: "" },
      ],
    };

    expect(removeTickerRow(form, 0).tickers).toEqual([
      { id: "i", strategy: "Top Dividendos", ticker: "ITUB4", percentage: "" },
    ]);
  });
});

describe("strategy target shares", () => {
  const row = (
    assetClass: StrategyTargetRow["assetClass"],
    strategy: string,
    percentage: string,
  ): StrategyTargetRow => ({ id: `${assetClass}:${strategy}`, assetClass, strategy, percentage });

  it("sums each class's strategy shares, ignoring nameless and blank rows", () => {
    const form = {
      ...emptyTargetsForm(),
      strategies: [
        row("ACOES", "Top Dividendos", "60"),
        row("ACOES", "Small Caps", "20"),
        row("FIIS", "Fundos", "30"),
        row("ACOES", "  ", "50"),
        row("FIIS", "Em branco", ""),
      ],
    };

    expect(overplannedStrategyClasses(form)).toEqual([]);
    expect(remainingLegacyShares(form, new Set(["ACOES", "FIIS"]))).toEqual([
      { assetClass: "ACOES", percentage: 20 },
      { assetClass: "FIIS", percentage: 70 },
    ]);
  });

  it("flags a class whose strategy shares exceed 100", () => {
    const form = {
      ...emptyTargetsForm(),
      strategies: [row("ACOES", "Top Dividendos", "60"), row("ACOES", "Small Caps", "50")],
    };

    expect(overplannedStrategyClasses(form)).toEqual(["ACOES"]);
  });

  it("accepts exactly 100 and derives a zero remainder", () => {
    const form = { ...emptyTargetsForm(), strategies: [row("ACOES", "Top Dividendos", "100")] };

    expect(overplannedStrategyClasses(form)).toEqual([]);
    expect(remainingLegacyShares(form, new Set(["ACOES"]))).toEqual([
      { assetClass: "ACOES", percentage: 0 },
    ]);
  });

  it("shows a remainder only for a class that has a bucket", () => {
    const form = emptyTargetsForm();

    expect(remainingLegacyShares(form, new Set(["FIIS"]))).toEqual([
      { assetClass: "FIIS", percentage: 100 },
    ]);
    expect(remainingLegacyShares(form, new Set())).toEqual([]);
  });

  it("never derives a negative remainder", () => {
    const form = { ...emptyTargetsForm(), strategies: [row("ACOES", "Top Dividendos", "120")] };

    expect(remainingLegacyShares(form, new Set(["ACOES"]))).toEqual([
      { assetClass: "ACOES", percentage: 0 },
    ]);
  });
});
