// @vitest-environment jsdom
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Link, MemoryRouter, Outlet, Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it } from "vitest";
import { AnalysisWindowProvider, useAnalysisWindow } from "./useAnalysisWindow.tsx";

const PERIODS = ["2025-01", "2025-02", "2026-01", "2026-02"];

function Probe() {
  const rail = useAnalysisWindow(PERIODS, "2026-02");

  return (
    <div>
      <span data-testid="preset">{rail.preset}</span>
      <span data-testid="range">
        {rail.range === undefined ? "none" : `${rail.range.start}..${rail.range.end}`}
      </span>
      <button type="button" onClick={() => rail.setPreset("3m")}>
        3 meses
      </button>
      <button type="button" onClick={() => rail.shift(-1)}>
        Um mês antes
      </button>
      <Link to="/analysis/income">Proventos</Link>
      <Link to="/portfolio">Sair</Link>
    </div>
  );
}

function AnalysisArea() {
  return (
    <AnalysisWindowProvider>
      <Outlet />
    </AnalysisWindowProvider>
  );
}

function Outside() {
  return <Link to="/analysis">Voltar</Link>;
}

function renderHarness() {
  return render(
    <MemoryRouter initialEntries={["/analysis"]}>
      <Routes>
        <Route path="/analysis" element={<AnalysisArea />}>
          <Route index element={<Probe />} />
          <Route path="income" element={<Probe />} />
        </Route>
        <Route path="/portfolio" element={<Outside />} />
      </Routes>
    </MemoryRouter>,
  );
}

function PreferredProbe() {
  const rail = useAnalysisWindow(PERIODS, "2026-02", "2026-01");

  return (
    <div>
      <span data-testid="preferred-range">
        {rail.range === undefined ? "none" : `${rail.range.start}..${rail.range.end}`}
      </span>
      <button type="button" onClick={() => rail.shift(1)}>
        Um mês depois
      </button>
    </div>
  );
}

afterEach(cleanup);

describe("useAnalysisWindow", () => {
  it("opens on 12 months ending the last closed month", () => {
    renderHarness();

    expect(screen.getByTestId("preset").textContent).toBe("12m");
    expect(screen.getByTestId("range").textContent).toBe("2025-03..2026-02");
  });

  it("keeps the window across a tab switch", async () => {
    const user = userEvent.setup();
    renderHarness();

    await user.click(screen.getByText("3 meses"));
    await user.click(screen.getByText("Um mês antes"));
    expect(screen.getByTestId("range").textContent).toBe("2025-11..2026-01");

    await user.click(screen.getByText("Proventos"));
    expect(screen.getByTestId("preset").textContent).toBe("3m");
    expect(screen.getByTestId("range").textContent).toBe("2025-11..2026-01");
  });

  it("honours a page's preferred anchor until the owner moves the rail", async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <AnalysisWindowProvider>
          <PreferredProbe />
        </AnalysisWindowProvider>
      </MemoryRouter>,
    );

    expect(screen.getByTestId("preferred-range").textContent).toBe("2025-02..2026-01");

    await user.click(screen.getByText("Um mês depois"));
    expect(screen.getByTestId("preferred-range").textContent).toBe("2025-03..2026-02");
  });

  it("resets on re-entry to the area", async () => {
    const user = userEvent.setup();
    renderHarness();

    await user.click(screen.getByText("3 meses"));
    expect(screen.getByTestId("preset").textContent).toBe("3m");

    await user.click(screen.getByText("Sair"));
    await user.click(screen.getByText("Voltar"));
    expect(screen.getByTestId("preset").textContent).toBe("12m");
    expect(screen.getByTestId("range").textContent).toBe("2025-03..2026-02");
  });
});
