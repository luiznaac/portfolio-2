// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { CorporateAction, CorporateActionResolution } from "../api/types.ts";
import { CorporateActions } from "./CorporateActions.tsx";

const mock = vi.hoisted(() => ({
  actions: [] as CorporateAction[],
  resolutions: [] as { id: number; resolution: CorporateActionResolution }[],
  failWith: null as Error | null,
}));

// The client seam is mocked so the real query and mutation hooks run: the resolve writes into the
// in-memory log and the invalidation refetches it, which is exactly the "moves without a reload"
// outcome the ticket asks for.
vi.mock("../api/client.ts", () => ({
  api: {
    getCorporateActions: () => Promise.resolve(mock.actions.map((action) => ({ ...action }))),
    importMovements: () =>
      Promise.resolve({ trades: 0, incomes: 0, corporate_actions: [], reuploaded: false }),
    resolveCorporateAction: (id: number, resolution: CorporateActionResolution) => {
      mock.resolutions.push({ id, resolution });
      if (mock.failWith !== null) return Promise.reject(mock.failWith);
      const action = mock.actions.find((candidate) => candidate.id === id);
      if (action === undefined) return Promise.reject(new Error(`missing action ${id}`));
      action.status = "APPLIED";
      action.is_applied = true;
      return Promise.resolve({ ...action });
    },
  },
}));

function action(overrides: Partial<CorporateAction> = {}): CorporateAction {
  return {
    id: 1,
    ticker: "PETR4",
    kind: "SPLIT",
    ex_date: "2026-10-20",
    ratio: 2,
    source: "B3_MOVEMENTS",
    raw_type: "Desdobramento",
    ordinal: 1,
    status: "APPLIED",
    is_applied: true,
    ...overrides,
  };
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={["/imports/corporate"]}>
        <CorporateActions />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  mock.resolutions = [];
  mock.failWith = null;
  mock.actions = [
    action({ id: 1 }),
    action({
      id: 2,
      ratio: undefined,
      raw_type: "Desdobramento",
      status: "NEEDS_REVIEW",
      is_applied: false,
    }),
    action({
      id: 3,
      ticker: "VALE3",
      kind: "RIGHTS_ISSUE",
      ratio: undefined,
      raw_type: "Subscrição",
      status: "NEEDS_REVIEW",
      is_applied: false,
    }),
  ];
});

afterEach(cleanup);

describe("Ações corporativas", () => {
  it("lists the queue with the context to decide and the applied history beside it", async () => {
    renderPage();

    const parked = await screen.findByTestId("corporate-review-2");
    expect(within(parked).getByText("PETR4")).toBeDefined();
    expect(within(parked).getByText("Desdobramento")).toBeDefined();
    expect(within(parked).getByText("fator ausente")).toBeDefined();
    expect(within(parked).getByText("A exportação não trouxe o fator.")).toBeDefined();
    expect(within(parked).getByText(/B3 · Movimentações/)).toBeDefined();
    expect(within(parked).getByLabelText("Fator")).toBeDefined();

    const manual = screen.getByTestId("corporate-review-3");
    expect(within(manual).getAllByText("Subscrição").length).toBeGreaterThan(0);
    expect(
      within(manual).getByText("Sem efeito automático: o motor não aplica Subscrição sozinho."),
    ).toBeDefined();
    expect(within(manual).getByRole("button", { name: "Marcar como tratada" })).toBeDefined();

    const settled = screen.getByTestId("corporate-history-1");
    expect(within(settled).getByText("Aplicado")).toBeDefined();
    expect(screen.queryByTestId("corporate-review-1")).toBeNull();
  });

  it("resolves a computable event with the supplied factor and moves it to the history", async () => {
    const user = userEvent.setup();
    renderPage();

    const parked = await screen.findByTestId("corporate-review-2");
    await user.type(within(parked).getByLabelText("Fator"), "2");
    await user.click(within(parked).getByRole("button", { name: "Aplicar" }));

    await waitFor(() => expect(screen.queryByTestId("corporate-review-2")).toBeNull());
    expect(screen.getByTestId("corporate-history-2")).toBeDefined();
    expect(mock.resolutions).toEqual([{ id: 2, resolution: { ratio: 2 } }]);
  });

  it("acknowledges a manual kind with an empty body", async () => {
    const user = userEvent.setup();
    renderPage();

    const manual = await screen.findByTestId("corporate-review-3");
    await user.click(within(manual).getByRole("button", { name: "Marcar como tratada" }));

    await waitFor(() => expect(screen.queryByTestId("corporate-review-3")).toBeNull());
    const settled = screen.getByTestId("corporate-history-3");
    expect(within(settled).getByText("Tratado")).toBeDefined();
    expect(mock.resolutions).toEqual([{ id: 3, resolution: {} }]);
  });

  it("keeps the decision disabled until the missing data is supplied", async () => {
    renderPage();

    const parked = await screen.findByTestId("corporate-review-2");
    expect(
      (within(parked).getByRole("button", { name: "Aplicar" }) as HTMLButtonElement).disabled,
    ).toBe(true);
  });
});
