// @vitest-environment jsdom
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { StagedImport } from "../api/client.ts";
import type { Import, ImportApplyResult, ImportRow, ImportRowSituation } from "../api/types.ts";
import { ImportAssistant } from "./ImportAssistant.tsx";

const state = vi.hoisted(() => ({
  stageInput: null as unknown,
  staged: null as StagedImport | null,
  record: null as Import | null,
  rows: [] as ImportRow[],
  reviewed: null as Import | null,
  applied: null as ImportApplyResult | null,
  reviewCalls: 0,
  applyCalls: 0,
}));

vi.mock("../api/queries.ts", () => ({
  useStageImport: () => ({
    mutate: (input: unknown, options?: { onSuccess?: (value: unknown) => void }) => {
      state.stageInput = input;
      if (state.staged) options?.onSuccess?.(state.staged);
    },
    isPending: false,
    error: null,
  }),
  useImport: () => ({ data: state.record, isLoading: false }),
  useImportRows: () => ({ data: state.rows, isLoading: false }),
  useReviewImport: () => ({
    mutate: (_id: number, options?: { onSuccess?: (value: unknown) => void }) => {
      state.reviewCalls += 1;
      if (state.reviewed) options?.onSuccess?.(state.reviewed);
    },
    isPending: false,
    error: null,
  }),
  useApplyImport: () => ({
    mutate: (_id: number, options?: { onSuccess?: (value: unknown) => void }) => {
      state.applyCalls += 1;
      if (state.applied) options?.onSuccess?.(state.applied);
    },
    isPending: false,
    error: null,
  }),
}));

function importRecord(status: Import["status"]): Import {
  return {
    id: 7,
    source: "B3_TRADES",
    file_name: "neg.xlsx",
    file_hash: "0123456789abcdef",
    status,
    destination: { object_type: "TRADES" },
    has_original: true,
    created_at: "2026-10-14T10:00:00",
  };
}

function row(id: number, situation: ImportRowSituation): ImportRow {
  return {
    id,
    import_id: 7,
    date: "2026-10-14",
    side: "BUY",
    venue: "VISTA",
    ticker: "PETR4",
    quantity: 100,
    unit_price: 48.92,
    institution_name: "XP INVESTIMENTOS",
    situation,
  };
}

function LocationProbe() {
  const location = useLocation();
  return <p data-testid="location">{location.pathname}</p>;
}

function renderAssistant(initialEntry: string) {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <LocationProbe />
      <Routes>
        <Route path="/imports/assistant" element={<ImportAssistant />} />
        <Route path="/imports/assistant/:importId" element={<ImportAssistant />} />
        <Route path="/imports" element={<p>caixa de entrada</p>} />
        <Route path="/ativos/trades" element={<p>negócios</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  state.stageInput = null;
  state.staged = null;
  state.record = null;
  state.rows = [];
  state.reviewed = null;
  state.applied = null;
  state.reviewCalls = 0;
  state.applyCalls = 0;
});

afterEach(cleanup);

describe("the import assistant", () => {
  it("pre-fills the destination from context and stages without applying", async () => {
    state.staged = { import: importRecord("STAGED"), created: true };
    state.record = importRecord("STAGED");
    const user = userEvent.setup();
    renderAssistant("/imports/assistant?object=TRADES");

    expect(screen.getAllByText("Ativos · Negócios").length).toBeGreaterThan(0);
    expect(screen.getByText(/B3 — negociação/)).toBeDefined();
    expect(screen.queryByRole("heading", { name: "Linhas" })).toBeNull();

    await user.upload(screen.getByLabelText("Arquivo"), new File(["x"], "neg.xlsx"));
    await user.click(screen.getByRole("button", { name: "Enviar e revisar" }));

    expect(state.stageInput).toEqual({
      source: "B3_TRADES",
      file: expect.any(File),
      destination: "TRADES",
    });
    expect(state.reviewCalls).toBe(0);
    expect(state.applyCalls).toBe(0);
    expect(screen.getByTestId("location").textContent).toBe("/imports/assistant/7");
    expect(screen.getByRole("heading", { name: "Destino" })).toBeDefined();
  });

  it("resumes a parked session at the material and applies onto the destination", async () => {
    state.record = importRecord("STAGED");
    state.reviewed = importRecord("REVIEWED");
    state.applied = {
      record: importRecord("APPLIED"),
      applied: 1,
      rejected: 1,
      skipped_duplicate: 0,
    };
    state.rows = [row(1, "new"), row(2, "error")];
    const user = userEvent.setup();
    renderAssistant("/imports/assistant/7");

    expect(screen.getByRole("heading", { name: "Linhas" })).toBeDefined();
    expect(screen.getByText("1 com atenção")).toBeDefined();
    expect(screen.getByText(/1 linha\(s\) com atenção ficam no lote de reenvio/)).toBeDefined();

    await user.click(screen.getByRole("button", { name: "Continuar →" }));

    expect(state.reviewCalls).toBe(1);
    expect(screen.getByRole("heading", { name: "O que esta importação muda" })).toBeDefined();
    expect(screen.getAllByText("Ativos · Negócios").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Ativos · Posições").length).toBeGreaterThan(0);

    await user.click(screen.getByRole("button", { name: "Aplicar 1 linha(s) válida(s)" }));

    expect(state.applyCalls).toBe(1);
    expect(screen.getByTestId("location").textContent).toBe("/ativos/trades");
  });

  it("opens a reviewed record directly on the apply decision", () => {
    state.record = importRecord("REVIEWED");
    state.rows = [row(1, "new")];
    renderAssistant("/imports/assistant/7");

    expect(screen.getByRole("heading", { name: "O que esta importação muda" })).toBeDefined();
    expect(screen.getByRole("button", { name: /Aplicar/ })).toBeDefined();
  });

  it("leaves the staged session parked in the inbox", async () => {
    state.record = importRecord("STAGED");
    const user = userEvent.setup();
    renderAssistant("/imports/assistant/7");

    await user.click(screen.getByRole("button", { name: "Sair" }));

    expect(screen.getByTestId("location").textContent).toBe("/imports");
    expect(state.applyCalls).toBe(0);
  });
});
