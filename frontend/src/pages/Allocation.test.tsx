// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { Allocation as AllocationModel } from "../api/types.ts";
import { Allocation } from "./Allocation.tsx";

const mock = vi.hoisted(() => ({
  allocation: null as AllocationModel | null,
}));

vi.mock("../api/client.ts", () => ({
  api: {
    getCurrentAllocation: () => Promise.resolve(mock.allocation),
  },
}));

function renderAllocation() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <Allocation />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function rowFor(label: string): HTMLElement {
  const cell = screen.getByText(label);
  const row = cell.closest("tr");
  if (!row) throw new Error(`no row for ${label}`);
  return row;
}

afterEach(() => {
  cleanup();
  mock.allocation = null;
});

describe("the Alocação tree", () => {
  it("renders the bucket and the outside row under their class, and the unclassified line at the root", async () => {
    mock.allocation = allocation;
    renderAllocation();
    const user = userEvent.setup();

    await screen.findByText("Ações");
    expect(screen.queryByText("Legado Ações")).toBeNull();
    expect(screen.getByText("Sem classe")).toBeDefined();

    await user.click(within(rowFor("Ações")).getByRole("button"));

    expect(screen.getByText("Top Dividendos")).toBeDefined();
    expect(screen.getByText("Legado Ações")).toBeDefined();
    expect(screen.getByText("Fora da estratégia")).toBeDefined();
  });

  it("renders an em dash, not a zero, for the bucket's target, ideal and deltas", async () => {
    mock.allocation = allocation;
    renderAllocation();
    const user = userEvent.setup();

    await screen.findByText("Ações");
    await user.click(within(rowFor("Ações")).getByRole("button"));
    const manualRow = rowFor("Legado Ações");

    expect(within(manualRow).getAllByText("—")).toHaveLength(4);
    expect(within(manualRow).queryByText("R$ 0,00")).toBeNull();
  });

  it("expands the bucket and the unclassified line to their tickers", async () => {
    mock.allocation = allocation;
    renderAllocation();
    const user = userEvent.setup();

    await screen.findByText("Ações");
    await user.click(within(rowFor("Ações")).getByRole("button"));
    await user.click(within(rowFor("Legado Ações")).getByRole("button"));
    expect(within(rowFor("Legado Ações")).getByText("▾")).toBeDefined();
    expect(screen.getByText("ITUB4")).toBeDefined();

    await user.click(within(rowFor("Sem classe")).getByRole("button"));
    expect(screen.getByText("ZZZZ9")).toBeDefined();
  });

  it("renders an Actual-only tree and an empty Capital panel without an open month", async () => {
    mock.allocation = monthFreeAllocation;
    renderAllocation();

    await screen.findByText("Ações");
    expect(screen.queryByText(/Nenhum mês aberto/)).toBeNull();

    const capitalRow = rowFor("Capital");
    expect(within(capitalRow).getAllByText("—")).toHaveLength(4);
    expect(within(capitalRow).getByText("R$ 3.600,00")).toBeDefined();

    expect(screen.getByText("Capital do mês")).toBeDefined();
    expect(screen.queryByText("Reserva (piso)")).toBeNull();
  });

  it("drops the stale strategy-distribution copy from the reconciliation bar", async () => {
    mock.allocation = allocation;
    renderAllocation();

    expect(await screen.findByText(/fora de qualquer estratégia/)).toBeDefined();
    expect(screen.queryByText(/distribuição por estratégia/)).toBeNull();
  });
});

const allocation: AllocationModel = {
  period: "2026-09",
  capital: 100000,
  floor: 20000,
  ideal: 100000,
  actual: 3600,
  deficit: 96400,
  drift: -96.4,
  unattributed: 500,
  unclassified: {
    actual: 100,
    tickers: [{ ticker: "ZZZZ9", actual: 100 }],
  },
  classes: [
    {
      asset_class: "ACOES",
      target: 50,
      ideal: 50000,
      actual: 1500,
      deficit: 48500,
      drift: -48.5,
      unattributed: 400,
      strategies: [
        {
          strategy: "Top Dividendos",
          target: 100,
          ideal: 50000,
          actual: 600,
          deficit: 49400,
          drift: -49.4,
          tickers: [
            { ticker: "VALE3", target: 12, ideal: 6000, actual: 600, deficit: 5400, drift: -5.4 },
          ],
        },
      ],
      manual: {
        strategy: "Legado Ações",
        actual: 500,
        tickers: [
          { ticker: "ITUB4", actual: 300 },
          { ticker: "VALE3", actual: 200 },
        ],
      },
      outside: {
        actual: 400,
        tickers: [
          { ticker: "ITUB4", actual: 200 },
          { ticker: "VALE3", actual: 200 },
        ],
      },
      sub_classes: [],
    },
    {
      asset_class: "FIIS",
      ideal: 0,
      actual: 0,
      deficit: 0,
      drift: 0,
      unattributed: 0,
      strategies: [],
      outside: { actual: 0, tickers: [] },
      sub_classes: [],
    },
    {
      asset_class: "RENDA_FIXA",
      target: 50,
      ideal: 50000,
      actual: 2000,
      deficit: 48000,
      drift: -48,
      unattributed: 0,
      strategies: [],
      outside: { actual: 0, tickers: [] },
      sub_classes: [
        {
          sub_class: "FLOATING_RATE",
          target: 55,
          ideal: 27500,
          actual: 2000,
          deficit: 25500,
          drift: -25.5,
        },
      ],
    },
  ],
};

// The month-free read: no period and no chain, so the Capital root and every class blank their
// planning columns while the custody-driven Actuals and the bucket/outside rows stay live.
const monthFreeAllocation: AllocationModel = {
  ...allocation,
  period: undefined,
  capital: 0,
  floor: 0,
  ideal: 0,
  deficit: -3600,
  drift: 0,
  classes: allocation.classes.map((cls) => ({ ...cls, target: undefined })),
};
