// @vitest-environment jsdom
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import type { Tone } from "../lib/tones.ts";
import { Kpi } from "./Kpi.tsx";

// The KPI tile is the detail pages' stat primitive; these assertions pin that it reads the shared
// meaning→token table (and the sign convention that goes with it) rather than a local colour map.
const TONES: [Tone, string][] = [
  ["default", "text-text"],
  ["muted", "text-muted"],
  ["deficit", "text-deficit"],
  ["surplus", "text-surplus"],
  ["attention", "text-attention"],
  ["return-up", "text-return-up"],
  ["return-down", "text-return-down"],
];

afterEach(cleanup);

describe("Kpi", () => {
  it.each(TONES)("renders the %s tone with its shared token class", (tone, className) => {
    render(<Kpi label="Retorno" value="+1,00%" tone={tone} testId="kpi" />);

    expect(screen.getByTestId("kpi").className).toContain(className);
  });
});
