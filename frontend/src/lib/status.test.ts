import { describe, expect, it } from "vitest";
import type { AttentionItem, PortfolioStatus } from "../api/types.ts";
import { attentionBadges, openMonthLabel, quoteFreshnessLabel } from "./status.ts";

function attention(overrides: Partial<AttentionItem>): AttentionItem {
  return {
    kind: "PENDING_TRANSFER",
    object: "MONTH",
    severity: "ACTION",
    ref: { type: "TRANSFER", id: 1 },
    params: {},
    ...overrides,
  };
}

function status(overrides: Partial<PortfolioStatus> = {}): PortfolioStatus {
  return {
    as_of: "2026-11-02",
    open_month: { id: 4, period: "2026-11" },
    capital: 100_000,
    invested: 70_000,
    deficit: 30_000,
    drift: 2.5,
    month: {
      open: true,
      steps_done: 2,
      steps_total: 6,
      suggested_orders: 3,
      pending_transfers: 1,
    },
    data: {
      quotes_as_of: "2026-10-30",
      stale_quotes: [],
      open_gaps: 0,
    },
    attention: [],
    ...overrides,
  };
}

describe("attentionBadges", () => {
  it("counts each finding under the sidebar object that owns it", () => {
    const badges = attentionBadges([
      attention({ object: "MONTH", severity: "ACTION" }),
      attention({ object: "IMPORT", severity: "ACTION", kind: "STAGED_IMPORT" }),
      attention({ object: "CORPORATE_ACTION", severity: "WATCH" }),
      attention({ object: "STRATEGY", severity: "WATCH", kind: "STAGED_EDITION" }),
      attention({ object: "ASSET", severity: "WATCH", kind: "OPEN_GAP" }),
      attention({ object: "ASSET", severity: "WATCH", kind: "STALE_QUOTE" }),
    ]);

    expect(badges.plan).toEqual({ count: 1, action: true });
    expect(badges.imports).toEqual({ count: 2, action: true });
    expect(badges.strategies).toEqual({ count: 1, action: false });
    expect(badges.assets).toEqual({ count: 2, action: false });
    expect(badges.portfolio).toBeUndefined();
  });

  it("marks an object as action-severity when any of its findings is ACTION", () => {
    const badges = attentionBadges([
      attention({ object: "ASSET", severity: "WATCH", kind: "OPEN_GAP" }),
      attention({ object: "ASSET", severity: "ACTION", kind: "UNCLASSIFIED_TICKER" }),
    ]);
    expect(badges.assets).toEqual({ count: 2, action: true });
  });

  it("returns no badges for an empty queue", () => {
    expect(attentionBadges([])).toEqual({});
  });
});

describe("quoteFreshnessLabel", () => {
  it("renders the quotes' as-of date", () => {
    expect(quoteFreshnessLabel(status())).toBe("cotações 30/10/2026");
  });

  it("says there are no quotes when the date is absent", () => {
    expect(
      quoteFreshnessLabel(
        status({ data: { quotes_as_of: undefined, stale_quotes: [], open_gaps: 0 } }),
      ),
    ).toBe("sem cotações");
  });
});

describe("openMonthLabel", () => {
  it("renders the open month and its state", () => {
    expect(openMonthLabel(status())).toBe("nov/2026 · aberto");
  });

  it("says no month is open when the month block is closed", () => {
    expect(
      openMonthLabel(
        status({
          open_month: undefined,
          month: {
            open: false,
            steps_done: 0,
            steps_total: 6,
            suggested_orders: 0,
            pending_transfers: 0,
          },
        }),
      ),
    ).toBe("Nenhum mês aberto");
  });
});
