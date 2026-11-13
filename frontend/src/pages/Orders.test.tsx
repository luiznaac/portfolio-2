// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { OrderPlan, SuggestedOrder } from "../api/types.ts";
import { Orders } from "./Orders.tsx";

const mock = vi.hoisted(() => ({
  orders: [] as SuggestedOrder[],
  plan: undefined as unknown as OrderPlan,
  override: vi.fn(),
}));

vi.mock("../api/client.ts", () => ({
  api: {
    getOrders: () =>
      Promise.resolve({
        period: "2026-10",
        orders: mock.orders,
        transfers: [],
        unplanned_trades: [],
      }),
    getOrderPlan: () => Promise.resolve(mock.plan),
    overrideOrderMatch: (orderId: number, payload: { trade_id?: number | null }) => {
      mock.override(orderId, payload);
      return Promise.resolve(mock.orders[0]);
    },
  },
}));

function renderOrders() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={["/plan/orders"]}>
        <Orders />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

afterEach(() => {
  cleanup();
  mock.orders = [];
  mock.override.mockReset();
});

beforeEach(() => {
  mock.plan = plan();
});

describe("Orders list", () => {
  it("renders a partial execution as PARCIAL from executed_quantity", async () => {
    mock.orders = [
      order({ id: 1, ticker: "VALE3", side: "BUY", quantity: 24, executed_quantity: 6 }),
      order({
        id: 2,
        ticker: "ITUB4",
        side: "BUY",
        quantity: 70,
        executed_quantity: 70,
        status: "EXECUTED",
      }),
    ];

    renderOrders();

    const partial = (await screen.findByText("VALE3")).closest("tr") as HTMLElement;
    expect(within(partial).getByText("PARCIAL")).toBeTruthy();
    expect(within(partial).getByText("6/24")).toBeTruthy();

    const executed = screen.getByText("ITUB4").closest("tr") as HTMLElement;
    expect(within(executed).getByText("Executado")).toBeTruthy();
  });

  it("shows the plan's funding and attribution context beside each order", async () => {
    mock.orders = [
      order({ id: 1, ticker: "VALE3", side: "BUY", quantity: 24, executed_quantity: 0 }),
      order({ id: 2, ticker: "ITSA4", side: "SELL", quantity: 90, executed_quantity: 0 }),
    ];
    mock.plan = plan({
      attribution: [
        { ticker: "VALE3", strategy: "Top Dividendos", side: "ALLOCATE", quantity: 24 },
      ],
      candidates: [
        {
          ticker: "VALE3",
          side: "BUY",
          quantity: 24,
          price: 60,
          amount: 1440,
          deficit: 1440,
          ideal: 10_000,
          target_quantity: 40,
          position_quantity: 16,
        },
        {
          ticker: "ITSA4",
          side: "SELL",
          quantity: 90,
          price: 10,
          amount: 900,
          deficit: -100,
          ideal: 0,
          target_quantity: 0,
          position_quantity: 90,
          funding_source: "NEUTRO",
        },
      ],
    });

    renderOrders();

    const vale = (await screen.findByText("VALE3")).closest("tr") as HTMLElement;
    expect(await within(vale).findByText("Top Dividendos +24")).toBeTruthy();

    const itsa = screen.getByText("ITSA4").closest("tr") as HTMLElement;
    expect(await within(itsa).findByText("Neutro")).toBeTruthy();
  });

  it("withholds context the computed plan no longer matches", async () => {
    mock.orders = [order({ id: 1, ticker: "VALE3", side: "BUY", quantity: 24 })];
    mock.plan = plan({
      attribution: [
        { ticker: "VALE3", strategy: "Top Dividendos", side: "ALLOCATE", quantity: 30 },
      ],
      candidates: [
        {
          ticker: "VALE3",
          side: "BUY",
          quantity: 30,
          price: 60,
          amount: 1800,
          deficit: 1800,
          ideal: 10_000,
          target_quantity: 40,
          position_quantity: 10,
        },
      ],
    });

    renderOrders();

    const vale = (await screen.findByText("VALE3")).closest("tr") as HTMLElement;
    expect(await within(vale).findByText("—")).toBeTruthy();
    expect(within(vale).queryByText(/Top Dividendos/)).toBeNull();
  });

  it("retargets an order's match at a trade from the page", async () => {
    const user = userEvent.setup();
    mock.orders = [
      order({ id: 7, ticker: "VALE3", side: "BUY", quantity: 24, executed_quantity: 6 }),
    ];

    renderOrders();

    const row = (await screen.findByText("VALE3")).closest("tr") as HTMLElement;
    await user.click(within(row).getByRole("button", { name: "Ajustar" }));
    await user.type(within(row).getByLabelText("Negócio para VALE3"), "42");
    await user.click(within(row).getByRole("button", { name: "Aplicar" }));

    expect(mock.override).toHaveBeenCalledWith(7, { trade_id: 42 });
  });
});

function order(overrides: Partial<SuggestedOrder> = {}): SuggestedOrder {
  return {
    id: 1,
    ticker: "VALE3",
    side: "BUY",
    quantity: 10,
    suggested_price: 10,
    executed_quantity: 0,
    status: "SUGGESTED",
    ...overrides,
  };
}

function plan(overrides: Partial<OrderPlan> = {}): OrderPlan {
  return {
    period: "2026-10",
    phase: "FUNDING",
    capital: 100_000,
    floor: 0,
    investable_capital: 100_000,
    classes: [],
    candidates: [],
    outside_strategy: [],
    unclassified: [],
    attribution: [],
    transfers: [],
    ...overrides,
  };
}
