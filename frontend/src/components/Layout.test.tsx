// @vitest-environment jsdom
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { AttentionItem, PortfolioStatus } from "../api/types.ts";
import { Layout } from "./Layout.tsx";

const mock = vi.hoisted(() => ({ status: null as PortfolioStatus | null }));

vi.mock("../api/queries.ts", () => ({
  usePortfolioStatus: () => ({
    data: mock.status,
    isLoading: mock.status === null,
    isError: false,
  }),
}));

function attention(overrides: Partial<AttentionItem>): AttentionItem {
  return {
    kind: "PENDING_TRANSFER",
    object: "MONTH",
    severity: "ACTION",
    ref: { type: "TRANSFER", id: 1 },
    params: {},
    ...overrides,
  };
}

function status(overrides: Partial<PortfolioStatus> = {}): PortfolioStatus {
  return {
    as_of: "2026-11-02",
    open_month: { id: 4, period: "2026-11" },
    capital: 100_000,
    invested: 70_000,
    deficit: 30_000,
    drift: 2.5,
    month: {
      open: true,
      steps_done: 2,
      steps_total: 6,
      suggested_orders: 3,
      pending_transfers: 1,
    },
    data: { quotes_as_of: "2026-10-30", stale_quotes: [], open_gaps: 0 },
    attention: [
      attention({ object: "MONTH" }),
      attention({ object: "IMPORT", kind: "STAGED_IMPORT" }),
      attention({ object: "CORPORATE_ACTION", severity: "WATCH" }),
      attention({ object: "STRATEGY", severity: "WATCH", kind: "STAGED_EDITION" }),
      attention({ object: "ASSET", severity: "WATCH", kind: "OPEN_GAP" }),
    ],
    ...overrides,
  };
}

function renderShell(path = "/portfolio") {
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route element={<Layout />}>
          <Route path="*" element={<div>page</div>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

afterEach(() => {
  cleanup();
  mock.status = null;
});

describe("six-object sidebar", () => {
  it("renders exactly the six objects in their three groups", () => {
    mock.status = status();
    renderShell();

    const nav = screen.getByTestId("sidebar");
    expect(within(nav).getByText("Posição")).toBeDefined();
    expect(within(nav).getByText("Operações")).toBeDefined();
    expect(within(nav).getByText("Resultados")).toBeDefined();

    const links = within(nav)
      .getAllByRole("link")
      .map((link) => link.textContent?.replace(/\d+$/, "").trim());
    expect(links).toEqual([
      "Carteira",
      "Estratégias",
      "Ativos",
      "Importações",
      "Plano / Mês",
      "Análise",
    ]);
  });

  it("never renders a sub-view as a sidebar item", () => {
    mock.status = status();
    renderShell();

    const nav = screen.getByTestId("sidebar");
    for (const subView of ["Visão geral", "Alocação", "Posições", "Negócios", "Fontes"]) {
      expect(within(nav).queryByRole("link", { name: new RegExp(subView) })).toBeNull();
    }
  });

  it("mirrors the attention queue with per-object counts, marking action severity", () => {
    mock.status = status();
    renderShell();

    expect(screen.getByTestId("nav-badge-plan").textContent).toBe("1");
    expect(screen.getByTestId("nav-badge-plan").getAttribute("data-severity")).toBe("ACTION");
    expect(screen.getByTestId("nav-badge-imports").textContent).toBe("2");
    expect(screen.getByTestId("nav-badge-imports").getAttribute("data-severity")).toBe("ACTION");
    expect(screen.getByTestId("nav-badge-strategies").textContent).toBe("1");
    expect(screen.getByTestId("nav-badge-strategies").getAttribute("data-severity")).toBe("WATCH");
    expect(screen.getByTestId("nav-badge-assets").textContent).toBe("1");
    expect(screen.getByTestId("nav-badge-assets").getAttribute("data-severity")).toBe("WATCH");
    expect(screen.queryByTestId("nav-badge-portfolio")).toBeNull();
  });
});

describe("top bar", () => {
  it("resolves group / object / sub-view in the breadcrumb", () => {
    mock.status = status();
    renderShell("/portfolio/allocation");

    expect(screen.getByTestId("breadcrumb").textContent).toBe("Posição / Carteira / Alocação");
  });

  it("names the entity at depth on a detail route", () => {
    mock.status = status();
    renderShell("/ativos/positions/PETR4");

    expect(screen.getByTestId("breadcrumb").textContent).toBe("Posição / Ativos / PETR4");
  });

  it("renders quote freshness and the open-month status from the aggregate", () => {
    mock.status = status();
    renderShell();

    expect(screen.getByTestId("quote-freshness").textContent).toContain("cotações 30/10/2026");
    expect(screen.getByTestId("open-month").textContent).toContain("nov/2026 · aberto");
  });

  it("says no month is open when the aggregate reports none", () => {
    mock.status = status({
      open_month: undefined,
      month: {
        open: false,
        steps_done: 0,
        steps_total: 6,
        suggested_orders: 0,
        pending_transfers: 0,
      },
    });
    renderShell();

    expect(screen.getByTestId("open-month").textContent).toContain("Nenhum mês aberto");
    expect(screen.getByTestId("quote-freshness").textContent).toContain("cotações 30/10/2026");
  });
});

describe("retired command palette", () => {
  it("has no palette trigger and no Ctrl+K shortcut", async () => {
    const user = userEvent.setup();
    mock.status = status();
    renderShell();

    expect(screen.queryByRole("button", { name: /comandos/i })).toBeNull();
    expect(screen.queryByText(/Ctrl K/)).toBeNull();

    await user.keyboard("{Control>}k{/Control}");
    expect(screen.queryByRole("dialog")).toBeNull();
  });
});
