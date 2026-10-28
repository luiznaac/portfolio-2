import { describe, expect, it } from "vitest";
import type { CatalogEntry } from "../api/types.ts";
import { classificationNote, effectiveAssetClass } from "./catalog.ts";

function entry(
  assetClass?: CatalogEntry["asset_class"],
  override?: CatalogEntry["asset_class_override"],
) {
  return {
    ticker: "XPTO3",
    short_name: "XPTO",
    bdi_type: "??",
    asset_kind: "UNKNOWN",
    asset_class: assetClass,
    asset_class_override: override,
    first_seen: "2026-10-01",
    last_seen: "2026-10-01",
  } satisfies CatalogEntry;
}

describe("effectiveAssetClass", () => {
  it("prefers the manual override to the BDI-derived class", () => {
    expect(effectiveAssetClass(entry("FIIS", "ACOES"))).toBe("ACOES");
  });

  it("falls back to the BDI-derived class when there is no override", () => {
    expect(effectiveAssetClass(entry("FIIS"))).toBe("FIIS");
  });

  it("is null for a missing row and for an entry the catalog has not classified", () => {
    expect(effectiveAssetClass(undefined)).toBeNull();
    expect(effectiveAssetClass(entry(undefined, undefined))).toBeNull();
  });
});

describe("classificationNote", () => {
  it("names the manual class and the catalog's newer call when they disagree", () => {
    expect(classificationNote(entry("FIIS", "ACOES"))).toBe(
      "classe manual: Ações — o catálogo agora diz FII",
    );
  });

  it("names the manual class alone when the catalog has no class or agrees", () => {
    expect(classificationNote(entry(undefined, "ACOES"))).toBe("classe manual: Ações");
    expect(classificationNote(entry("ACOES", "ACOES"))).toBe("classe manual: Ações");
  });

  it("stays silent when there is no override", () => {
    expect(classificationNote(entry("ACOES", undefined))).toBeNull();
  });
});
