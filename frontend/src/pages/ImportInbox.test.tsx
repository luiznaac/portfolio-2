// @vitest-environment jsdom
import { cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { Import } from "../api/types.ts";
import { ImportInbox } from "./ImportInbox.tsx";

const state = vi.hoisted(() => ({ imports: [] as Import[] }));

vi.mock("../api/queries.ts", () => ({
  useImports: () => ({ data: state.imports }),
}));

const parked: Import = {
  id: 7,
  source: "B3_TRADES",
  file_name: "neg.xlsx",
  file_hash: "hash",
  status: "STAGED",
  destination: { object_type: "TRADES" },
  has_original: true,
  created_at: "2026-10-14T10:00:00",
};

afterEach(() => {
  cleanup();
  state.imports = [];
});

describe("Importações · Caixa de entrada", () => {
  it("offers the one upload entry and the parked session to resume", () => {
    state.imports = [parked];
    render(
      <MemoryRouter>
        <ImportInbox />
      </MemoryRouter>,
    );

    expect(screen.getByRole("link", { name: "Enviar arquivo →" }).getAttribute("href")).toBe(
      "/imports/assistant?object=TRADES",
    );
    expect(screen.getByText("neg.xlsx")).toBeDefined();
    expect(screen.getByRole("link", { name: "Retomar →" }).getAttribute("href")).toBe(
      "/imports/assistant/7",
    );
  });
});
