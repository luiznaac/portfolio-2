// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { Gap } from "../api/types.ts";
import { Gaps } from "./Gaps.tsx";

const mock = vi.hoisted(() => ({
  gaps: [] as Gap[],
  prices: [] as Array<{ id: number; unitPrice: number }>,
}));

vi.mock("../api/client.ts", () => ({
  ApiError: class ApiError extends Error {
    constructor(readonly status: number) {
      super(`HTTP ${status}`);
    }
  },
  api: {
    getGaps: () => Promise.resolve(mock.gaps),
    priceGap: (id: number, unitPrice: number) => {
      mock.prices.push({ id, unitPrice });
      const current = mock.gaps.find((gap) => gap.id === id);
      const priced: Gap = { ...current, unit_price: unitPrice, realized_pending: false } as Gap;
      mock.gaps = mock.gaps.map((gap) => (gap.id === id ? priced : gap));
      return Promise.resolve(priced);
    },
  },
}));

function pendingSale(): Gap {
  return {
    id: 11,
    ticker: "VALE3",
    date: "2026-01-10",
    quantity: 5,
    status: "OPEN",
    realized_pending: true,
    trade_watermark: 0,
    action_watermark: 0,
  };
}

function pricedSale(): Gap {
  return {
    id: 13,
    ticker: "PETR4",
    date: "2026-01-10",
    quantity: 3,
    unit_price: 18,
    status: "OPEN",
    realized_pending: false,
    trade_watermark: 0,
    action_watermark: 0,
  };
}

function missingBuy(): Gap {
  return {
    id: 12,
    ticker: "ITUB4",
    date: "2026-01-10",
    quantity: 8,
    unit_price: 9,
    status: "OPEN",
    trade_watermark: 0,
    action_watermark: 0,
  };
}

function replacedGap(): Gap {
  return {
    id: 14,
    ticker: "MSFT34",
    date: "2025-12-18",
    quantity: 2,
    unit_price: 84.2,
    status: "REPLACED",
    trade_watermark: 0,
    action_watermark: 0,
  };
}

function renderGaps() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <Gaps />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  mock.gaps = [];
  mock.prices = [];
});

afterEach(cleanup);

describe("Ativos · Lacunas", () => {
  it("says no gap is registered", async () => {
    renderGaps();

    expect(await screen.findByText("Nenhuma lacuna registrada.")).toBeDefined();
  });

  it("lists each gap with its realized-result state, status and price", async () => {
    mock.gaps = [pendingSale(), pricedSale(), missingBuy(), replacedGap()];
    renderGaps();

    const table = await screen.findByTestId("gaps-table");
    expect(within(table).getByTestId("gap-pending-11").textContent).toContain("pendente");
    expect(within(table).getByTestId("gap-approximate-ITUB4").textContent).toContain("aproximado");

    const vale = within(table).getByText("VALE3").closest("tr") as HTMLElement;
    expect(within(vale).getByText("aberta")).toBeDefined();
    expect(within(vale).getByTestId("gap-price-11")).toBeDefined();

    const msft = within(table).getByText("MSFT34").closest("tr") as HTMLElement;
    expect(within(msft).getByText("substituída")).toBeDefined();
    expect(within(msft).getByText("resolvido")).toBeDefined();
    expect(within(msft).queryByTestId("gap-approximate-MSFT34")).toBeNull();
    expect(within(msft).queryByTestId("gap-price-14")).toBeNull();

    expect(within(table).getAllByText("resolvido")).toHaveLength(2);
  });

  it("prices a pending sale and the row resolves without a reload", async () => {
    mock.gaps = [pendingSale()];
    const user = userEvent.setup();
    renderGaps();

    const table = await screen.findByTestId("gaps-table");
    await user.type(within(table).getByTestId("gap-price-11"), "18");
    await user.click(within(table).getByTestId("gap-price-save-11"));

    expect(mock.prices).toEqual([{ id: 11, unitPrice: 18 }]);
    await waitFor(() => expect(within(table).queryByTestId("gap-pending-11")).toBeNull());
    expect(within(table).getByText("resolvido")).toBeDefined();
  });

  it("keeps an already priced missing sale's proceeds in the field", async () => {
    mock.gaps = [pricedSale()];
    renderGaps();

    const table = await screen.findByTestId("gaps-table");
    expect(within(table).getByTestId("gap-price-13")).toHaveProperty("value", "18");
  });
});
