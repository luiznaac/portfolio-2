// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { EditionSummary, Strategy } from "../api/types.ts";
import { Strategies } from "./Strategies.tsx";

const mock = vi.hoisted(() => ({
  strategies: [] as Strategy[],
  allocation: { classes: [] } as unknown,
  editionsCalls: 0,
  createError: null as Error | null,
}));

vi.mock("../api/client.ts", () => ({
  api: {
    getStrategies: () => Promise.resolve(mock.strategies),
    getCurrentAllocation: () => Promise.resolve(mock.allocation),
    getEditions: () => {
      mock.editionsCalls++;
      return Promise.resolve([]);
    },
    createStrategy: (creation: Omit<Strategy, "id">) => {
      if (mock.createError) return Promise.reject(mock.createError);
      const created = { id: 99, ...creation };
      mock.strategies = [...mock.strategies, created];
      return Promise.resolve(created);
    },
  },
}));

function renderStrategies() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={["/strategies"]}>
        <Strategies />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function report(
  id: number,
  name: string,
  assetClass: Strategy["asset_class"] = "ACOES",
  latest?: EditionSummary,
): Strategy {
  return {
    id,
    name,
    asset_class: assetClass,
    kind: "REPORT_DRIVEN",
    latest_edition: latest,
  };
}

function cellsOf(row: HTMLElement): (string | null)[] {
  return within(row)
    .getAllByRole("cell")
    .map((cell) => cell.textContent);
}

function rows(): string[][] {
  return screen
    .getAllByRole("row")
    .slice(1)
    .map((row) => cellsOf(row) as string[]);
}

afterEach(() => {
  cleanup();
  mock.strategies = [];
  mock.allocation = { classes: [] };
  mock.editionsCalls = 0;
  mock.createError = null;
});

describe("Estratégias · Todas", () => {
  it("compares every strategy by class, kind, latest edition, value and drift", async () => {
    mock.strategies = [
      report(1, "Top Dividendos", "ACOES", {
        id: 11,
        reference_month: "2026-09",
        status: "ACTIVE",
      }),
      report(2, "Small Caps", "FIIS", {
        id: 12,
        reference_month: "2026-10",
        status: "STAGED",
      }),
      { id: 9, name: "Legado", asset_class: "ACOES", kind: "MANUAL" },
      report(3, "Top Ações"),
    ];
    mock.allocation = {
      classes: [
        {
          strategies: [{ strategy: "Top Dividendos", actual: 12000, drift: 2.4 }],
          manual: { strategy: "Legado", actual: 3000 },
        },
        { strategies: [{ strategy: "Small Caps", actual: 5000, drift: -1.2 }] },
      ],
    };

    renderStrategies();

    expect(await screen.findByRole("row", { name: /Top Dividendos/ })).toBeDefined();
    expect(rows()).toEqual([
      ["Top Dividendos", "Ações", "Com relatório", "set/26 ✓", "R$ 12.000,00", "+2,4 p.p."],
      ["Top Ações", "Ações", "Com relatório", "—", "—", "—"],
      ["Legado", "Ações", "Manual (legado)", "—", "R$ 3.000,00", "—"],
      [
        "Small Caps",
        "Fundos imob.",
        "Com relatório",
        "out/26 · revisão",
        "R$ 5.000,00",
        "-1,2 p.p.",
      ],
    ]);
  });

  it("navigates each row to the strategy detail without fanning out editions calls", async () => {
    mock.strategies = [report(1, "Top Dividendos")];

    renderStrategies();

    const link = await screen.findByRole("link", { name: "Top Dividendos" });
    expect(link.getAttribute("href")).toBe("/strategies/1");
    expect(mock.editionsCalls).toBe(0);
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

  it("closes on create and lists the new strategy", async () => {
    mock.strategies = [report(1, "Top Dividendos")];

    renderStrategies();
    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: "Nova estratégia" }));
    await user.type(screen.getByLabelText("Nome"), "Small Caps");
    await user.click(screen.getByRole("button", { name: "Criar estratégia" }));

    expect(await screen.findByRole("link", { name: "Small Caps" })).toBeDefined();
    expect(screen.queryByRole("dialog", { name: "Nova estratégia" })).toBeNull();
  });
});
