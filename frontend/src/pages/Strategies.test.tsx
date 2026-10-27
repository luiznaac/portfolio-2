// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { Edition, Strategy, StrategyHoldings } from "../api/types.ts";
import { Strategies } from "./Strategies.tsx";

const mock = vi.hoisted(() => ({
  strategies: [] as Strategy[],
  editions: {} as Record<number, Edition[]>,
  holdings: {} as Record<number, StrategyHoldings>,
  createError: null as Error | null,
}));

vi.mock("../api/client.ts", () => ({
  api: {
    getStrategies: () => Promise.resolve(mock.strategies),
    getEditions: (strategyId: number) => Promise.resolve(mock.editions[strategyId] ?? []),
    getStrategyHoldings: (strategyId: number) =>
      Promise.resolve(
        mock.holdings[strategyId] ?? {
          strategy_id: strategyId,
          strategy_name: "",
          total_value: 0,
          holdings: [],
        },
      ),
    getCurrentMonth: () => Promise.resolve({ month: { period: "2026-09" } }),
    createStrategy: (creation: Omit<Strategy, "id">) => {
      if (mock.createError) return Promise.reject(mock.createError);
      const created = { id: 99, ...creation };
      mock.strategies = [...mock.strategies, created];
      return Promise.resolve(created);
    },
  },
}));

function renderStrategies(path = "/strategies") {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Strategies />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function report(id: number, name: string): Strategy {
  return { id, name, asset_class: "ACOES", kind: "REPORT_DRIVEN" };
}

function edition(strategyId: number, status: Edition["status"]): Edition {
  return {
    id: strategyId * 10,
    strategy_id: strategyId,
    reference_month: "2026-09",
    status,
    entries: [],
  };
}

afterEach(() => {
  cleanup();
  mock.strategies = [];
  mock.editions = {};
  mock.holdings = {};
  mock.createError = null;
});

describe("Strategies catalog", () => {
  it("lists the bucket last and counts report-driven coverage only", async () => {
    mock.strategies = [
      { id: 9, name: "Legado", asset_class: "ACOES", kind: "MANUAL" },
      report(1, "Top Dividendos"),
      report(2, "Small Caps"),
      report(3, "Top Ações"),
    ];
    mock.editions = {
      1: [edition(1, "ACTIVE")],
      2: [edition(2, "STAGED")],
    };

    renderStrategies();

    expect(await screen.findByText("2 de 3 com edição")).toBeDefined();
    expect(screen.getByRole("heading", { name: "Top Dividendos" })).toBeDefined();

    const nav = screen.getByRole("navigation");
    const rows = within(nav)
      .getAllByRole("button")
      .map((button) => button.textContent ?? "");
    expect(rows.at(-1)).toContain("Legado");
    expect(rows[0]).toContain("Top Dividendos");
    expect(rows[0]).toContain("set/26 ✓");
    expect(rows[1]).toContain("Small Caps");
    expect(rows[1]).toContain("set/26 · revisão");
    expect(rows[2]).toContain("Top Ações");
    expect(rows[2]).toContain("sem edição");
  });
});

describe("the report import", () => {
  it("offers no report destination and keeps import disabled when only a bucket exists", async () => {
    mock.strategies = [{ id: 9, name: "Legado", asset_class: "ACOES", kind: "MANUAL" }];

    const { container } = renderStrategies("/strategies/import");
    const user = userEvent.setup();
    const input = container.querySelector('input[type="file"]') as HTMLInputElement;
    await user.upload(
      input,
      new File(["report"], "Carteira Top Dividendos.pdf", { type: "application/pdf" }),
    );

    expect(await screen.findByText("Nenhuma estratégia com relatório")).toBeDefined();
    expect(
      (screen.getByRole("button", { name: "Importar pendentes" }) as HTMLButtonElement).disabled,
    ).toBe(true);
  });
});

describe("the manual Strategy detail", () => {
  it("shows the bucket's read-only holdings, total and a link to Alocação", async () => {
    mock.strategies = [{ id: 9, name: "Legado Ações", asset_class: "ACOES", kind: "MANUAL" }];
    mock.holdings = {
      9: {
        strategy_id: 9,
        strategy_name: "Legado Ações",
        total_value: 200,
        holdings: [
          { ticker: "ITUB4", quantity: 20, value: 100, share: 50 },
          { ticker: "VALE3", quantity: 10, value: 100, share: 50 },
        ],
      },
    };

    renderStrategies();

    expect(await screen.findByRole("heading", { name: "Legado Ações" })).toBeDefined();
    expect(screen.getByText("ITUB4")).toBeDefined();
    expect(screen.getByText("VALE3")).toBeDefined();
    expect(screen.getAllByText("50,00%")).toHaveLength(2);
    expect(screen.getByText("Total do legado")).toBeDefined();
    expect(screen.getByText("R$ 200,00")).toBeDefined();
    expect(screen.getByRole("link", { name: /Alocação/ })).toBeDefined();
    expect(screen.queryByRole("link", { name: "Importar relatório" })).toBeNull();
    expect(screen.queryByText("Mudanças vs. edição anterior")).toBeNull();
  });

  it("shows the empty state when no position is attributed", async () => {
    mock.strategies = [{ id: 9, name: "Legado Ações", asset_class: "ACOES", kind: "MANUAL" }];

    renderStrategies();

    expect(
      await screen.findByText(
        "Nenhuma posição atribuída. Use Atribuir posições fora da estratégia na Alocação.",
      ),
    ).toBeDefined();
  });
});

describe("the create modal", () => {
  it("disables Manual for a class that already has a bucket", async () => {
    mock.strategies = [
      report(1, "Top Dividendos"),
      { id: 9, name: "Legado", asset_class: "ACOES", kind: "MANUAL" },
    ];

    renderStrategies();
    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: "Nova estratégia" }));

    expect(screen.getByRole("dialog", { name: "Nova estratégia" })).toBeDefined();
    expect((screen.getByLabelText("Manual (legado)") as HTMLInputElement).disabled).toBe(true);
    expect(screen.getByText("Ações já tem uma estratégia manual.")).toBeDefined();

    await user.selectOptions(screen.getByLabelText("Classe"), "FIIS");

    expect((screen.getByLabelText("Manual (legado)") as HTMLInputElement).disabled).toBe(false);
  });

  it("keeps the modal open and shows a server rejection inline", async () => {
    mock.strategies = [report(1, "Top Dividendos")];
    mock.createError = new Error("This asset class already has a manual strategy");

    renderStrategies();
    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: "Nova estratégia" }));
    await user.type(screen.getByLabelText("Nome"), "Legado FIIs");
    await user.selectOptions(screen.getByLabelText("Classe"), "FIIS");
    await user.click(screen.getByLabelText("Manual (legado)"));
    await user.click(screen.getByRole("button", { name: "Criar estratégia" }));

    expect(await screen.findByText("This asset class already has a manual strategy")).toBeDefined();
    expect(screen.getByRole("dialog", { name: "Nova estratégia" })).toBeDefined();
  });

  it("selects the new strategy after create", async () => {
    mock.strategies = [report(1, "Top Dividendos")];

    renderStrategies();
    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: "Nova estratégia" }));
    await user.type(screen.getByLabelText("Nome"), "Small Caps");
    await user.click(screen.getByRole("button", { name: "Criar estratégia" }));

    expect(await screen.findByRole("heading", { name: "Small Caps" })).toBeDefined();
    expect(screen.queryByRole("dialog", { name: "Nova estratégia" })).toBeNull();
  });
});
