// @vitest-environment jsdom
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { StagedImport } from "../api/client.ts";
import type {
  Edition,
  EditionActivation,
  EditionImport,
  Import,
  ImportApplyResult,
  ImportRow,
  ImportRowSituation,
  Position,
  Strategy,
} from "../api/types.ts";
import { activeEdition as newestActiveEdition } from "../lib/strategy.ts";
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
  strategies: [] as Strategy[],
  editions: [] as Edition[],
  positions: [] as Position[],
  editionInput: null as { strategyId: number; file: File } | null,
  importedEdition: null as EditionImport | null,
  activations: [] as { strategyId: number; activation: EditionActivation }[],
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
  useImport: () => ({ data: state.record ?? undefined, isLoading: false }),
  useImportRows: () => ({ data: state.rows, isLoading: false }),
  usePositions: () => ({ data: state.positions, isLoading: false }),
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
  useStrategies: () => ({ data: state.strategies, isLoading: false }),
  useEditions: () => ({ data: state.editions, isLoading: false }),
  useImportEdition: () => ({
    mutate: (
      input: { strategyId: number; file: File },
      options?: { onSuccess?: (value: EditionImport) => void },
    ) => {
      state.editionInput = input;
      if (state.importedEdition) options?.onSuccess?.(state.importedEdition);
    },
    isPending: false,
    error: null,
  }),
  useActivateEdition: () => ({
    mutate: (
      input: { strategyId: number; activation: EditionActivation },
      options?: { onSuccess?: (value: Edition) => void },
    ) => {
      state.activations.push(input);
      const promoted = state.editions.find(
        (edition) => edition.reference_month === input.activation.reference_month,
      );
      state.editions = state.editions.map((edition) =>
        edition.reference_month === input.activation.reference_month
          ? { ...edition, status: "ACTIVE" as const }
          : edition,
      );
      if (promoted !== undefined) options?.onSuccess?.(promoted);
    },
    isPending: false,
    error: null,
  }),
}));

function importRecord(
  status: Import["status"],
  object: Import["destination"]["object_type"] = "TRADES",
): Import {
  return {
    id: 7,
    source: object === "POSITIONS" ? "B3_POSITION" : "B3_TRADES",
    file_name: object === "POSITIONS" ? "posicao.xlsx" : "neg.xlsx",
    file_hash: "0123456789abcdef",
    status,
    destination: { object_type: object },
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

const editionStrategy: Strategy = {
  id: 1,
  name: "Top Dividendos",
  asset_class: "ACOES",
  kind: "REPORT_DRIVEN",
};

const activeEdition: Edition = {
  id: 10,
  strategy_id: 1,
  reference_month: "2026-09",
  status: "ACTIVE",
  entries: [
    { ticker: "PETR4", weight: 25, rating: "BUY", target_price: 60, do_not_trade: false },
    { ticker: "VALE3", weight: 25, rating: "NEUTRAL", target_price: 85, do_not_trade: false },
    { ticker: "ITUB4", weight: 20, rating: "BUY", target_price: 30, do_not_trade: false },
    { ticker: "EGIE3", weight: 20, rating: "BUY", target_price: 48, do_not_trade: false },
    { ticker: "B3SA3", weight: 10, rating: "NEUTRAL", target_price: 12, do_not_trade: false },
  ],
};

const stagedEdition: Edition = {
  id: 11,
  strategy_id: 1,
  reference_month: "2026-10",
  status: "STAGED",
  entries: [
    { ticker: "PETR4", weight: 25, rating: "BUY", target_price: 60, do_not_trade: false },
    { ticker: "VALE3", weight: 22.5, rating: "NEUTRAL", target_price: 85, do_not_trade: true },
    { ticker: "ITUB4", weight: 20, rating: "SELL", target_price: 30, do_not_trade: false },
    { ticker: "EGIE3", weight: 20, rating: "BUY", target_price: 52, do_not_trade: false },
    { ticker: "CXSE3", weight: 12.5, rating: "BUY", target_price: 20, do_not_trade: false },
  ],
};

function custodyRow(id: number, ticker: string, quantity: number): ImportRow {
  return {
    id,
    import_id: 7,
    date: "2026-11-13",
    side: "BUY",
    venue: "CUSTODIA",
    ticker,
    quantity,
    unit_price: 0,
    institution_name: "CUSTODIA",
    situation: "new",
  };
}

function custodyPosition(ticker: string, quantity: number): Position {
  return { ticker, quantity, average_price: 10 };
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
  state.strategies = [];
  state.editions = [];
  state.positions = [];
  state.editionInput = null;
  state.importedEdition = null;
  state.activations = [];
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

  it("opens a failed record read-only instead of offering an apply", () => {
    state.record = importRecord("FAILED");
    renderAssistant("/imports/assistant/7");

    expect(screen.getByRole("heading", { name: "O que esta importação muda" })).toBeDefined();
    expect(screen.getByText(/Esta tentativa falhou e não pode ser aplicada/)).toBeDefined();
    expect(screen.queryByRole("button", { name: /Aplicar/ })).toBeNull();
    expect(state.applyCalls).toBe(0);
  });

  it("leaves the staged session parked in the inbox", async () => {
    state.record = importRecord("STAGED");
    const user = userEvent.setup();
    renderAssistant("/imports/assistant/7");

    await user.click(screen.getByRole("button", { name: "Sair" }));

    expect(screen.getByTestId("location").textContent).toBe("/imports");
    expect(state.applyCalls).toBe(0);
  });

  it("stages a custody upload as the POSITIONS destination", async () => {
    state.staged = { import: importRecord("STAGED", "POSITIONS"), created: true };
    state.record = importRecord("STAGED", "POSITIONS");
    const user = userEvent.setup();
    renderAssistant("/imports/assistant?object=POSITIONS");

    expect(screen.getAllByText("Ativos · Posições").length).toBeGreaterThan(0);
    expect(screen.getByText(/B3 — posição em custódia/)).toBeDefined();

    await user.upload(screen.getByLabelText("Arquivo"), new File(["x"], "posicao.xlsx"));
    await user.click(screen.getByRole("button", { name: "Enviar e revisar" }));

    expect(state.stageInput).toEqual({
      source: "B3_POSITION",
      file: expect.any(File),
      destination: "POSITIONS",
    });
    expect(state.reviewCalls).toBe(0);
    expect(state.applyCalls).toBe(0);
    expect(screen.getByTestId("location").textContent).toBe("/imports/assistant/7");
  });

  it("renders the custody divergences and still allows apply", async () => {
    state.record = importRecord("STAGED", "POSITIONS");
    state.reviewed = importRecord("REVIEWED", "POSITIONS");
    state.applied = {
      record: importRecord("APPLIED", "POSITIONS"),
      applied: 2,
      rejected: 0,
      skipped_duplicate: 0,
    };
    state.rows = [custodyRow(1, "PETR4", 150), custodyRow(2, "VALE3", 20)];
    state.positions = [custodyPosition("PETR4", 100), custodyPosition("VALE3", 50)];
    const user = userEvent.setup();
    renderAssistant("/imports/assistant/7");

    expect(screen.getByRole("heading", { name: "Divergências" })).toBeDefined();
    expect(screen.getByText("+50")).toBeDefined();
    expect(screen.getByText("−30")).toBeDefined();
    expect(screen.getByText("compra faltante")).toBeDefined();
    expect(screen.getByText("venda faltante · proventos pendentes")).toBeDefined();
    expect(screen.getByText(/Uma divergência não bloqueia a aplicação/)).toBeDefined();
    expect(screen.getByText("2 divergências")).toBeDefined();

    await user.click(screen.getByRole("button", { name: "Continuar →" }));

    expect(state.reviewCalls).toBe(1);
    expect(screen.getByRole("heading", { name: "O que esta importação muda" })).toBeDefined();
    expect(screen.getAllByText("Ativos · Lacunas").length).toBeGreaterThan(0);
    const applyButton = screen.getByRole("button", { name: "Aplicar 2 posições" });
    expect((applyButton as HTMLButtonElement).disabled).toBe(false);

    await user.click(applyButton);

    expect(state.applyCalls).toBe(1);
    expect(screen.getByTestId("location").textContent).toBe("/ativos/positions");
  });

  it("stages an XP report against its strategy and renders the edition diff", async () => {
    state.strategies = [editionStrategy];
    state.editions = [activeEdition];
    state.importedEdition = { parser: "XpEquityCarteira", edition: stagedEdition };
    const user = userEvent.setup();
    renderAssistant("/imports/assistant?object=STRATEGY_EDITION&strategy=1");

    expect(screen.getAllByText("Estratégias · Edições").length).toBeGreaterThan(0);
    expect(screen.getByText("Top Dividendos")).toBeDefined();
    expect(screen.getByText(/XP — relatório de carteira/)).toBeDefined();

    await user.upload(screen.getByLabelText("Arquivo"), new File(["x"], "relatorio.pdf"));
    await user.click(screen.getByRole("button", { name: "Enviar e revisar" }));

    expect(state.editionInput).toEqual({ strategyId: 1, file: expect.any(File) });
    expect(screen.getByRole("heading", { name: "Destino" })).toBeDefined();
    expect(screen.getByText("promover a edição a ativa")).toBeDefined();

    await user.click(screen.getByRole("button", { name: "Continuar →" }));

    expect(screen.getByRole("heading", { name: "Diff da edição" })).toBeDefined();
    expect(screen.getByText("25,0% → 22,5%")).toBeDefined();
    expect(screen.getByText("Compra → Venda")).toBeDefined();
    expect(screen.getByText("R$ 48,00 → R$ 52,00")).toBeDefined();
    expect(screen.getByText("1 peso alterado")).toBeDefined();
    expect(screen.getByText("1 preço-alvo alterado")).toBeDefined();
    expect(screen.getByText("1 rating alterado")).toBeDefined();
    expect(screen.getByText("1 saída")).toBeDefined();
    expect(screen.getByText("− B3SA3 10,0%")).toBeDefined();
    expect(screen.getByText(/VALE3 passa a não operar nesta edição/)).toBeDefined();
    expect(state.activations).toEqual([]);
  });

  it("promotes the staged edition explicitly and names the next recompute", async () => {
    state.strategies = [editionStrategy];
    state.editions = [activeEdition, stagedEdition];
    const user = userEvent.setup();
    renderAssistant("/imports/assistant?object=STRATEGY_EDITION&strategy=1");

    expect(screen.getByRole("heading", { name: "Diff da edição" })).toBeDefined();
    await user.click(screen.getByRole("button", { name: "Continuar →" }));

    expect(screen.getByText(/os novos pesos entram no próximo recálculo/)).toBeDefined();
    expect(state.activations).toEqual([]);
    expect(newestActiveEdition(state.editions)?.reference_month).toBe("2026-09");

    await user.click(screen.getByRole("button", { name: "Promover a ativa" }));

    expect(state.activations).toHaveLength(1);
    expect(state.activations[0]?.strategyId).toBe(1);
    expect(state.activations[0]?.activation.reference_month).toBe("2026-10");
    expect(state.activations[0]?.activation.entries.map((entry) => entry.ticker)).toEqual([
      "PETR4",
      "VALE3",
      "ITUB4",
      "EGIE3",
      "CXSE3",
    ]);
    const promotedNote = screen.getByTestId("edition-decision-note");
    expect(promotedNote.textContent).toContain("Edição promovida");
    expect(promotedNote.textContent).toContain("próximo recálculo");
    expect(state.editions.find((edition) => edition.reference_month === "2026-10")?.status).toBe(
      "ACTIVE",
    );
    expect(newestActiveEdition(state.editions)?.reference_month).toBe("2026-10");
  });

  it("discards the staged edition without touching the active one", async () => {
    state.strategies = [editionStrategy];
    state.editions = [activeEdition, stagedEdition];
    const user = userEvent.setup();
    renderAssistant("/imports/assistant?object=STRATEGY_EDITION&strategy=1");

    await user.click(await screen.findByRole("button", { name: "Continuar →" }));
    await user.click(screen.getByRole("button", { name: "Descartar" }));

    expect(state.activations).toEqual([]);
    const discardedNote = screen.getByTestId("edition-decision-note");
    expect(discardedNote.textContent).toContain("Edição não promovida");
    expect(discardedNote.textContent).toContain("próximo recálculo");
    expect(state.editions.find((edition) => edition.reference_month === "2026-10")?.status).toBe(
      "STAGED",
    );
    expect(newestActiveEdition(state.editions)?.reference_month).toBe("2026-09");
    expect(screen.getByRole("button", { name: "Promover a ativa" })).toBeDefined();
  });
});
