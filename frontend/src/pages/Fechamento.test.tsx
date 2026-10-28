// @vitest-environment jsdom
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { Fechamento } from "./Fechamento.tsx";

const mutate = vi.fn();
const mock = vi.hoisted(() => ({ hasPendingTransfer: true }));

vi.mock("../api/queries.ts", () => {
  const pending = {
    id: 7,
    ticker: "ITUB4",
    from_strategy_id: 1,
    from_strategy: "Dividendos",
    to_strategy_id: 2,
    to_strategy: "Small Caps",
    quantity: 20,
    amount: 600,
    auto_approved: false,
    status: "PENDING",
  };

  return {
    useCurrentMonth: () => ({
      isLoading: false,
      isError: false,
      data: {
        month: {
          id: 10,
          period: "2026-10",
          capital: 250000,
          floor: 20000,
          status: "OPEN",
          aportes: 0,
        },
        targets: { classes: [], strategies: [], sub_classes: [], tickers: [] },
      },
    }),
    useOrders: () => ({
      isLoading: false,
      isError: false,
      data: {
        period: "2026-10",
        orders: [],
        transfers: mock.hasPendingTransfer ? [pending] : [],
        unplanned_trades: [],
      },
    }),
    useClosedMonths: () => ({
      isLoading: false,
      isError: false,
      data: [
        {
          id: 9,
          period: "2026-09",
          capital: 240000,
          floor: 20000,
          status: "CLOSED",
          aportes: 2000,
        },
      ],
    }),
    useMonthSnapshot: () => ({
      isLoading: false,
      isError: false,
      data: {
        id: 3,
        month_id: 9,
        created_at: "2026-09-30T18:05:00",
        state: {
          period: "2026-09",
          capital: 240000,
          floor: 20000,
          aportes: 2000,
          caixa: 45000,
          holdings: [],
          attributions: [],
          classes: [],
          income: { gross: 100, retained: 15, net: 85 },
          orders: {
            orders: [
              {
                id: 1,
                ticker: "ITUB4",
                side: "BUY",
                quantity: 10,
                executed_quantity: 10,
                status: "IMPORTED",
              },
              {
                id: 2,
                ticker: "B3SA3",
                side: "SELL",
                quantity: 10,
                executed_quantity: 5,
                status: "EXECUTED",
              },
            ],
            transfers: [],
            unplanned_trades: [],
          },
          tax: {
            carry: [],
            meter: {
              gross_sales: 0,
              exemption_limit: 20000,
              exceeded: false,
              realized_gain: 0,
              taxable_gain: 0,
              rate: 15,
              tax: 0,
            },
          },
        },
      },
    }),
    useCloseMonth: () => ({
      mutate,
      isPending: false,
      error: null,
    }),
  };
});

beforeEach(() => {
  mock.hasPendingTransfer = true;
});

afterEach(() => {
  cleanup();
  mutate.mockReset();
});

function renderPage() {
  return render(
    <MemoryRouter>
      <Fechamento />
    </MemoryRouter>,
  );
}

describe("Fechamento", () => {
  it("blocks the close while a transfer is pending and links to Ordens", () => {
    renderPage();

    const close = screen.getByTestId("close-month") as HTMLButtonElement;
    expect(close.disabled).toBe(true);
    expect(close.textContent).toContain("Bloqueado — 1 transferência pendente");
    expect(screen.getByRole("link", { name: "resolver" }).getAttribute("href")).toBe("/orders");
  });

  it("shows the selected closed month's frozen summary", () => {
    renderPage();

    expect(screen.getByText("R$ 45.000,00")).toBeTruthy();
    expect(screen.getByText("R$ 85,00")).toBeTruthy();
    expect(screen.getByText("2")).toBeTruthy();
    expect(screen.getByText("30/09/2026 18:05")).toBeTruthy();
  });

  it("closes the month from the enabled button", async () => {
    mock.hasPendingTransfer = false;
    const user = userEvent.setup();
    renderPage();

    const close = screen.getByTestId("close-month") as HTMLButtonElement;
    expect(close.disabled).toBe(false);

    await user.click(close);

    expect(mutate).toHaveBeenCalledTimes(1);
  });
});
