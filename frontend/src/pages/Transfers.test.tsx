// @vitest-environment jsdom
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { OrderList, Strategy, TransferProposal } from "../api/types.ts";
import { Transfers } from "./Transfers.tsx";

const mock = vi.hoisted(() => ({
  orders: null as OrderList | null,
  strategies: [] as Strategy[],
  decide: vi.fn(),
  decideError: null as Error | null,
}));

vi.mock("../api/queries.ts", () => ({
  useOrders: () => ({ data: mock.orders, isLoading: false, isError: false, error: null }),
  useStrategies: () => ({ data: mock.strategies, isLoading: false, isError: false }),
  useDecideTransfer: () => ({
    mutate: mock.decide,
    isPending: false,
    error: mock.decideError,
  }),
}));

function transfer(overrides: Partial<TransferProposal> = {}): TransferProposal {
  return {
    id: 1,
    ticker: "ITUB4",
    from_strategy_id: 1,
    from_strategy: "Small Caps",
    to_strategy_id: 2,
    to_strategy: "Top Dividendos",
    quantity: 60,
    amount: 2100,
    auto_approved: false,
    status: "PENDING",
    ...overrides,
  };
}

function month(transfers: TransferProposal[]): OrderList {
  return { period: "2026-10", orders: [], transfers, unplanned_trades: [] };
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={["/plan/transfers"]}>
      <Transfers />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  mock.orders = month([]);
  mock.strategies = [
    { id: 1, name: "Small Caps", asset_class: "ACOES", kind: "REPORT_DRIVEN" },
    { id: 2, name: "Top Dividendos", asset_class: "ACOES", kind: "REPORT_DRIVEN" },
  ] satisfies Strategy[];
  mock.decideError = null;
});

afterEach(() => {
  cleanup();
  mock.decide.mockReset();
});

describe("Transferências inbox", () => {
  it("lists each proposal's quantity and the atomic effect of its decision", () => {
    mock.orders = month([
      transfer({ id: 1, ticker: "ITUB4", quantity: 60 }),
      transfer({ id: 2, ticker: "VALE3", quantity: 1, amount: 90 }),
    ]);

    renderPage();

    const row = screen.getByTestId("transfer-pending-1");
    expect(within(row).getByText("ITUB4")).toBeDefined();
    expect(within(row).getByText("60 cotas")).toBeDefined();
    expect(
      within(row).getByText("aprovar move 60 cotas · rejeitar não move nenhuma"),
    ).toBeDefined();
    expect(within(row).getByText("evita vender R$ 2.100,00")).toBeDefined();
    expect(within(row).getByRole("button", { name: "Aprovar" })).toBeDefined();
    expect(within(row).getByRole("button", { name: "Rejeitar" })).toBeDefined();

    const single = screen.getByTestId("transfer-pending-2");
    expect(within(single).getByText("1 cota")).toBeDefined();
  });

  it("offers no partial path — the decision carries no quantity control", () => {
    mock.orders = month([transfer({ id: 1 })]);

    renderPage();

    expect(screen.queryByRole("spinbutton")).toBeNull();
    expect(screen.queryByRole("textbox")).toBeNull();
  });

  it("approves the whole proposal by id", async () => {
    const user = userEvent.setup();
    mock.orders = month([transfer({ id: 7, quantity: 60 })]);

    renderPage();
    await user.click(screen.getByRole("button", { name: "Aprovar" }));

    expect(mock.decide).toHaveBeenCalledWith({ id: 7, decision: "approve" });
  });

  it("rejects the proposal by id", async () => {
    const user = userEvent.setup();
    mock.orders = month([transfer({ id: 9 })]);

    renderPage();
    await user.click(screen.getByRole("button", { name: "Rejeitar" }));

    expect(mock.decide).toHaveBeenCalledWith({ id: 9, decision: "reject" });
  });

  it("releases the close with no pending proposal and links to Fechamentos", () => {
    mock.orders = month([transfer({ id: 3, status: "REJECTED" })]);

    renderPage();

    expect(screen.getByText(/O fechamento está liberado/)).toBeDefined();
    expect(screen.getByRole("link", { name: /Ver fechamento/ }).getAttribute("href")).toBe(
      "/plan/close",
    );
  });

  it("shows decided transfers and their outcome in the history", () => {
    mock.orders = month([
      transfer({ id: 1, ticker: "ITUB4", quantity: 60, status: "APPROVED" }),
      transfer({
        id: 2,
        ticker: "VALE3",
        quantity: 40,
        status: "APPROVED",
        auto_approved: true,
      }),
      transfer({ id: 3, ticker: "CPLE6", quantity: 30, status: "REJECTED" }),
    ]);

    renderPage();

    const approved = screen.getByTestId("transfer-decided-1");
    expect(within(approved).getByText("Aprovada")).toBeDefined();
    expect(within(approved).getByText("moveu 60 cotas")).toBeDefined();

    const automatic = screen.getByTestId("transfer-decided-2");
    expect(within(automatic).getByText("aprovada automaticamente")).toBeDefined();

    const rejected = screen.getByTestId("transfer-decided-3");
    expect(within(rejected).getByText("Rejeitada")).toBeDefined();
    expect(within(rejected).getByText("não moveu nenhuma cota")).toBeDefined();
  });

  it("points at the month opener when no month is open", () => {
    mock.orders = null;

    renderPage();

    expect(screen.getByText(/Abra um mês/)).toBeDefined();
    expect(screen.getByRole("link", { name: /Mês atual/ }).getAttribute("href")).toBe("/plan");
  });
});
