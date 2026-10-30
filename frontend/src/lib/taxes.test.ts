import { describe, expect, it } from "vitest";
import type {
  ExemptionMeter,
  HarvestProposal,
  RealizedResult,
  StockTaxReport,
} from "../api/types.ts";
import {
  darfDeadlineLabel,
  darfIsOverdue,
  harvestPairForm,
  hasSales,
  meterLabel,
  meterProgress,
  realizedKindLabel,
  realizedRows,
  taxPeriodChip,
  toHarvestPairCreation,
} from "./taxes.ts";

const meter: ExemptionMeter = {
  gross_sales: 15000,
  exemption_limit: 20000,
  exceeded: false,
  realized_gain: 0,
  taxable_gain: 0,
  rate: 15,
  tax: 0,
};

const realized: RealizedResult[] = [
  { kind: "SWING", gross_sales: 15000, gains: 800, losses: 300, result: 500 },
  { kind: "DAY_TRADE", gross_sales: 0, gains: 0, losses: 0, result: 0 },
  { kind: "FII", gross_sales: 0, gains: 0, losses: 100, result: -100 },
];

const report: StockTaxReport = {
  period: "2026-10",
  meter,
  average_prices: [],
  realized,
  carry: [],
  day_trades: [],
  pending_gaps: [],
  unclassified: [],
  darf_due_date: "2026-11-30",
};

const proposal: HarvestProposal = {
  ticker: "PETR4",
  strategy_id: 7,
  strategy: "Dividendos",
  quantity: 300,
  average_price: 12,
  price: 10,
  loss: 600,
  neutro: false,
  sell: { date: "2026-10-22", quantity: 300, price: 10, amount: 3000 },
  rebuy: { date: "2026-10-23", quantity: 300, price: 10, amount: 3000 },
};

describe("tax ledger labels", () => {
  it("labels each tax kind", () => {
    expect(realizedKindLabel("SWING")).toBe("Swing");
    expect(realizedKindLabel("DAY_TRADE")).toBe("Day trade");
    expect(realizedKindLabel("FII")).toBe("FIIs");
  });

  it("maps realized results to display rows in server order", () => {
    expect(realizedRows(realized)).toEqual([
      {
        kind: "SWING",
        kindLabel: "Swing",
        grossSales: 15000,
        gains: 800,
        losses: 300,
        result: 500,
      },
      {
        kind: "DAY_TRADE",
        kindLabel: "Day trade",
        grossSales: 0,
        gains: 0,
        losses: 0,
        result: 0,
      },
      { kind: "FII", kindLabel: "FIIs", grossSales: 0, gains: 0, losses: 100, result: -100 },
    ]);
  });
});

describe("tax period labels", () => {
  it("chips the open month, a closed month and imported history", () => {
    expect(taxPeriodChip("2026-10", "2026-10", "2026-10", ["2026-09"])).toEqual({
      label: "mês aberto",
      tone: "green",
    });
    expect(taxPeriodChip("2026-09", "2026-10", "2026-10", ["2026-09"])).toEqual({
      label: "fechado",
      tone: "neutral",
    });
    expect(taxPeriodChip("2020-12", "2026-10", "2026-10", ["2026-09"])).toEqual({
      label: "histórico",
      tone: "amber",
    });
    expect(taxPeriodChip("2026-10", "2026-10", null, [])).toBeNull();
  });

  it("detects a period with no sales and an overdue DARF", () => {
    expect(hasSales(report)).toBe(true);
    expect(hasSales({ ...report, realized: [] })).toBe(false);
    expect(darfIsOverdue(report, "2026-11-30")).toBe(false);
    expect(darfIsOverdue(report, "2026-12-01")).toBe(true);
  });
});

describe("DARF deadline", () => {
  it("labels the report's deadline", () => {
    const report: StockTaxReport = {
      period: "2026-10",
      meter,
      average_prices: [],
      realized,
      carry: [],
      day_trades: [],
      pending_gaps: [],
      unclassified: [],
      darf_due_date: "2026-11-30",
    };

    expect(darfDeadlineLabel(report)).toBe("30/11/2026");
  });
});

describe("exemption meter", () => {
  it("fills by gross sales against the limit", () => {
    expect(meterProgress(meter)).toBe(75);
  });

  it("caps at 100% once crossed", () => {
    expect(meterProgress({ ...meter, gross_sales: 26000, exceeded: true })).toBe(100);
  });

  it("labels the meter's verdict", () => {
    expect(meterLabel(meter)).toBe("Dentro da isenção");
    expect(meterLabel({ ...meter, exceeded: true })).toBe("Teto estourado");
  });
});

describe("editable harvest pair", () => {
  it("seeds the form from the suggested pair", () => {
    expect(harvestPairForm(proposal)).toEqual({
      sellDate: "2026-10-22",
      sellQuantity: "300",
      sellPrice: "10",
      rebuyDate: "2026-10-23",
      rebuyQuantity: "300",
      rebuyPrice: "10",
    });
  });

  it("builds the acceptance payload from the edited legs", () => {
    expect(toHarvestPairCreation(proposal, harvestPairForm(proposal))).toEqual({
      ticker: "PETR4",
      strategy_id: 7,
      sell_date: "2026-10-22",
      sell_quantity: 300,
      sell_price: 10,
      rebuy_date: "2026-10-23",
      rebuy_quantity: 300,
      rebuy_price: 10,
    });
  });

  it("accepts edited quantities and prices on both legs", () => {
    expect(
      toHarvestPairCreation(proposal, {
        sellDate: "2026-10-22",
        sellQuantity: "150",
        sellPrice: "9.5",
        rebuyDate: "2026-10-26",
        rebuyQuantity: "150",
        rebuyPrice: "9.4",
      }),
    ).toMatchObject({
      sell_quantity: 150,
      sell_price: 9.5,
      rebuy_date: "2026-10-26",
      rebuy_quantity: 150,
      rebuy_price: 9.4,
    });
  });

  it("refuses a weekend leg", () => {
    const form = harvestPairForm(proposal);

    expect(toHarvestPairCreation(proposal, { ...form, sellDate: "2026-10-24" })).toBeNull();
    expect(toHarvestPairCreation(proposal, { ...form, rebuyDate: "2026-10-24" })).toBeNull();
  });

  it("refuses a same-day or earlier rebuy", () => {
    const form = harvestPairForm(proposal);

    expect(toHarvestPairCreation(proposal, { ...form, rebuyDate: "2026-10-22" })).toBeNull();
    expect(toHarvestPairCreation(proposal, { ...form, rebuyDate: "2026-10-21" })).toBeNull();
  });

  it("refuses a blank, zero or fractional-unit leg", () => {
    const form = harvestPairForm(proposal);

    expect(toHarvestPairCreation(proposal, { ...form, sellQuantity: "" })).toBeNull();
    expect(toHarvestPairCreation(proposal, { ...form, sellPrice: "0" })).toBeNull();
    expect(toHarvestPairCreation(proposal, { ...form, rebuyQuantity: "0.5" })).toBeNull();
    expect(toHarvestPairCreation(proposal, { ...form, rebuyPrice: "abc" })).toBeNull();
  });

  it("refuses a malformed date", () => {
    const form = harvestPairForm(proposal);

    expect(toHarvestPairCreation(proposal, { ...form, sellDate: "22/10/2026" })).toBeNull();
  });
});
