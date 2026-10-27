// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import type {
  Allocation as AllocationModel,
  LegacyAttributionPreview,
  LegacyAttributionResult,
} from "../api/types.ts";
import { Allocation } from "./Allocation.tsx";

const mock = vi.hoisted(() => ({
  allocation: null as AllocationModel | null,
  settled: null as AllocationModel | null,
  preview: null as LegacyAttributionPreview | null,
  result: null as LegacyAttributionResult | null,
  applied: 0,
  classified: [] as Array<[string, string]>,
  refreshed: 0,
}));

vi.mock("../api/client.ts", () => ({
  api: {
    getCurrentAllocation: () => Promise.resolve(mock.allocation),
    getLegacyAttributionPreview: () => Promise.resolve(mock.preview),
    applyLegacyAttribution: () => {
      mock.applied += 1;
      if (mock.settled) mock.allocation = mock.settled;
      return Promise.resolve(mock.result);
    },
    classifyTicker: (ticker: string, assetClass: string) => {
      mock.classified.push([ticker, assetClass]);
      return Promise.resolve({ ticker });
    },
    refreshCatalog: () => {
      mock.refreshed += 1;
      return Promise.resolve({ catalog: 1, quotes: 1 });
    },
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
  mock.settled = null;
  mock.preview = null;
  mock.result = null;
  mock.applied = 0;
  mock.classified = [];
  mock.refreshed = 0;
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

  it("drops the stale strategy-distribution copy from the reconciliation bar", async () => {
    mock.allocation = allocation;
    renderAllocation();

    expect(await screen.findByText(/fora de qualquer estratégia/)).toBeDefined();
    expect(screen.queryByText(/distribuição por estratégia/)).toBeNull();
  });

  it("shows the bulk action above the tree and previews the adds and skips before anything changes", async () => {
    mock.allocation = allocation;
    mock.preview = preview;
    renderAllocation();
    const user = userEvent.setup();

    const open = await screen.findByRole("button", {
      name: /Atribuir posições fora da estratégia ao legado/,
    });
    await user.click(open);

    const dialog = await screen.findByRole("dialog", { name: /Atribuir ao legado/ });
    expect(within(dialog).getByText("ITUB4")).toBeDefined();
    expect(within(dialog).getByText("Já tem atribuição")).toBeDefined();
    expect(within(dialog).getByRole("link", { name: /Classificar/ })).toBeDefined();
    expect(mock.applied).toBe(0);
  });

  it("applies the bulk attribution and reports the per-ticker result", async () => {
    mock.allocation = allocation;
    mock.preview = preview;
    mock.result = result;
    renderAllocation();
    const user = userEvent.setup();

    await user.click(
      await screen.findByRole("button", {
        name: /Atribuir posições fora da estratégia ao legado/,
      }),
    );
    const dialog = await screen.findByRole("dialog", { name: /Atribuir ao legado/ });
    await user.click(within(dialog).getByRole("button", { name: /Confirmar/ }));

    expect(mock.applied).toBe(1);
    expect(await screen.findByText(/1 posição atribuída ao legado/)).toBeDefined();
  });

  it("hides the bulk action when no outside custody remains", async () => {
    mock.allocation = {
      ...allocation,
      classes: allocation.classes.map((cls) => ({
        ...cls,
        outside: { actual: 0, tickers: [] },
      })),
    };
    renderAllocation();

    await screen.findByText("Ações");
    expect(
      screen.queryByRole("button", { name: /Atribuir posições fora da estratégia ao legado/ }),
    ).toBeNull();
  });

  it("keeps the apply result visible when the refetch clears the banner", async () => {
    mock.allocation = allocation;
    mock.settled = settledAllocation;
    mock.preview = preview;
    mock.result = result;
    renderAllocation();
    const user = userEvent.setup();

    await user.click(
      await screen.findByRole("button", {
        name: /Atribuir posições fora da estratégia ao legado/,
      }),
    );
    const dialog = await screen.findByRole("dialog", { name: /Atribuir ao legado/ });
    await user.click(within(dialog).getByRole("button", { name: /Confirmar/ }));

    await waitFor(() =>
      expect(
        screen.queryByRole("button", { name: /Atribuir posições fora da estratégia ao legado/ }),
      ).toBeNull(),
    );
    expect(screen.getByRole("dialog", { name: /Atribuir ao legado/ })).toBeDefined();
    expect(screen.getByText(/1 posição atribuída ao legado/)).toBeDefined();
  });

  it("classifies an unclassified ticker from its leaf", async () => {
    mock.allocation = allocation;
    renderAllocation();
    const user = userEvent.setup();

    await screen.findByText("Sem classe");
    await user.click(within(rowFor("Sem classe")).getByRole("button"));
    await user.click(screen.getByRole("button", { name: "Classificar" }));
    await user.click(screen.getByRole("button", { name: "FIIs" }));

    expect(mock.classified).toEqual([["ZZZZ9", "FIIS"]]);
  });

  it("offers the catalog refresh as the bulk alternative in the classify dialog", async () => {
    mock.allocation = allocation;
    renderAllocation();
    const user = userEvent.setup();

    await screen.findByText("Sem classe");
    await user.click(within(rowFor("Sem classe")).getByRole("button"));
    await user.click(screen.getByRole("button", { name: "Classificar" }));
    await user.click(screen.getByRole("button", { name: "Atualizar catálogo" }));

    expect(mock.refreshed).toBe(1);
    expect(mock.classified).toEqual([]);
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

const settledAllocation: AllocationModel = {
  ...allocation,
  classes: allocation.classes.map((cls) => ({
    ...cls,
    outside: { actual: 0, tickers: [] },
  })),
};

const preview: LegacyAttributionPreview = {
  groups: [
    {
      asset_class: "ACOES",
      strategy_id: 9,
      strategy_name: "Legado Ações",
      adds: [{ ticker: "ITUB4", quantity: 50, value: 500 }],
    },
  ],
  skips: [
    { ticker: "VALE3", reason: "HAS_ATTRIBUTION", asset_class: "ACOES" },
    { ticker: "ZZZZ9", reason: "UNCLASSIFIED", asset_class: null },
  ],
};

const result: LegacyAttributionResult = {
  attributed: [
    {
      ticker: "ITUB4",
      quantity: 50,
      value: 500,
      strategy_id: 9,
      strategy_name: "Legado Ações",
    },
  ],
  skipped: [{ ticker: "VALE3", reason: "HAS_ATTRIBUTION", asset_class: "ACOES" }],
};
