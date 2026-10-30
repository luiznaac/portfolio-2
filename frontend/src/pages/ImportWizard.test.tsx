// @vitest-environment jsdom
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { StagedImport } from "../api/client.ts";
import type {
  Allocation,
  Import,
  ImportRow,
  ImportRowSituation,
  Position,
  TickerRefreshResult,
} from "../api/types.ts";
import { ImportWizard } from "./ImportWizard.tsx";

const state = vi.hoisted(() => ({
  staged: null as StagedImport | null,
  rows: [] as ImportRow[],
  reviewed: null as Import | null,
  applied: null as Import | null,
  positions: [] as Position[],
  allocation: null as Allocation | null,
  refresh: null as TickerRefreshResult | null,
  refreshed: 0,
}));

vi.mock("../api/queries.ts", () => ({
  useStageImport: () => ({
    mutate: (_input: unknown, options?: { onSuccess?: (value: unknown) => void }) => {
      if (state.staged) options?.onSuccess?.(state.staged);
    },
    isPending: false,
    error: null,
    reset: () => {},
  }),
  useImportRows: () => ({ data: state.rows, isLoading: false }),
  useReviewImport: () => ({
    mutate: (_id: number, options?: { onSuccess?: (value: unknown) => void }) => {
      if (state.reviewed) options?.onSuccess?.(state.reviewed);
    },
    isPending: false,
    error: null,
    reset: () => {},
  }),
  useApplyImport: () => ({
    mutate: (_id: number, options?: { onSuccess?: (value: unknown) => void }) => {
      if (state.applied) options?.onSuccess?.(state.applied);
    },
    isPending: false,
    error: null,
    reset: () => {},
  }),
  usePositions: () => ({ data: state.positions, isLoading: false }),
  useCurrentAllocation: () => ({ data: state.allocation, isLoading: false }),
  useRefreshCatalog: () => ({
    mutate: () => {
      state.refreshed += 1;
    },
    isPending: false,
    data: state.refresh,
    error: null,
  }),
}));

function importRecord(status: Import["status"]): Import {
  return {
    id: 7,
    source: "B3_TRADES",
    file_name: "neg.xlsx",
    file_hash: "hash",
    status,
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

function renderWizard() {
  return render(
    <MemoryRouter>
      <ImportWizard />
    </MemoryRouter>,
  );
}

async function upload() {
  const user = userEvent.setup();
  await user.upload(screen.getByLabelText("Arquivo"), new File(["x"], "neg.xlsx"));
  await user.click(screen.getByRole("button", { name: "Enviar arquivo" }));
}

beforeEach(() => {
  state.staged = null;
  state.rows = [];
  state.reviewed = null;
  state.applied = null;
  state.positions = [];
  state.allocation = null;
  state.refresh = null;
  state.refreshed = 0;
});

afterEach(cleanup);

describe("the B3 import wizard", () => {
  it("keeps a new file's flow and counts the four server situations", async () => {
    state.staged = { import: importRecord("STAGED"), created: true };
    state.rows = [
      row(1, "new"),
      row(2, "duplicate_in_ledger"),
      row(3, "duplicate_in_file"),
      row(4, "error"),
    ];
    renderWizard();

    await upload();

    expect(screen.getByText("Revisar linhas")).toBeDefined();
    expect(screen.queryByText(/já foi enviado em/)).toBeNull();
    expect(screen.getByText("1 nova(s)")).toBeDefined();
    expect(screen.getByText("1 já no livro")).toBeDefined();
    expect(screen.getByText("1 repetida(s) no arquivo")).toBeDefined();
    expect(screen.getByText("1 com atenção")).toBeDefined();
  });

  it("resumes a staged file on Revisar with its resume banner", async () => {
    state.staged = { import: importRecord("STAGED"), created: false };
    renderWizard();

    await upload();

    expect(screen.getByText("Revisar linhas")).toBeDefined();
    expect(
      screen.getByText(
        "Este arquivo já foi enviado em 14/10/2026 10:00 e aguarda revisão. Retomando a revisão.",
      ),
    ).toBeDefined();
  });

  it("resumes a reviewed file on Confirmar with its resume banner", async () => {
    state.staged = { import: importRecord("REVIEWED"), created: false };
    state.rows = [row(1, "new")];
    renderWizard();

    await upload();

    expect(screen.getByText("Confirmar importação")).toBeDefined();
    expect(
      screen.getByText("Este arquivo já foi revisado em 14/10/2026 10:00. Retome para confirmar."),
    ).toBeDefined();
  });

  it("shows an applied re-upload as a read-only result with its date and the Ativos link", async () => {
    state.staged = { import: importRecord("APPLIED"), created: false };
    state.rows = [row(1, "duplicate_in_ledger")];
    renderWizard();

    await upload();

    expect(screen.getByRole("heading", { name: "Resultado" })).toBeDefined();
    expect(screen.getByText("Este arquivo já foi importado em 14/10/2026 10:00.")).toBeDefined();
    expect(screen.getByText("PETR4")).toBeDefined();
    expect(screen.getByRole("link", { name: "Ver negócios" }).getAttribute("href")).toBe("/trades");
    expect(screen.getByTestId("import-quality-summary").textContent).toContain(
      "0 entradas importadas",
    );
  });

  it("shows a failed re-upload as a result with no rows and a select-another-file action", async () => {
    state.staged = { import: importRecord("FAILED"), created: false };
    renderWizard();

    await upload();

    expect(screen.getByRole("heading", { name: "Resultado" })).toBeDefined();
    expect(
      screen.getByText(
        "Este arquivo falhou no processamento em 14/10/2026 10:00 e não pode ser reutilizado. Selecione outro arquivo.",
      ),
    ).toBeDefined();
    expect(screen.queryByRole("table")).toBeNull();
    expect(screen.getByRole("button", { name: "Selecionar outro arquivo" })).toBeDefined();
  });

  it("drops the resume banner once a resumed staged file is reviewed in this session", async () => {
    state.staged = { import: importRecord("STAGED"), created: false };
    state.reviewed = importRecord("REVIEWED");
    state.rows = [row(1, "new")];
    const user = userEvent.setup();
    renderWizard();

    await upload();
    expect(screen.getByText(/já foi enviado em/)).toBeDefined();
    await user.click(screen.getByRole("button", { name: "Continuar" }));

    expect(screen.getByText("Confirmar importação")).toBeDefined();
    expect(screen.queryByText(/já foi revisado em/)).toBeNull();
  });

  it("lands a fresh file on the result after review and apply, with no resume banner", async () => {
    state.staged = { import: importRecord("STAGED"), created: true };
    state.reviewed = importRecord("REVIEWED");
    state.applied = importRecord("APPLIED");
    state.rows = [row(1, "new")];
    const user = userEvent.setup();
    renderWizard();

    await upload();
    await user.click(screen.getByRole("button", { name: "Continuar" }));

    expect(
      screen.getByText(
        /Resumo da revisão: 1 nova\(s\), 0 já no livro, 0 repetida\(s\) no arquivo, 0 com atenção\./,
      ),
    ).toBeDefined();

    await user.click(screen.getByRole("button", { name: "Importar definitivamente" }));

    expect(screen.getByRole("heading", { name: "Resultado" })).toBeDefined();
    expect(screen.queryByText(/já foi importado em/)).toBeNull();
    expect(screen.getByText("PETR4")).toBeDefined();
  });

  it("summarises the imported entries and the remaining data quality on the result", async () => {
    state.staged = { import: importRecord("STAGED"), created: true };
    state.reviewed = importRecord("REVIEWED");
    state.applied = importRecord("APPLIED");
    state.rows = [row(1, "new"), row(2, "duplicate_in_file"), row(3, "error")];
    state.positions = [
      { ticker: "PETR4", quantity: 10, average_price: 30 },
      { ticker: "VALE3", quantity: 5, average_price: 10 },
      { ticker: "BCFF11", quantity: -10, average_price: 8 },
    ];
    state.allocation = {
      capital: 0,
      floor: 0,
      ideal: 0,
      actual: 0,
      deficit: 0,
      drift: 0,
      unattributed: 0,
      partial: true,
      unpriced: [
        { ticker: "VALE3", quantity: 5 },
        { ticker: "PETR4", quantity: 10 },
      ],
      negative: [{ ticker: "BCFF11", quantity: -10 }],
      unclassified: {
        actual: 0,
        tickers: [{ ticker: "ZZZZ9", actual: undefined, quantity: 1 }],
      },
      classes: [],
    };
    state.refresh = {
      catalog: 2,
      quotes: 3,
      as_of: "2026-10-13",
      failed: [],
      unclassified: [],
    };
    const user = userEvent.setup();
    renderWizard();

    await upload();
    await user.click(screen.getByRole("button", { name: "Continuar" }));
    await user.click(screen.getByRole("button", { name: "Importar definitivamente" }));

    const summary = await screen.findByTestId("import-quality-summary");
    expect(summary.textContent).toContain("2 entradas importadas");
    expect(summary.textContent).toContain("3 posições na carteira");
    expect(summary.textContent).toContain("1 negativa");
    expect(summary.textContent).toContain("1 sem classe");
    expect(summary.textContent).toContain("2 sem cotação");
    expect(screen.getByRole("link", { name: "negativa" }).getAttribute("href")).toBe("/history");

    await user.click(screen.getByRole("button", { name: "Atualizar catálogo e cotações" }));
    expect(state.refreshed).toBe(1);
    expect(screen.getByTestId("catalog-refresh-report").textContent).toContain("Catálogo: 2");
    expect(screen.getByTestId("catalog-refresh-report").textContent).toContain(
      "Fechamento: 13/10/2026",
    );
  });
});
