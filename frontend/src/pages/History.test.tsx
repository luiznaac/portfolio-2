// @vitest-environment jsdom
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { Gap } from "../api/types.ts";
import { History } from "./History.tsx";

const mock = vi.hoisted(() => ({
  gaps: [] as Gap[],
  prices: [] as Array<{ id: number; unitPrice: number }>,
}));

vi.mock("../api/queries.ts", () => ({
  useGaps: () => ({ data: mock.gaps, isLoading: false }),
  usePriceGap: () => ({
    mutate: (
      input: { id: number; unitPrice: number },
      options?: { onSuccess?: (gap: Gap) => void },
    ) => {
      mock.prices.push(input);
      options?.onSuccess?.(mock.gaps[0]);
    },
    isPending: false,
    error: null,
  }),
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

function renderHistory() {
  return render(
    <MemoryRouter>
      <History />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  mock.gaps = [];
  mock.prices = [];
});

afterEach(cleanup);

describe("Ativos · Lacunas", () => {
  it("lists no gap as an open one", () => {
    renderHistory();

    expect(screen.getByText("Nenhuma lacuna aberta.")).toBeDefined();
  });

  it("prices a missing sale's proceeds inline and marks a missing buy as approximate", async () => {
    mock.gaps = [pendingSale(), missingBuy()];
    const user = userEvent.setup();
    renderHistory();

    const lacunas = screen.getByTestId("lacunas");
    expect(within(lacunas).getByTestId("gap-approximate-ITUB4").textContent).toContain(
      "aproximado",
    );

    await user.type(within(lacunas).getByTestId("gap-price-11"), "18");
    await user.click(within(lacunas).getByTestId("gap-price-save-11"));

    expect(mock.prices).toEqual([{ id: 11, unitPrice: 18 }]);
  });

  it("corrects an already priced missing sale's proceeds inline", async () => {
    mock.gaps = [pricedSale()];
    const user = userEvent.setup();
    renderHistory();

    const lacunas = screen.getByTestId("lacunas");
    const input = within(lacunas).getByTestId("gap-price-13");
    expect(input).toHaveProperty("value", "18");

    await user.clear(input);
    await user.type(input, "20");
    await user.click(within(lacunas).getByTestId("gap-price-save-13"));

    expect(mock.prices).toEqual([{ id: 13, unitPrice: 20 }]);
  });
});
