// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { Strategy, TransferProposal } from "../api/types.ts";
import { Orders } from "./Orders.tsx";

const mock = vi.hoisted(() => ({
  transfers: [] as TransferProposal[],
  strategies: [] as Strategy[],
}));

vi.mock("../api/client.ts", () => ({
  api: {
    getOrders: () =>
      Promise.resolve({
        period: "2026-10",
        orders: [],
        transfers: mock.transfers,
        unplanned_trades: [],
      }),
    getStrategies: () => Promise.resolve(mock.strategies),
  },
}));

function renderOrders() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={["/orders"]}>
        <Orders />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function transfer(overrides: Partial<TransferProposal> = {}): TransferProposal {
  return {
    id: 1,
    ticker: "ITUB4",
    from_strategy_id: 1,
    from_strategy: "Top",
    to_strategy_id: 2,
    to_strategy: "Small",
    quantity: 10,
    amount: 600,
    auto_approved: false,
    status: "PENDING",
    ...overrides,
  };
}

afterEach(() => {
  cleanup();
  mock.transfers = [];
  mock.strategies = [];
});

describe("Orders inbox", () => {
  it("reads a bucket-sourced row as a move from the legacy book and a report row as avoided selling", async () => {
    mock.strategies = [
      { id: 1, name: "Top", asset_class: "ACOES", kind: "REPORT_DRIVEN" },
      { id: 3, name: "Legado", asset_class: "ACOES", kind: "MANUAL" },
    ];
    mock.transfers = [
      transfer({
        id: 1,
        from_strategy_id: 3,
        from_strategy: "Legado",
        to_strategy_id: 1,
        to_strategy: "Top",
        amount: 100,
      }),
      transfer({ id: 2, from_strategy_id: 1, from_strategy: "Top", amount: 600 }),
    ];

    renderOrders();

    expect(await screen.findByText("move R$ 100,00 do legado")).toBeTruthy();
    expect(screen.getByText("evita vender R$ 600,00")).toBeTruthy();
  });
});
