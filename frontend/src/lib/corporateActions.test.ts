import { describe, expect, it } from "vitest";
import type { CorporateAction } from "../api/types.ts";
import {
  corporateActionDetail,
  corporateActionKindLabel,
  corporateActionRows,
  corporateActionStatusLabel,
  reviewQueue,
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

  it("is empty when there is no log", () => {
    expect(corporateActionRows([])).toEqual([]);
  });
});

describe("reviewQueue", () => {
  it("keeps only the rows the engine queued", () => {
    expect(reviewQueue([split, group, reissue]).map((action) => action.id)).toEqual([3]);
  });
});
