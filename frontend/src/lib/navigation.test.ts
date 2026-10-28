import { describe, expect, it } from "vitest";
import { NAV_GROUPS, navCrumb } from "./navigation.ts";

describe("navCrumb", () => {
  it("resolves the group and title for a top-level route", () => {
    expect(navCrumb("/imports")).toEqual({ group: "Mês", title: "Importar extrato" });
  });

  it("resolves a route in another group", () => {
    expect(navCrumb("/trades")).toEqual({ group: "Carteira", title: "Ativos" });
  });

  it("resolves the Ordens route under Mês", () => {
    expect(navCrumb("/orders")).toEqual({ group: "Mês", title: "Ordens" });
  });

  it("resolves the Fechamento route under Mês", () => {
    expect(navCrumb("/fechamento")).toEqual({ group: "Mês", title: "Fechamento" });
  });

  it("resolves the Performance route under Resultados", () => {
    expect(navCrumb("/performance")).toEqual({ group: "Resultados", title: "Performance" });
  });

  it("matches a nested path under an item's route", () => {
    expect(navCrumb("/imports/42")).toEqual({ group: "Mês", title: "Importar extrato" });
  });

  it("does not match a path that only shares a prefix", () => {
    expect(navCrumb("/tradesx")).toBeNull();
  });

  it("resolves the Cockpit route at the root", () => {
    expect(navCrumb("/")).toEqual({ group: "Mês", title: "Cockpit" });
  });

  it("returns null for a route outside the nav", () => {
    expect(navCrumb("/health")).toBeNull();
  });

  it("every nav item carries a non-empty label and an absolute route", () => {
    for (const group of NAV_GROUPS) {
      for (const item of group.items) {
        expect(item.label.length).toBeGreaterThan(0);
        expect(item.to.startsWith("/")).toBe(true);
      }
    }
  });
});
