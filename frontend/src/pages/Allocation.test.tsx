// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "../api/client.ts";
import type {
  Allocation as AllocationModel,
  Attribution,
  Edition,
  OrderPlan,
  Strategy,
} from "../api/types.ts";
import { Allocation } from "./Allocation.tsx";

const mock = vi.hoisted(() => ({
  allocation: null as AllocationModel | null,
  strategies: [] as Strategy[],
  editions: {} as Record<number, Edition[]>,
  attribution: null as Attribution | null,
  plan: null as OrderPlan | null,
}));

vi.mock("../api/client.ts", () => ({
  ApiError: class ApiError extends Error {},
  api: {
    getCurrentAllocation: () => Promise.resolve(mock.allocation),
    getStrategies: () => Promise.resolve(mock.strategies),
    getEditions: (strategyId: number) => Promise.resolve(mock.editions[strategyId] ?? []),
    getAttribution: () => Promise.resolve(mock.attribution),
    getOrderPlan: () => Promise.resolve(mock.plan),
    transferAttribution: vi.fn(() => Promise.resolve(mock.attribution)),
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
  vi.clearAllMocks();
  mock.allocation = null;
  mock.strategies = [];
  mock.editions = {};
  mock.attribution = null;
  mock.plan = null;
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
});

describe("the manual transfer from the bucket", () => {
  it("offers only report-driven Strategies whose active edition weighs the ticker, with the shortfall as default", async () => {
    mock.allocation = allocation;
    mock.strategies = transferStrategies;
    mock.editions = transferEditions;
    mock.attribution = transferAttribution;
    mock.plan = orderPlan({ targetQuantity: 25, attributedQuantity: 10 });
    renderAllocation();
    const user = userEvent.setup();

    await openTransferModal(user);

    const dialog = screen.getByRole("dialog", { name: "Transferir do legado" });
    const destination = within(dialog).getByLabelText("Estratégia de destino");
    const options = within(destination).getAllByRole("option");
    expect(options.map((option) => option.textContent)).toEqual([
      "Top Dividendos · falta 15 cotas",
    ]);
    expect((within(dialog).getByLabelText("Quantidade") as HTMLInputElement).value).toBe("15");
    expect(within(dialog).getByText(/40 cotas no legado/)).toBeDefined();
  });

  it("applies the transfer from the manual bucket to the chosen report Strategy", async () => {
    mock.allocation = allocation;
    mock.strategies = transferStrategies;
    mock.editions = transferEditions;
    mock.attribution = transferAttribution;
    mock.plan = orderPlan({ targetQuantity: 25, attributedQuantity: 10 });
    renderAllocation();
    const user = userEvent.setup();

    await openTransferModal(user);
    await user.click(screen.getByRole("button", { name: "Transferir 15 cotas" }));

    expect(vi.mocked(api.transferAttribution)).toHaveBeenCalledWith("ITUB4", {
      from_strategy_id: 9002,
      to_strategy_id: 9001,
      quantity: 15,
    });
  });

  it("tells the user to import an edition when no report Strategy weighs the ticker", async () => {
    mock.allocation = allocation;
    mock.strategies = transferStrategies;
    mock.editions = {};
    mock.attribution = transferAttribution;
    mock.plan = null;
    renderAllocation();
    const user = userEvent.setup();

    await openTransferModal(user);

    expect(screen.getByText(/Importe uma edição/)).toBeDefined();
  });
});

async function openTransferModal(user: ReturnType<typeof userEvent.setup>) {
  await screen.findByText("Ações");
  await user.click(within(rowFor("Ações")).getByRole("button"));
  await user.click(within(rowFor("Legado Ações")).getByRole("button"));
  await user.click(screen.getByRole("button", { name: "Transferir ITUB4" }));
  await screen.findByRole("dialog", { name: "Transferir do legado" });
}

function strategy(
  id: number,
  name: string,
  assetClass: Strategy["asset_class"],
  kind: Strategy["kind"],
): Strategy {
  return { id, name, asset_class: assetClass, kind };
}

function edition(
  strategyId: number,
  status: Edition["status"],
  entries: Edition["entries"],
): Edition {
  return {
    id: strategyId,
    strategy_id: strategyId,
    reference_month: "2026-09",
    status,
    entries,
  };
}

function orderPlan({
  targetQuantity,
  attributedQuantity,
}: {
  targetQuantity: number;
  attributedQuantity: number;
}): OrderPlan {
  return {
    period: "2026-09",
    phase: "FUNDING",
    capital: 100000,
    floor: 20000,
    investable_capital: 80000,
    classes: [
      {
        asset_class: "ACOES",
        target: 50,
        ideal: 50000,
        strategies: [
          {
            strategy: "Top Dividendos",
            target: 100,
            ideal: 50000,
            tickers: [
              {
                ticker: "ITUB4",
                strategy: "Top Dividendos",
                target: 100,
                ideal: 50000,
                target_quantity: targetQuantity,
                neutro: false,
                attributed_quantity: attributedQuantity,
              },
            ],
          },
        ],
      },
    ],
    candidates: [],
    outside_strategy: [],
    unclassified: [],
    attribution: [],
    transfers: [],
  };
}

const transferStrategies: Strategy[] = [
  strategy(9001, "Top Dividendos", "ACOES", "REPORT_DRIVEN"),
  strategy(9002, "Legado Ações", "ACOES", "MANUAL"),
  strategy(9003, "Sem edição", "ACOES", "REPORT_DRIVEN"),
  strategy(9004, "Top FIIs", "FIIS", "REPORT_DRIVEN"),
];

const transferEditions: Record<number, Edition[]> = {
  9001: [edition(9001, "ACTIVE", [{ ticker: "ITUB4", weight: 20, do_not_trade: false }])],
  9004: [edition(9004, "ACTIVE", [{ ticker: "ITUB4", weight: 20, do_not_trade: false }])],
};

const transferAttribution: Attribution = {
  ticker: "ITUB4",
  custody_quantity: 100,
  attributed_quantity: 100,
  unattributed_quantity: 0,
  slices: [
    { strategy_id: 9001, strategy_name: "Top Dividendos", quantity: 60 },
    { strategy_id: 9002, strategy_name: "Legado Ações", quantity: 40 },
  ],
};

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
