import { describe, expect, it } from "vitest";
import type { ImportRow, Position } from "../api/types.ts";
import { custodyDivergenceSummary, custodyDivergences } from "./importDivergences.ts";

function row(ticker: string, quantity: number, error?: string): ImportRow {
  return {
    id: Math.round(quantity * 100),
    import_id: 7,
    date: "2026-11-13",
    side: "BUY",
    venue: "CUSTODIA",
    ticker,
    quantity,
    unit_price: 0,
    institution_name: "CUSTODIA",
    situation: "new",
    error,
  };
}

function position(ticker: string, quantity: number): Position {
  return { ticker, quantity, average_price: 10 };
}

describe("custodyDivergences", () => {
  it("sums repeated custody lines by ticker", () => {
    const rows = [row("PETR4", 100), row("PETR4", 50)];

    expect(custodyDivergences(rows, [position("PETR4", 100)])).toEqual([
      {
        ticker: "PETR4",
        custodyQuantity: 150,
        ledgerQuantity: 100,
        delta: 50,
        kind: "missing_buy",
        pending: false,
      },
    ]);
  });

  it("marks an equal quantity as aligned", () => {
    const [divergence] = custodyDivergences([row("PETR4", 100)], [position("PETR4", 100)]);

    expect(divergence?.kind).toBe("aligned");
    expect(divergence?.delta).toBe(0);
  });

  it("treats an upward delta as a missing buy", () => {
    const [divergence] = custodyDivergences([row("PETR4", 150)], [position("PETR4", 100)]);

    expect(divergence?.kind).toBe("missing_buy");
    expect(divergence?.delta).toBe(50);
    expect(divergence?.pending).toBe(false);
  });

  it("treats a downward delta as a missing sale with pending proceeds", () => {
    const [divergence] = custodyDivergences([row("PETR4", 60)], [position("PETR4", 100)]);

    expect(divergence?.kind).toBe("missing_sale");
    expect(divergence?.delta).toBe(-40);
    expect(divergence?.pending).toBe(true);
  });

  it("includes a ledger ticker the snapshot drops as a full missing sale", () => {
    const divergences = custodyDivergences([row("PETR4", 10)], [position("VALE3", 30)]);

    expect(divergences.map((divergence) => divergence.ticker)).toEqual(["PETR4", "VALE3"]);
    expect(divergences[1]?.kind).toBe("missing_sale");
    expect(divergences[1]?.custodyQuantity).toBe(0);
    expect(divergences[1]?.ledgerQuantity).toBe(30);
  });

  it("ignores a flagged row so it cannot skew the snapshot", () => {
    const divergences = custodyDivergences(
      [row("PETR4", 100), row("VALE3", 20, "quantity missing")],
      [position("PETR4", 100)],
    );

    expect(divergences.map((divergence) => divergence.ticker)).toEqual(["PETR4"]);
  });
});

describe("custodyDivergenceSummary", () => {
  it("counts the divergent tickers by variant", () => {
    const divergences = custodyDivergences(
      [row("PETR4", 150), row("VALE3", 20), row("ITUB4", 10)],
      [position("PETR4", 100), position("VALE3", 50), position("ITUB4", 10)],
    );

    expect(custodyDivergenceSummary(divergences)).toEqual({
      total: 3,
      divergent: 2,
      missingBuys: 1,
      missingSales: 1,
    });
  });
});
