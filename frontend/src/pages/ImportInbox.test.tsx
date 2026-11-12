// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Link, MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { ImportFilters } from "../api/client.ts";
import type { Import } from "../api/types.ts";
import { ImportInbox } from "./ImportInbox.tsx";

const mock = vi.hoisted(() => ({
  imports: [] as Import[],
  calls: [] as ImportFilters[],
}));

// The server read is mocked at the client seam so the page's additive status/source parameters are
// exercised for real: the mock narrows by the filters the page sends, exactly as GET /imports does.
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

function LocationProbe() {
  const location = useLocation();
  return <p data-testid="location-search">{location.search}</p>;
}

function renderInbox() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={["/imports"]}>
        <LocationProbe />
        <nav>
          <Link to="/imports">Importações</Link>
          <Link to="/outra">Outra página</Link>
        </nav>
        <Routes>
          <Route path="/imports" element={<ImportInbox />} />
          <Route path="/outra" element={<p>outra página</p>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function queueRows() {
  return within(screen.getByRole("table")).getAllByRole("row");
}

afterEach(() => {
  cleanup();
  mock.imports = [];
  mock.calls = [];
});

describe("Importações · Caixa de entrada", () => {
  it("lists only parked sessions, newest first, with file, source, destination, state and age", async () => {
    mock.imports = [
      record({ id: 7, status: "STAGED", file_name: "negocios-outubro.xlsx" }),
      record({
        id: 8,
        status: "REVIEWED",
        source: "B3_POSITION",
        destination: { object_type: "POSITIONS" },
        file_name: "posicao-outubro.xlsx",
        created_at: "2026-11-11T09:00:00",
      }),
      record({
        id: 9,
        status: "STAGED",
        source: "XP_REPORT",
        destination: { object_type: "STRATEGY_EDITION" },
        file_name: "relatorio-novembro.csv",
        created_at: "2026-11-12T09:00:00",
      }),
      record({ id: 10, status: "APPLIED", file_name: "negocios-setembro.xlsx" }),
    ];
    renderInbox();

    expect(await screen.findByText("3 de 3")).toBeDefined();
    expect(queueRows()).toHaveLength(4);
    expect(screen.queryByText("negocios-setembro.xlsx")).toBeNull();

    const table = within(screen.getByRole("table"));
    expect(table.getByText("negocios-outubro.xlsx")).toBeDefined();
    expect(table.getByText("B3 · Custódia")).toBeDefined();
    expect(table.getByText("Ativos · Posições")).toBeDefined();
    expect(table.getByText("Estratégias · Edições")).toBeDefined();
    expect(table.getByText("Idade")).toBeDefined();
    expect(table.getAllByText("Aguardando revisão")).toHaveLength(2);
    expect(table.getAllByText("Revisada")).toHaveLength(1);

    expect(screen.getByRole("link", { name: "Enviar arquivo →" }).getAttribute("href")).toBe(
      "/imports/assistant?object=TRADES",
    );
    expect(screen.getAllByRole("link", { name: "Retomar →" })[0].getAttribute("href")).toBe(
      "/imports/assistant/9",
    );
  });

  it("narrows through the shared bar, sends fonte and estado to the server and keeps the total", async () => {
    mock.imports = [
      record({ id: 7, status: "STAGED", file_name: "negocios-outubro.xlsx" }),
      record({
        id: 8,
        status: "REVIEWED",
        source: "B3_POSITION",
        file_name: "posicao-outubro.xlsx",
      }),
      record({ id: 9, status: "STAGED", file_name: "negocios-novembro.xlsx" }),
    ];
    const user = userEvent.setup();
    renderInbox();

    expect(await screen.findByText("3 de 3")).toBeDefined();

    const file = screen.getByLabelText("Arquivo");
    await user.type(file, "posicao");

    expect(screen.getByText("1 de 3")).toBeDefined();
    expect(queueRows()).toHaveLength(2);
    expect((file as HTMLInputElement).value).toBe("posicao");
    expect(document.activeElement).toBe(file);
    expect(screen.getByTestId("location-search").textContent).toBe("");
    expect(screen.getByRole("button", { name: "limpar" })).toBeDefined();

    await user.click(screen.getByRole("button", { name: "limpar" }));
    expect(await screen.findByText("3 de 3")).toBeDefined();

    await user.selectOptions(screen.getByLabelText("Fonte"), "B3_TRADES");
    expect(await screen.findByText("2 de 3")).toBeDefined();
    expect(mock.calls.some((call) => call.source === "B3_TRADES")).toBe(true);

    await user.selectOptions(screen.getByLabelText("Fonte"), "");
    await screen.findByText("3 de 3");
    await user.selectOptions(screen.getByLabelText("Estado"), "REVIEWED");
    expect(await screen.findByText("1 de 3")).toBeDefined();
    expect(mock.calls.some((call) => call.status === "REVIEWED")).toBe(true);
  });

  it("clears every field when the page is left and re-entered", async () => {
    mock.imports = [record({ id: 7, file_name: "negocios.xlsx" })];
    const user = userEvent.setup();
    renderInbox();

    await user.type(await screen.findByLabelText("Arquivo"), "neg");
    expect(screen.getByText("1 de 1")).toBeDefined();

    await user.click(screen.getByRole("link", { name: "Outra página" }));
    expect(screen.getByText("outra página")).toBeDefined();

    await user.click(screen.getByRole("link", { name: "Importações" }));
    expect(await screen.findByText("1 de 1")).toBeDefined();
    expect((screen.getByLabelText("Arquivo") as HTMLInputElement).value).toBe("");
  });

  it("offers the same clear action from the narrowing empty state", async () => {
    mock.imports = [record({ id: 7, file_name: "negocios.xlsx" })];
    const user = userEvent.setup();
    renderInbox();

    await user.type(await screen.findByLabelText("Arquivo"), "zzz");

    expect(screen.getByText("Nenhuma linha com esse filtro.")).toBeDefined();
    expect(screen.queryByRole("table")).toBeNull();

    await user.click(screen.getAllByRole("button", { name: "limpar" })[1]);
    expect(await screen.findByText("1 de 1")).toBeDefined();
    expect(queueRows()).toHaveLength(2);
  });
});
