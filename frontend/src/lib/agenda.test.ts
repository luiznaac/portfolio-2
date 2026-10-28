import { describe, expect, it } from "vitest";
import type { Bond, Month } from "../api/types.ts";
import { agendaItems, dismissAgendaItem, readAgendaIds, serializeAgendaIds } from "./agenda.ts";

function bond(id: number, name: string, maturityDate: string): Bond {
  return { id, name, rate: 10, maturity_date: maturityDate, sub_class: "FIXED_RATE" };
}

function month(overrides: Partial<Month> = {}): Month {
  return {
    id: 1,
    period: "2026-09",
    capital: 100_000,
    floor: 20_000,
    status: "OPEN",
    aportes: 0,
    ...overrides,
  };
}

describe("agendaItems", () => {
  it("lists an RF maturity inside the seven-day lead", () => {
    const items = agendaItems([bond(1, "CDB Banco X", "2026-10-30")], null, [], "2026-10-24");
    expect(items).toHaveLength(1);
    expect(items[0]?.kind).toBe("MATURITY");
    expect(items[0]?.id).toBe("maturity:1:2026-10-30");
    expect(items[0]?.detail).toContain("6 dias");
  });

  it("leaves a maturity beyond the lead out", () => {
    expect(agendaItems([bond(1, "CDB Banco X", "2026-11-01")], null, [], "2026-10-24")).toEqual([]);
  });

  it("keeps an overdue maturity until it is marked read", () => {
    const items = agendaItems([bond(1, "CDB Banco X", "2026-10-20")], null, [], "2026-10-24");
    expect(items[0]?.detail).toContain("Venceu");
  });

  it("calls today's maturity out", () => {
    const items = agendaItems([bond(1, "CDB Banco X", "2026-10-24")], null, [], "2026-10-24");
    expect(items[0]?.detail).toBe("Vence hoje");
  });

  it("adds the close reminder once a new month starts with the old one still open", () => {
    const items = agendaItems([], month(), [], "2026-10-01");
    expect(items.map((item) => item.kind)).toEqual(["CLOSE"]);
    expect(items[0]?.title).toContain("set/2026");
    expect(items[0]?.to).toBe("/fechamento");
  });

  it("does not remind before the month turns", () => {
    expect(agendaItems([], month(), [], "2026-09-30")).toEqual([]);
  });

  it("does not remind when the open month is the current one", () => {
    expect(agendaItems([], month({ period: "2026-10" }), [], "2026-10-01")).toEqual([]);
  });

  it("does not remind when no month was ever opened for the previous period", () => {
    expect(agendaItems([], null, [], "2026-10-01")).toEqual([]);
  });

  it("keeps a completed close notice until it is marked read", () => {
    const closed = month({ id: 9, period: "2026-09", status: "CLOSED" });
    const items = agendaItems([], null, [closed], "2026-10-15");
    expect(items).toHaveLength(1);
    expect(items[0]?.id).toBe("close-done:2026-09");
    expect(items[0]?.title).toContain("concluído");
  });

  it("uses the current move's close notice over the latest completed one", () => {
    const overdue = month({ id: 8, period: "2026-08", status: "OPEN" });
    const closed = month({ id: 9, period: "2026-09", status: "CLOSED" });
    const items = agendaItems([], overdue, [closed], "2026-10-15");
    expect(items[0]?.id).toBe("close-pending:2026-08");
  });

  it("restages an older overdue close as its own completed notice", () => {
    const closed = month({ id: 8, period: "2026-08", status: "CLOSED" });
    const items = agendaItems([], null, [closed], "2026-10-15");
    expect(items[0]?.id).toBe("close-done:2026-08");
    expect(items[0]?.title).toContain("ago/2026");
  });

  it("keeps the completed notice distinct from a dismissed pending one", () => {
    const overdue = agendaItems([], month(), [], "2026-10-15")[0];
    const completed = agendaItems([], null, [month({ status: "CLOSED" })], "2026-10-15")[0];
    expect(overdue?.id).toBe("close-pending:2026-09");
    expect(completed?.id).toBe("close-done:2026-09");
  });

  it("orders the agenda by date", () => {
    const items = agendaItems([bond(1, "CDB Banco X", "2026-10-26")], month(), [], "2026-10-24");
    expect(items.map((item) => item.kind)).toEqual(["CLOSE", "MATURITY"]);
  });
});

describe("manual read lifecycle", () => {
  it("round-trips the dismissed ids", () => {
    const next = dismissAgendaItem(new Set(["a"]), "b");
    expect([...dismissAgendaItem(readAgendaIds(serializeAgendaIds(next)), "c")]).toEqual([
      "a",
      "b",
      "c",
    ]);
  });

  it("starts empty for a missing or malformed store", () => {
    expect(readAgendaIds(null).size).toBe(0);
    expect(readAgendaIds("not json").size).toBe(0);
    expect(readAgendaIds('{"a":1}').size).toBe(0);
  });

  it("ignores non-string entries", () => {
    expect([...readAgendaIds('["a",1,null]')]).toEqual(["a"]);
  });
});
