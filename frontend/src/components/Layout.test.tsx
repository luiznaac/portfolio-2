// @vitest-environment jsdom
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { Layout } from "./Layout.tsx";

vi.mock("../api/queries.ts", () => ({
  useCurrentMonth: () => ({
    isLoading: false,
    isError: false,
    data: {
      month: {
        id: 10,
        period: "2026-10",
        capital: 250_000,
        floor: 20_000,
        status: "OPEN",
        aportes: 0,
      },
      targets: { classes: [], strategies: [], sub_classes: [], tickers: [] },
    },
  }),
  useOrders: () => ({
    isLoading: false,
    isError: false,
    data: {
      period: "2026-10",
      orders: [],
      transfers: [
        {
          id: 7,
          ticker: "ITUB4",
          from_strategy_id: 1,
          from_strategy: "Top Dividendos",
          to_strategy_id: 2,
          to_strategy: "Small Caps",
          quantity: 10,
          auto_approved: false,
          status: "PENDING",
        },
      ],
      unplanned_trades: [],
    },
  }),
}));

function renderShell() {
  render(
    <MemoryRouter initialEntries={["/taxes"]}>
      <Routes>
        <Route element={<Layout />}>
          <Route path="taxes" element={<div>Impostos page</div>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

afterEach(cleanup);

describe("Layout shell", () => {
  it("shows the open month and its pending transfers in the month pill", () => {
    renderShell();

    expect(screen.getByText("out/2026")).toBeDefined();
    expect(screen.getByText("Aberto")).toBeDefined();
    expect(screen.getByText("1 pendente")).toBeDefined();
  });

  it("opens the command palette with Ctrl+K and closes it on Escape", async () => {
    const user = userEvent.setup();
    renderShell();

    await user.keyboard("{Control>}k{/Control}");
    expect(screen.getByRole("dialog", { name: "Comandos" })).toBeDefined();

    await user.keyboard("{Escape}");
    expect(screen.queryByRole("dialog", { name: "Comandos" })).toBeNull();
  });

  it("opens the command palette from the topbar button", async () => {
    const user = userEvent.setup();
    renderShell();

    await user.click(screen.getByRole("button", { name: /Comandos/ }));

    expect(screen.getByRole("dialog", { name: "Comandos" })).toBeDefined();
  });
});
