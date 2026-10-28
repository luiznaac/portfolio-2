// @vitest-environment jsdom
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { CommandPalette } from "./CommandPalette.tsx";

function Location() {
  const location = useLocation();
  return <div data-testid="location">{location.pathname}</div>;
}

function renderPalette(onClose = vi.fn()) {
  render(
    <MemoryRouter initialEntries={["/"]}>
      <Routes>
        <Route
          path="*"
          element={
            <>
              <Location />
              <CommandPalette onClose={onClose} />
            </>
          }
        />
      </Routes>
    </MemoryRouter>,
  );
  return onClose;
}

afterEach(cleanup);

describe("CommandPalette", () => {
  it("lists the shell's pages", () => {
    renderPalette();
    expect(screen.getByRole("dialog", { name: "Comandos" })).toBeDefined();
    expect(screen.getByRole("button", { name: /Cockpit/ })).toBeDefined();
    expect(screen.getByRole("button", { name: /Ordens/ })).toBeDefined();
  });

  it("filters the list while typing", async () => {
    const user = userEvent.setup();
    renderPalette();

    await user.type(screen.getByLabelText("Buscar tela"), "impost");

    expect(screen.getByRole("button", { name: /Impostos/ })).toBeDefined();
    expect(screen.queryByRole("button", { name: /Cockpit/ })).toBeNull();
  });

  it("navigates to the highlighted command on Enter and closes", async () => {
    const user = userEvent.setup();
    const onClose = renderPalette();

    await user.type(screen.getByLabelText("Buscar tela"), "impost{Enter}");

    expect(screen.getByTestId("location").textContent).toBe("/taxes");
    expect(onClose).toHaveBeenCalled();
  });

  it("walks the list with the arrow keys", async () => {
    const user = userEvent.setup();
    renderPalette();

    await user.type(screen.getByLabelText("Buscar tela"), "mês{ArrowDown}{Enter}");

    expect(screen.getByTestId("location").textContent).toBe("/month");
  });

  it("closes on Escape", async () => {
    const user = userEvent.setup();
    const onClose = renderPalette();

    await user.type(screen.getByLabelText("Buscar tela"), "{Escape}");

    expect(onClose).toHaveBeenCalled();
  });

  it("navigates when a command is clicked", async () => {
    const user = userEvent.setup();
    renderPalette();

    await user.click(screen.getByRole("button", { name: /Fechamento/ }));

    expect(screen.getByTestId("location").textContent).toBe("/fechamento");
  });
});
