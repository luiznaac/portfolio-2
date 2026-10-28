import { describe, expect, it } from "vitest";
import type { NavGroup } from "./navigation.ts";
import { commandItems, filterCommands } from "./palette.ts";

const GROUPS: NavGroup[] = [
  {
    label: "Mês",
    items: [
      { to: "/", label: "Cockpit", icon: "grid" },
      { to: "/orders", label: "Ordens", icon: "list" },
    ],
  },
  {
    label: "Resultados",
    items: [{ to: "/taxes", label: "Impostos", icon: "receipt" }],
  },
];

describe("commandItems", () => {
  it("flattens the nav groups into one searchable list", () => {
    expect(commandItems(GROUPS)).toEqual([
      { to: "/", label: "Cockpit", group: "Mês" },
      { to: "/orders", label: "Ordens", group: "Mês" },
      { to: "/taxes", label: "Impostos", group: "Resultados" },
    ]);
  });
});

describe("filterCommands", () => {
  it("returns every command for an empty query", () => {
    expect(filterCommands(commandItems(GROUPS), "  ")).toHaveLength(3);
  });

  it("matches the label case-insensitively", () => {
    expect(filterCommands(commandItems(GROUPS), "IMPOST").map((item) => item.label)).toEqual([
      "Impostos",
    ]);
  });

  it("matches the group name too", () => {
    expect(filterCommands(commandItems(GROUPS), "resultados").map((item) => item.to)).toEqual([
      "/taxes",
    ]);
  });

  it("returns nothing when no command matches", () => {
    expect(filterCommands(commandItems(GROUPS), "zzz")).toEqual([]);
  });
});
