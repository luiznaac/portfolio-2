import { describe, expect, it } from "vitest";
import {
  DESTINATIONS,
  assistantPath,
  destinationRoute,
  editionAssistantPath,
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

  it("names the custody destination with its snapshot pipeline", () => {
    const custody = DESTINATIONS.POSITIONS;

    expect(custody.label).toBe("Ativos · Posições");
    expect(destinationRoute("POSITIONS")).toBe("/ativos/positions");
    expect(custody.source).toBe("B3_POSITION");
    expect(custody.supported).toBe(true);
    expect(custody.changes.map((change) => change.object)).toEqual([
      "Ativos · Posições",
      "Ativos · Lacunas",
    ]);
    expect(custody.mapping.map((cell) => cell.from)).toEqual([
      "Código de Negociação",
      "Quantidade",
    ]);
  });

  it("names the XP edition destination with its strategy-edition pipeline", () => {
    const edition = DESTINATIONS.STRATEGY_EDITION;

    expect(edition.label).toBe("Estratégias · Edições");
    expect(edition.source).toBe("XP_REPORT");
    expect(edition.supported).toBe(true);
    expect(edition.changes.map((change) => change.object)).toEqual([
      "Estratégias · Edições",
      "Plano / Mês",
    ]);
    expect(edition.mapping.map((cell) => cell.from)).toEqual([
      "ticker",
      "peso",
      "preço-alvo",
      "rating",
      "não operar",
    ]);
  });

  it("builds the contextual and resumed assistant paths", () => {
    expect(assistantPath("TRADES")).toBe("/imports/assistant?object=TRADES");
    expect(sessionPath(7)).toBe("/imports/assistant/7");
  });

  it("builds the XP assistant path with the strategy it stages against", () => {
    expect(editionAssistantPath(7)).toBe("/imports/assistant?object=STRATEGY_EDITION&strategy=7");
  });

  it("resumes a parked session at the material and a reviewed one at apply", () => {
    expect(sessionStep(false, undefined)).toBe(1);
    expect(sessionStep(true, "STAGED")).toBe(3);
    expect(sessionStep(true, "REVIEWED")).toBe(4);
  });
});
