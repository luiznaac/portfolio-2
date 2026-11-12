// @vitest-environment jsdom
import { cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it } from "vitest";
import type { ImportApplyResult } from "../api/types.ts";
import { ImportAppliedBanner } from "./ImportAppliedBanner.tsx";

const result: ImportApplyResult = {
  record: {
    id: 7,
    source: "B3_TRADES",
    file_name: "neg.xlsx",
    file_hash: "0123456789abcdef",
    status: "APPLIED",
    destination: { object_type: "TRADES" },
    has_original: true,
    created_at: "2026-10-14T10:00:00",
  },
  applied: 3,
  rejected: 1,
  skipped_duplicate: 2,
};

afterEach(cleanup);

describe("the applied-import banner", () => {
  it("reports the apply counts, the retry batch and the record's provenance", () => {
    render(
      <MemoryRouter
        initialEntries={[{ pathname: "/ativos/trades", state: { importApplied: result } }]}
      >
        <ImportAppliedBanner />
      </MemoryRouter>,
    );

    const banner = screen.getByTestId("import-applied");
    expect(banner.textContent).toContain("3 aplicada(s)");
    expect(banner.textContent).toContain("1 rejeitada(s)");
    expect(banner.textContent).toContain("2 duplicada(s)");
    expect(banner.textContent).toContain("sha256 0123456789ab");
    expect(screen.getByRole("link", { name: "Baixar original" }).getAttribute("href")).toBe(
      "/api/imports/7/file",
    );
    expect(banner.textContent).toContain("lote de reenvio");
  });

  it("renders nothing on a destination visit that did not apply an import", () => {
    const { container } = render(
      <MemoryRouter initialEntries={["/ativos/trades"]}>
        <ImportAppliedBanner />
      </MemoryRouter>,
    );

    expect(container.textContent).toBe("");
  });
});
