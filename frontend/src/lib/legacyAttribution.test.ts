import { describe, expect, it } from "vitest";
import type { Allocation, LegacyAttributionPreview } from "../api/types.ts";
import {
  noBucketSkips,
  outsideCustody,
  previewAddCount,
  previewTotalValue,
  shouldShowBanner,
  skipAction,
  skipReasonLabel,
} from "./legacyAttribution.ts";

describe("outsideCustody", () => {
  it("sums the outside rows and never the bucket or the unclassified line", () => {
    expect(outsideCustody(allocation)).toBe(400);
  });

  it("is zero when every class has no outside remainder", () => {
    expect(
      outsideCustody({
        ...allocation,
        classes: allocation.classes.map((cls) => ({
          ...cls,
          outside: { actual: 0, tickers: [] },
        })),
      }),
    ).toBe(0);
  });
});

describe("shouldShowBanner", () => {
  it("shows the banner while there is outside custody to attribute", () => {
    expect(shouldShowBanner(allocation)).toBe(true);
  });

  it("hides it when no outside custody remains", () => {
    const settled: Allocation = {
      ...allocation,
      classes: allocation.classes.map((cls) => ({ ...cls, outside: { actual: 0, tickers: [] } })),
    };
    expect(shouldShowBanner(settled)).toBe(false);
  });

  it("shows the banner for a held outside ticker that has no stored close", () => {
    const unquoted: Allocation = {
      ...allocation,
      classes: allocation.classes.map((cls) =>
        cls.asset_class === "ACOES"
          ? { ...cls, outside: { actual: 0, tickers: [{ ticker: "ITUB4", actual: 0 }] } }
          : cls,
      ),
    };
    expect(shouldShowBanner(unquoted)).toBe(true);
  });
});

describe("the preview totals", () => {
  it("counts every add across the per-class groups", () => {
    expect(previewAddCount(preview)).toBe(3);
  });

  it("sums the add values across the per-class groups", () => {
    expect(previewTotalValue(preview)).toBe(1800);
  });
});

describe("skipReasonLabel", () => {
  it("names every skip reason the server can send", () => {
    expect(skipReasonLabel("UNCLASSIFIED")).toBe("Sem classe");
    expect(skipReasonLabel("NO_BUCKET")).toBe("Sem estratégia manual");
    expect(skipReasonLabel("HAS_ATTRIBUTION")).toBe("Já tem atribuição");
    expect(skipReasonLabel("WRITE_FAILED")).toBe("Falha ao atribuir");
  });
});

describe("noBucketSkips", () => {
  it("groups the no-bucket skips under one line per class", () => {
    expect(
      noBucketSkips([
        { ticker: "HGLG11", reason: "NO_BUCKET", asset_class: "FIIS" },
        { ticker: "KNRI11", reason: "NO_BUCKET", asset_class: "FIIS" },
        { ticker: "VALE3", reason: "HAS_ATTRIBUTION", asset_class: "ACOES" },
      ]),
    ).toEqual([{ assetClass: "FIIS", tickers: ["HGLG11", "KNRI11"] }]);
  });
});

describe("skipAction", () => {
  it("points an unclassified ticker at its asset detail", () => {
    expect(skipAction({ ticker: "ZZZZ3", reason: "UNCLASSIFIED", asset_class: null })).toEqual({
      to: "/trades/ZZZZ3",
      label: "Classificar",
    });
  });

  it("points a class without a bucket at Estratégias", () => {
    expect(skipAction({ ticker: "HGLG11", reason: "NO_BUCKET", asset_class: "FIIS" })).toEqual({
      to: "/strategies",
      label: "Criar estratégia",
    });
  });

  it("points an attributed ticker at its asset detail", () => {
    expect(
      skipAction({ ticker: "VALE3", reason: "HAS_ATTRIBUTION", asset_class: "ACOES" }),
    ).toEqual({ to: "/trades/VALE3", label: "Ver ativo" });
  });

  it("has no link for a write failure", () => {
    expect(
      skipAction({ ticker: "ITUB4", reason: "WRITE_FAILED", asset_class: "ACOES", message: "bad" }),
    ).toBeUndefined();
  });
});

const allocation: Allocation = {
  period: "2026-09",
  capital: 100000,
  floor: 20000,
  ideal: 100000,
  actual: 3600,
  deficit: 96400,
  drift: -96.4,
  unattributed: 500,
  unclassified: {
    actual: 100,
    tickers: [{ ticker: "ZZZZ9", actual: 100 }],
  },
  classes: [
    {
      asset_class: "ACOES",
      target: 50,
      ideal: 50000,
      actual: 1500,
      deficit: 48500,
      drift: -48.5,
      unattributed: 400,
      strategies: [],
      manual: { strategy: "Legado Ações", actual: 500, tickers: [] },
      outside: { actual: 400, tickers: [{ ticker: "ITUB4", actual: 400 }] },
      sub_classes: [],
    },
    {
      asset_class: "RENDA_FIXA",
      target: 50,
      ideal: 50000,
      actual: 2000,
      deficit: 48000,
      drift: -48,
      unattributed: 0,
      strategies: [],
      outside: { actual: 0, tickers: [] },
      sub_classes: [],
    },
  ],
};

const preview: LegacyAttributionPreview = {
  groups: [
    {
      asset_class: "ACOES",
      strategy_id: 9,
      strategy_name: "Legado Ações",
      adds: [
        { ticker: "ITUB4", quantity: 50, value: 500 },
        { ticker: "VALE3", quantity: 10, value: 100 },
      ],
    },
    {
      asset_class: "FIIS",
      strategy_id: 10,
      strategy_name: "Legado FIIs",
      adds: [{ ticker: "HGLG11", quantity: 12, value: 1200 }],
    },
  ],
  skips: [{ ticker: "ZZZZ3", reason: "UNCLASSIFIED", asset_class: null }],
};
