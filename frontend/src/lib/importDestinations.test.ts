import { describe, expect, it } from "vitest";
import {
  DESTINATIONS,
  assistantPath,
  destinationRoute,
  resolveDestination,
  sessionPath,
  sessionStep,
} from "./importDestinations.ts";

describe("import destinations", () => {
  it("names the trades destination with its console object, source and parser", () => {
    const trades = resolveDestination("TRADES");

    expect(trades.label).toBe("Ativos · Negócios");
    expect(trades.route).toBe("/ativos/trades");
    expect(trades.source).toBe("B3_TRADES");
    expect(trades.supported).toBe(true);
    expect(trades.changes.map((change) => change.object)).toEqual([
      "Ativos · Negócios",
      "Ativos · Posições",
    ]);
    expect(trades.mapping.length).toBeGreaterThan(0);
  });

  it("falls back to the trades destination for an absent or unknown context", () => {
    expect(resolveDestination(null)).toBe(DESTINATIONS.TRADES);
    expect(resolveDestination("nope")).toBe(DESTINATIONS.TRADES);
  });

  it("keeps a typed destination's own route even before its assistant variant lands", () => {
    expect(DESTINATIONS.POSITIONS.label).toBe("Ativos · Posições");
    expect(destinationRoute("POSITIONS")).toBe("/ativos/positions");
    expect(DESTINATIONS.STRATEGY_EDITION.supported).toBe(false);
  });

  it("builds the contextual and resumed assistant paths", () => {
    expect(assistantPath("TRADES")).toBe("/imports/assistant?object=TRADES");
    expect(sessionPath(7)).toBe("/imports/assistant/7");
  });

  it("resumes a parked session at the material and a reviewed one at apply", () => {
    expect(sessionStep(false, undefined)).toBe(1);
    expect(sessionStep(true, "STAGED")).toBe(3);
    expect(sessionStep(true, "REVIEWED")).toBe(4);
  });
});
