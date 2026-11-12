import { describe, expect, it } from "vitest";
import {
  NAV_GROUPS,
  OBJECTS,
  activeSub,
  incomeRow,
  portfolioHistoryDetail,
  resolveCrumb,
} from "./routes.ts";

describe("OBJECTS", () => {
  it("holds exactly six objects in the three console groups", () => {
    expect(OBJECTS.map((object) => object.label)).toEqual([
      "Carteira",
      "Estratégias",
      "Ativos",
      "Importações",
      "Plano / Mês",
      "Análise",
    ]);
  });

  it("groups them Posição, Operações, Resultados", () => {
    expect(NAV_GROUPS.map((group) => group.label)).toEqual(["Posição", "Operações", "Resultados"]);
    expect(NAV_GROUPS.map((group) => group.objects.map((object) => object.label))).toEqual([
      ["Carteira", "Estratégias", "Ativos"],
      ["Importações", "Plano / Mês"],
      ["Análise"],
    ]);
  });

  it("opens every object on its first sub-view", () => {
    expect(OBJECTS.map((object) => object.subs[0]?.path)).toEqual([
      "/portfolio",
      "/strategies",
      "/ativos/positions",
      "/imports",
      "/plan",
      "/analysis",
    ]);
  });

  it("never lists a sub-view as a sidebar item", () => {
    const objectLabels = new Set(OBJECTS.map((object) => object.label));
    for (const group of NAV_GROUPS) {
      for (const object of group.objects) {
        expect(objectLabels.has(object.label)).toBe(true);
        for (const sub of object.subs) {
          expect(objectLabels.has(sub.label)).toBe(false);
        }
      }
    }
  });

  it("declares each sub-view path once and under its object's root", () => {
    const seen = new Set<string>();
    for (const object of OBJECTS) {
      for (const sub of object.subs) {
        expect(seen.has(sub.path)).toBe(false);
        seen.add(sub.path);
        expect(sub.path === object.root || sub.path.startsWith(`${object.root}/`)).toBe(true);
      }
    }
  });
});

describe("resolveCrumb", () => {
  it("resolves the landing as Carteira · Visão geral", () => {
    expect(resolveCrumb("/portfolio")).toEqual({
      group: "Posição",
      object: "Carteira",
      view: "Visão geral",
    });
  });

  it("resolves a sub-view under its object", () => {
    expect(resolveCrumb("/portfolio/allocation")).toEqual({
      group: "Posição",
      object: "Carteira",
      view: "Alocação",
    });
    expect(resolveCrumb("/analysis/tax")).toEqual({
      group: "Resultados",
      object: "Análise",
      view: "Impostos",
    });
    expect(resolveCrumb("/imports/corporate")).toEqual({
      group: "Operações",
      object: "Importações",
      view: "Ações corporativas",
    });
  });

  it("resolves every registered sub-view to its own label", () => {
    for (const object of OBJECTS) {
      for (const sub of object.subs) {
        expect(resolveCrumb(sub.path)?.view).toBe(sub.label);
      }
    }
  });

  it("names the entity at depth instead of the sub-view", () => {
    expect(resolveCrumb("/ativos/positions/PETR4")).toEqual({
      group: "Posição",
      object: "Ativos",
      view: "PETR4",
    });
    expect(resolveCrumb("/portfolio/history/2026-10")?.view).toBe("2026-10");
  });

  it("names a nested route that is not an entity", () => {
    expect(resolveCrumb("/strategies/import")).toEqual({
      group: "Posição",
      object: "Estratégias",
      view: "Importar relatórios",
    });
  });

  it("resolves the root path to the landing", () => {
    expect(resolveCrumb("/")?.object).toBe("Carteira");
  });

  it("ignores a trailing slash", () => {
    expect(resolveCrumb("/plan/")?.view).toBe("Mês atual");
  });

  it("returns null outside the object table", () => {
    expect(resolveCrumb("/health")).toBeNull();
    expect(resolveCrumb("/trades")).toBeNull();
  });
});

describe("incomeRow", () => {
  it("builds the Proventos deep link with the target row id", () => {
    expect(incomeRow(24)).toBe("/analysis/income?focus=24");
  });
});

describe("portfolioHistoryDetail", () => {
  it("nests the frozen month under Histórico with the period as the entity segment", () => {
    expect(portfolioHistoryDetail("2026-10")).toBe("/portfolio/history/2026-10");
  });
});

describe("activeSub", () => {
  it("keeps the parent tab active on a detail route", () => {
    const assets = OBJECTS.find((object) => object.key === "assets");
    if (assets === undefined) throw new Error("assets object missing");
    expect(activeSub(assets, "/ativos/positions/PETR4")?.label).toBe("Posições");
  });

  it("keeps the list tab active on the nested import route", () => {
    const strategies = OBJECTS.find((object) => object.key === "strategies");
    if (strategies === undefined) throw new Error("strategies object missing");
    expect(activeSub(strategies, "/strategies/import")?.label).toBe("Todas");
  });
});
