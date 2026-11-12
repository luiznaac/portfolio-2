// @vitest-environment jsdom
import { cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { Import, ImportDestinationObject, ImportStatus } from "../api/types.ts";
import { ResumableImportStrip } from "./ResumableImportStrip.tsx";

const state = vi.hoisted(() => ({ imports: [] as Import[] }));

vi.mock("../api/queries.ts", () => ({
  useImports: () => ({ data: state.imports }),
}));

function importRecord(
  id: number,
  status: ImportStatus,
  object: ImportDestinationObject,
  fileName: string,
): Import {
  return {
    id,
    source: "B3_TRADES",
    file_name: fileName,
    file_hash: "hash",
    status,
    destination: { object_type: object },
    has_original: true,
    created_at: "2026-10-14T10:00:00",
  };
}

afterEach(() => {
  cleanup();
  state.imports = [];
});

describe("the resumable import strip", () => {
  it("shows only parked sessions for its destination", () => {
    state.imports = [
      importRecord(7, "STAGED", "TRADES", "neg.xlsx"),
      importRecord(8, "REVIEWED", "TRADES", "outro.xlsx"),
      importRecord(9, "APPLIED", "TRADES", "aplicado.xlsx"),
      importRecord(10, "STAGED", "POSITIONS", "posicao.xlsx"),
    ];
    render(
      <MemoryRouter>
        <ResumableImportStrip destination="TRADES" />
      </MemoryRouter>,
    );

    expect(screen.getByText("neg.xlsx")).toBeDefined();
    expect(screen.getByText("outro.xlsx")).toBeDefined();
    expect(screen.queryByText("aplicado.xlsx")).toBeNull();
    expect(screen.queryByText("posicao.xlsx")).toBeNull();
    expect(screen.getAllByRole("link", { name: "Retomar →" })[0].getAttribute("href")).toBe(
      "/imports/assistant/7",
    );
  });

  it("renders nothing when there is no parked session", () => {
    state.imports = [importRecord(9, "APPLIED", "TRADES", "aplicado.xlsx")];
    const { container } = render(
      <MemoryRouter>
        <ResumableImportStrip />
      </MemoryRouter>,
    );

    expect(container.textContent).toBe("");
  });
});
