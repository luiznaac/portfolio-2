import { describe, expect, it } from "vitest";
import type { CorporateAction } from "../api/types.ts";
import {
  appliedHistory,
  catalogBlocked,
  corporateActionDetail,
  corporateActionKindLabel,
  corporateActionRows,
  corporateActionStatusLabel,
  initialDraft,
  isManualReview,
  resolutionFields,
  resolutionPayload,
  resolutionReady,
  reviewQueue,
  reviewReason,
} from "./corporateActions.ts";

const split: CorporateAction = {
  id: 1,
  ticker: "PETR4",
  kind: "SPLIT",
  ex_date: "2026-10-20",
  ratio: 2,
  source: "B3_MOVEMENTS",
  raw_type: "Desdobramento",
  ordinal: 1,
  status: "APPLIED",
  is_applied: true,
};

const group: CorporateAction = {
  ...split,
  id: 2,
  kind: "REVERSE_SPLIT",
  ratio: 10,
};

const reissue: CorporateAction = {
  ...split,
  id: 3,
  kind: "RIGHTS_ISSUE",
  ratio: undefined,
  raw_type: "Subscrição",
  status: "NEEDS_REVIEW",
};

describe("corporateActionKindLabel", () => {
  it("renders each kind in pt-BR", () => {
    expect(corporateActionKindLabel("SPLIT")).toBe("Desdobramento");
    expect(corporateActionKindLabel("REVERSE_SPLIT")).toBe("Grupamento");
    expect(corporateActionKindLabel("BONUS_SHARES")).toBe("Bonificação em ações");
    expect(corporateActionKindLabel("TICKER_CHANGE")).toBe("Troca de código");
    expect(corporateActionKindLabel("OTHER")).toBe("Outro");
  });
});

describe("corporateActionStatusLabel", () => {
  it("renders the engine decision", () => {
    expect(corporateActionStatusLabel("APPLIED")).toBe("Aplicado");
    expect(corporateActionStatusLabel("NEEDS_REVIEW")).toBe("Revisar");
  });
});

describe("corporateActionDetail", () => {
  it("explains a quantity event with its factor", () => {
    expect(corporateActionDetail(split)).toBe("×2");
    expect(corporateActionDetail(group)).toBe("÷10");
  });

  it("explains a cash event with its per-share value", () => {
    expect(
      corporateActionDetail({
        ...split,
        kind: "CAPITAL_RETURN",
        ratio: undefined,
        declared_value_per_share: 1.5,
      }),
    ).toBe("R$ 1,50/ação");
  });

  it("explains a ticker change with the new code", () => {
    expect(corporateActionDetail({ ...split, kind: "TICKER_CHANGE", target_ticker: "VALE3" })).toBe(
      "→ VALE3",
    );
  });

  it("falls back to the export label and then to a dash", () => {
    expect(corporateActionDetail(reissue)).toBe("Subscrição");
    expect(corporateActionDetail({ ...reissue, raw_type: undefined })).toBe("—");
  });
});

describe("corporateActionRows", () => {
  it("carries the derived labels and the review flag", () => {
    const rows = corporateActionRows([split, reissue]);

    expect(rows[0]).toMatchObject({
      key: 1,
      date: "2026-10-20",
      ticker: "PETR4",
      kindLabel: "Desdobramento",
      detail: "×2",
      statusLabel: "Aplicado",
      isReview: false,
    });
    expect(rows[1].isReview).toBe(true);
    expect(rows[1].statusLabel).toBe("Revisar");
  });

  it("labels an acknowledged manual kind as handled, not applied", () => {
    const rows = corporateActionRows([{ ...reissue, status: "APPLIED", is_applied: true }]);

    expect(rows[0].statusLabel).toBe("Tratado");
  });

  it("is empty when there is no log", () => {
    expect(corporateActionRows([])).toEqual([]);
  });
});

describe("reviewQueue", () => {
  it("keeps only the rows the engine queued", () => {
    expect(reviewQueue([split, group, reissue]).map((action) => action.id)).toEqual([3]);
  });
});

describe("appliedHistory", () => {
  it("keeps the rows the engine applied", () => {
    expect(appliedHistory([split, group, reissue]).map((action) => action.id)).toEqual([1, 2]);
  });
});

describe("isManualReview", () => {
  it("names the kinds the engine never applies on its own", () => {
    expect(isManualReview(reissue)).toBe(true);
    expect(isManualReview({ ...split, kind: "MERGER" })).toBe(true);
    expect(isManualReview(split)).toBe(false);
  });
});

describe("resolutionFields", () => {
  it("offers the field each computable kind needs", () => {
    expect(resolutionFields(split)).toEqual(["ratio"]);
    expect(resolutionFields({ ...split, kind: "BONUS_SHARES" })).toEqual([
      "ratio",
      "declared_value_per_share",
    ]);
    expect(resolutionFields({ ...split, kind: "CAPITAL_RETURN" })).toEqual([
      "declared_value_per_share",
    ]);
    expect(resolutionFields({ ...split, kind: "TICKER_CHANGE" })).toEqual(["target_ticker"]);
  });

  it("offers no data for a manual review kind", () => {
    expect(resolutionFields(reissue)).toEqual([]);
  });
});

describe("reviewReason", () => {
  it("states the missing column for a computable kind", () => {
    expect(reviewReason({ ...split, ratio: undefined })).toBe("A exportação não trouxe o fator.");
    expect(reviewReason({ ...split, kind: "CAPITAL_RETURN", ratio: undefined })).toBe(
      "A exportação não trouxe o valor por ação.",
    );
    expect(
      reviewReason({
        ...split,
        kind: "BONUS_SHARES",
        ratio: undefined,
        declared_value_per_share: 0,
      }),
    ).toBe("A exportação não trouxe o fator e o valor por ação.");
  });

  it("names the catalog as the blocker for a ticker change that has its new code", () => {
    expect(
      reviewReason({ ...split, kind: "TICKER_CHANGE", target_ticker: "VALE3", ratio: undefined }),
    ).toBe(
      "O ticker de origem não está no catálogo; atualize o catálogo em Ativos · Posições e aplique.",
    );
  });

  it("states the manual decision a review kind needs", () => {
    expect(reviewReason(reissue)).toBe(
      "Sem efeito automático: o motor não aplica Subscrição sozinho.",
    );
  });
});

describe("catalogBlocked", () => {
  it("names the ticker-change row whose source is missing from the catalog", () => {
    expect(
      catalogBlocked({ ...split, kind: "TICKER_CHANGE", target_ticker: "VALE3", ratio: undefined }),
    ).toBe(true);
    expect(catalogBlocked({ ...split, kind: "TICKER_CHANGE", target_ticker: undefined })).toBe(
      false,
    );
    expect(catalogBlocked(split)).toBe(false);
  });
});

describe("initialDraft", () => {
  it("prefills the fields the export already brought", () => {
    expect(initialDraft({ ...split, kind: "BONUS_SHARES", ratio: 0.5 })).toEqual({
      ratio: "0.5",
    });
    expect(
      initialDraft({
        ...split,
        kind: "TICKER_CHANGE",
        ratio: undefined,
        target_ticker: "VALE3",
      }),
    ).toEqual({ target_ticker: "VALE3" });
  });

  it("leaves the missing column empty", () => {
    expect(initialDraft({ ...split, ratio: undefined })).toEqual({});
    expect(initialDraft(reissue)).toEqual({});
  });
});

describe("resolutionReady", () => {
  it("requires every offered field to carry a positive number", () => {
    expect(resolutionReady(split, {})).toBe(false);
    expect(resolutionReady(split, { ratio: "0" })).toBe(false);
    expect(resolutionReady(split, { ratio: "2" })).toBe(true);
    expect(resolutionReady({ ...split, kind: "BONUS_SHARES" }, { ratio: "0,5" })).toBe(false);
    expect(
      resolutionReady(
        { ...split, kind: "BONUS_SHARES" },
        { ratio: "0,5", declared_value_per_share: "1.5" },
      ),
    ).toBe(true);
  });

  it("requires a target code for a ticker change and nothing for a manual kind", () => {
    expect(resolutionReady({ ...split, kind: "TICKER_CHANGE" }, { target_ticker: " " })).toBe(
      false,
    );
    expect(resolutionReady({ ...split, kind: "TICKER_CHANGE" }, { target_ticker: "vale3" })).toBe(
      true,
    );
    expect(resolutionReady(reissue, {})).toBe(true);
  });
});

describe("resolutionPayload", () => {
  it("builds the wire body, accepting a decimal comma and normalising the ticker", () => {
    expect(
      resolutionPayload(
        { ...split, kind: "BONUS_SHARES" },
        { ratio: "0,5", declared_value_per_share: "1.5" },
      ),
    ).toEqual({ ratio: 0.5, declared_value_per_share: 1.5 });
    expect(
      resolutionPayload({ ...split, kind: "TICKER_CHANGE" }, { target_ticker: "vale3" }),
    ).toEqual({ target_ticker: "VALE3" });
  });

  it("sends an empty body for a manual kind", () => {
    expect(resolutionPayload(reissue, {})).toEqual({});
  });
});
