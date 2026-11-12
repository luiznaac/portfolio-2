// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { ImportFilters } from "../api/client.ts";
import type { Import } from "../api/types.ts";
import { ImportHistory } from "./ImportHistory.tsx";

const mock = vi.hoisted(() => ({
  imports: [] as Import[],
  calls: [] as ImportFilters[],
}));

// The history reads the same additive GET /imports parameters as the inbox; the mock narrows the
// way the server does so the page's status/source filters are exercised end to end.
vi.mock("../api/client.ts", () => ({
  api: {
    getImports: (filters: ImportFilters = {}) => {
      mock.calls.push(filters);
      return Promise.resolve(
        mock.imports.filter(
          (row) =>
            (filters.status === undefined || row.status === filters.status) &&
            (filters.source === undefined || row.source === filters.source),
        ),
      );
    },
  },
}));

function record(overrides: Partial<Import>): Import {
  return {
    id: 1,
    source: "B3_TRADES",
    file_name: "negocios.xlsx",
    file_hash: "hash",
    status: "STAGED",
    destination: { object_type: "TRADES" },
    has_original: true,
    created_at: "2026-11-10T09:00:00",
    ...overrides,
  };
}

function renderHistory() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={["/imports/history"]}>
        <ImportHistory />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function logRows() {
  return within(screen.getByRole("table")).getAllByRole("row");
}

afterEach(() => {
  cleanup();
  mock.imports = [];
  mock.calls = [];
});

describe("Importações · Histórico", () => {
  it("lists the whole import log, newest first, with the same columns", async () => {
    mock.imports = [
      record({ id: 1, file_name: "negocios.xlsx", status: "STAGED" }),
      record({
        id: 2,
        source: "B3_POSITION",
        destination: { object_type: "POSITIONS" },
        file_name: "posicao.xlsx",
        status: "APPLIED",
        created_at: "2026-11-11T09:00:00",
      }),
      record({
        id: 3,
        source: "XP_REPORT",
        destination: { object_type: "STRATEGY_EDITION" },
        file_name: "relatorio.csv",
        status: "FAILED",
        created_at: "2026-11-12T09:00:00",
      }),
    ];
    renderHistory();

    expect(await screen.findByText("3 de 3")).toBeDefined();
    expect(logRows()).toHaveLength(4);

    const firstRow = within(logRows()[1]);
    expect(firstRow.getByText("relatorio.csv")).toBeDefined();
    expect(firstRow.getByText("XP · Relatório")).toBeDefined();
    expect(firstRow.getByText("Estratégias · Edições")).toBeDefined();
    expect(firstRow.getByText("Falhou")).toBeDefined();

    const table = within(screen.getByRole("table"));
    expect(table.getByText("posicao.xlsx")).toBeDefined();
    expect(table.getByText("Aplicada")).toBeDefined();
    expect(table.getByText("Idade")).toBeDefined();
  });

  it("narrows by file, fonte and estado, with the additive server parameters and the full total", async () => {
    mock.imports = [
      record({ id: 1, file_name: "negocios-outubro.xlsx", status: "APPLIED" }),
      record({ id: 2, file_name: "negocios-novembro.xlsx", status: "STAGED" }),
      record({
        id: 3,
        source: "B3_POSITION",
        file_name: "posicao.xlsx",
        status: "APPLIED",
      }),
      record({ id: 4, source: "XP_REPORT", file_name: "relatorio.csv", status: "FAILED" }),
    ];
    const user = userEvent.setup();
    renderHistory();

    expect(await screen.findByText("4 de 4")).toBeDefined();

    await user.type(screen.getByLabelText("Arquivo"), "negocios");
    expect(screen.getByText("2 de 4")).toBeDefined();
    expect(screen.getByRole("button", { name: "limpar" })).toBeDefined();

    await user.click(screen.getByRole("button", { name: "limpar" }));
    expect(await screen.findByText("4 de 4")).toBeDefined();

    await user.selectOptions(screen.getByLabelText("Estado"), "APPLIED");
    expect(await screen.findByText("2 de 4")).toBeDefined();
    expect(mock.calls.some((call) => call.status === "APPLIED")).toBe(true);

    await user.selectOptions(screen.getByLabelText("Estado"), "");
    await screen.findByText("4 de 4");
    await user.selectOptions(screen.getByLabelText("Fonte"), "XP_REPORT");
    expect(await screen.findByText("1 de 4")).toBeDefined();
    expect(mock.calls.some((call) => call.source === "XP_REPORT")).toBe(true);

    await user.type(screen.getByLabelText("Arquivo"), "sem-resultado");
    expect(screen.getByText("Nenhuma linha com esse filtro.")).toBeDefined();
    expect(screen.queryByRole("table")).toBeNull();
  });
});
