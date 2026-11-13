// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { ImportAttempt, ImportSourceStatus } from "../api/types.ts";
import { ImportSources } from "./ImportSources.tsx";

const mock = vi.hoisted(() => ({
  sources: [] as ImportSourceStatus[],
}));

vi.mock("../api/client.ts", () => ({
  api: {
    getImportSources: () => Promise.resolve(mock.sources),
  },
}));

function attempt(overrides: Partial<ImportAttempt>): ImportAttempt {
  return {
    import_id: 1,
    file_name: "negocios.xlsx",
    status: "APPLIED",
    created_at: "2026-11-10T09:00:00",
    ...overrides,
  };
}

function status(overrides: Partial<ImportSourceStatus>): ImportSourceStatus {
  return {
    source: "B3_TRADES",
    last_attempt: attempt({}),
    last_success: attempt({}),
    pending: 0,
    ...overrides,
  };
}

function renderSources() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={["/imports/sources"]}>
        <ImportSources />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

afterEach(() => {
  cleanup();
  mock.sources = [];
});

describe("Importações · Fontes", () => {
  it("renders one card per source with its last attempt, last success and pending count", async () => {
    mock.sources = [
      status({ source: "B3_TRADES" }),
      status({
        source: "B3_POSITION",
        last_attempt: attempt({ import_id: 3, file_name: "posicao.xlsx", status: "STAGED" }),
        last_success: undefined,
        pending: 1,
      }),
    ];
    renderSources();

    const trades = within(await screen.findByTestId("source-B3_TRADES"));
    expect(trades.getByText("B3 · Negócios")).toBeDefined();
    expect(trades.getByText("Extrato de negócios fechados")).toBeDefined();
    expect(trades.getByText("Aplicada")).toBeDefined();
    expect(trades.getAllByText("negocios.xlsx")).toHaveLength(2);
    expect(trades.getByText("Nenhuma sessão em revisão")).toBeDefined();

    const custody = within(screen.getByTestId("source-B3_POSITION"));
    expect(custody.getByText("B3 · Custódia")).toBeDefined();
    expect(custody.getByText("Aguardando revisão")).toBeDefined();
    expect(custody.getByText("posicao.xlsx")).toBeDefined();
    expect(custody.getByText("1 em revisão")).toBeDefined();
  });

  it("shows the failed file beside the accepted one, each opening its own record", async () => {
    mock.sources = [
      status({
        source: "XP_REPORT",
        last_attempt: attempt({
          import_id: 4,
          file_name: "relatorio.pdf",
          status: "FAILED",
          created_at: "2026-11-11T09:00:00",
        }),
        last_success: attempt({
          import_id: 2,
          file_name: "relatorio.csv",
          created_at: "2026-11-09T09:00:00",
        }),
        failure: attempt({
          import_id: 4,
          file_name: "relatorio.pdf",
          status: "FAILED",
          created_at: "2026-11-11T09:00:00",
        }),
      }),
    ];
    renderSources();

    const card = within(await screen.findByTestId("source-XP_REPORT"));
    expect(card.getAllByText("Falhou")).toHaveLength(2);
    expect(card.getByText("Aplicada")).toBeDefined();

    const failed = card.getByRole("link", { name: "relatorio.pdf" });
    expect(failed.getAttribute("href")).toBe("/imports/assistant/4");

    const accepted = card.getByRole("link", { name: "relatorio.csv" });
    expect(accepted.getAttribute("href")).toBe("/imports/assistant/2");
  });

  it("renders the healthy state when the latest attempt did not fail", async () => {
    mock.sources = [
      status({
        source: "B3_MOVEMENTS",
        last_attempt: attempt({ status: "REVIEWED" }),
        last_success: undefined,
        pending: 2,
      }),
    ];
    renderSources();

    const card = within(await screen.findByTestId("source-B3_MOVEMENTS"));
    expect(card.getByText("Sem falhas na última tentativa.")).toBeDefined();
    expect(card.queryByText("Falhou")).toBeNull();
    expect(card.getByText("2 em revisão")).toBeDefined();
  });

  it("renders the empty state when no source has attempts", async () => {
    renderSources();

    expect(await screen.findByText(/Nenhuma fonte com tentativas ainda/)).toBeDefined();
    expect(screen.queryByTestId("source-B3_TRADES")).toBeNull();
  });
});
