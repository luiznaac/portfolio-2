// @vitest-environment jsdom
import { cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it } from "vitest";
import { PageHead } from "./PageHead.tsx";
import { PageTabs } from "./PageTabs.tsx";

afterEach(cleanup);

describe("PageHead", () => {
  it("renders the object title as the page heading", () => {
    render(<PageHead title="Carteira" subtitle="nov/26 · posição" />);
    expect(screen.getByRole("heading", { level: 1, name: "Carteira" })).toBeDefined();
    expect(screen.getByText("nov/26 · posição")).toBeDefined();
  });

  it("holds the 26 px floor and reserves the subtitle line, so the tab strip cannot jump", () => {
    render(
      <>
        <PageHead title="Carteira" />
        <PageHead title="Estratégias" subtitle="comparação de todas as estratégias" />
      </>,
    );
    const heads = screen.getAllByTestId("page-head");
    expect(heads).toHaveLength(2);
    for (const head of heads) {
      expect(head.className).toContain("min-h-[var(--height-pagehead)]");
    }
    const subtitles = screen.getAllByTestId("page-subtitle");
    expect(subtitles).toHaveLength(2);
    for (const subtitle of subtitles) {
      expect(subtitle.className).toContain("truncate");
    }
    expect(subtitles[0].getAttribute("aria-hidden")).toBe("true");
  });

  it("renders caller actions on the right of the head", () => {
    render(<PageHead title="Ativos" actions={<button type="button">Nova posição</button>} />);
    expect(screen.getByRole("button", { name: "Nova posição" })).toBeDefined();
  });
});

describe("PageTabs", () => {
  function renderTabs(initial: string) {
    render(
      <MemoryRouter initialEntries={[initial]}>
        <PageTabs
          tabs={[
            { to: "/strategies", label: "Carteira", end: true },
            { to: "/strategies/edicoes", label: "Edições" },
            { to: "/strategies/atribuicao", label: "Atribuição" },
          ]}
          trailing="nov/26 · Estratégias"
        />
      </MemoryRouter>,
    );
  }

  it("renders the sub-views as page tabs under the head", () => {
    renderTabs("/strategies/edicoes");
    expect(screen.getByRole("navigation", { name: "Sub-views" })).toBeDefined();
    expect(screen.getAllByRole("link")).toHaveLength(3);
  });

  it("marks the current sub-view", () => {
    renderTabs("/strategies/edicoes");
    const active = screen.getByRole("link", { name: "Edições" });
    expect(active.getAttribute("aria-current")).toBe("page");
    expect(active.className).toContain("border-action");
    expect(screen.getByRole("link", { name: "Carteira" }).getAttribute("aria-current")).toBeNull();
  });

  it("renders the trailing context in the strip", () => {
    renderTabs("/strategies");
    expect(screen.getByText("nov/26 · Estratégias")).toBeDefined();
  });
});
