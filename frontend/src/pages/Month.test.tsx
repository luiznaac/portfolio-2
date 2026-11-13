// @vitest-environment jsdom
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type {
  OrderList,
  Strategy,
  SuggestedOrder,
  Targets,
  TransferProposal,
} from "../api/types.ts";
import { Month } from "./Month.tsx";

// Pin "today" so the month's backdated chip is deterministic: the page compares the open month's
// period with the wall clock's month, which would mark it historical on the first of a month.
vi.mock("../lib/dates.ts", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../lib/dates.ts")>();
  return { ...actual, todayIso: () => "2026-10-15" };
});

// The targets form renders variable rows (strategies, tickers) whose inputs are controlled by the
// form state. A row must keep its React identity while its fields are edited: a key derived from the
// edited value remounts the input on the first keystroke and the browser drops focus.
const mock = vi.hoisted(() => ({
  targets: undefined as unknown as Targets,
  strategies: [] as Strategy[],
  period: "2026-10",
  capital: 100_000,
  orders: null as OrderList | null,
  update: vi.fn(),
  replace: vi.fn(),
  refresh: vi.fn(),
  open: vi.fn(),
}));

vi.mock("../api/queries.ts", () => {
  const mutation = (fn: ReturnType<typeof vi.fn>) => ({
    mutate: fn,
    isPending: false,
    isSuccess: false,
    error: null,
  });

  return {
    useCurrentMonth: () => ({
      isLoading: false,
      isError: false,
      data: {
        month: {
          id: 1,
          period: mock.period,
          capital: mock.capital,
          floor: 0,
          status: "OPEN",
          aportes: 0,
        },
        targets: mock.targets,
      },
    }),
    useOrders: () => ({ data: mock.orders, isLoading: false, isError: false }),
    useMonthPeriodRange: () => ({
      isLoading: false,
      isError: false,
      data: { min: "2020-03", max: "2026-10", default: "2020-12" },
    }),
    useOpenMonth: () => mutation(mock.open),
    useUpdateCurrentMonth: () => mutation(mock.update),
    useReplaceTargets: () => mutation(mock.replace),
    useRefreshOrders: () => mutation(mock.refresh),
    useStrategies: () => ({ data: mock.strategies, isLoading: false, isError: false }),
  };
});

function defaultTargets(): Targets {
  return {
    classes: [{ asset_class: "ACOES", percentage: 40 }],
    strategies: [{ asset_class: "ACOES", strategy: "Top Dividendos", percentage: 34 }],
    sub_classes: [],
    tickers: [{ strategy: "Top Dividendos", ticker: "VALE3", percentage: 12 }],
  };
}

beforeEach(() => {
  mock.targets = defaultTargets();
  mock.strategies = [];
  mock.period = "2026-10";
  mock.capital = 100_000;
  mock.orders = null;
  mock.update.mockReset();
  mock.replace.mockReset();
  mock.refresh.mockReset();
  mock.open.mockReset();
});

afterEach(cleanup);

// The tool is a task list, not a dashboard: it opens on the first unfinished of the six steps, the
// rail is the only step list on the page, and the body renders the active step only.
describe("Plan / Mês shell", () => {
  it("opens on the first unfinished step and shows n de 6 on the rail", () => {
    render(<Month />);

    expect(screen.getByTestId("plan-step-plan")).toBeDefined();
    expect(screen.queryByTestId("plan-step-capital")).toBeNull();
    expect(screen.getByText("2 de 6")).toBeDefined();

    for (const key of ["capital", "targets", "plan", "execution", "transfer", "close"]) {
      expect(screen.getByTestId(`plan-step-rail-${key}`)).toBeDefined();
    }
  });

  it("collapses concluded steps to one-line summaries with their meta", () => {
    render(<Month />);

    const capital = screen.getByTestId("plan-step-rail-capital");
    expect(capital.textContent).toContain("Capital do mês");
    expect(capital.textContent).toContain("R$ 100.000,00");
    expect(capital.textContent).toContain("✓");

    const targets = screen.getByTestId("plan-step-rail-targets");
    expect(targets.textContent).toContain("Cadeia de alvos");
    expect(targets.textContent).toContain("1 classe");
  });

  it("returns to the first unfinished step with Retomar", async () => {
    const user = userEvent.setup();
    render(<Month />);

    await user.click(screen.getByTestId("plan-step-rail-capital"));
    expect(screen.getByTestId("plan-step-capital")).toBeDefined();

    await user.click(screen.getByRole("button", { name: /Retomar/ }));
    expect(screen.getByTestId("plan-step-plan")).toBeDefined();
  });
});

describe("Capital step", () => {
  it("previews the chain from the typed capital and flags the stored plan stale until a refresh", async () => {
    const user = userEvent.setup();
    mock.update.mockImplementationOnce((_payload, options) => options?.onSuccess?.());
    mock.refresh.mockImplementationOnce((_payload, options) => options?.onSuccess?.());
    render(<Month />);

    await user.click(screen.getByTestId("plan-step-rail-capital"));
    const capital = screen.getByLabelText("Capital (R$)");
    await user.clear(capital);
    await user.type(capital, "250000");

    const preview = screen.getByTestId("chain-preview");
    expect(within(preview).getByText("R$ 100.000,00")).toBeDefined();
    expect(within(preview).getByText("40,00%")).toBeDefined();

    await user.click(screen.getByRole("button", { name: "Salvar capital" }));
    expect(mock.update).toHaveBeenCalledWith({ capital: 250_000, floor: 0 }, expect.anything());
    expect(screen.getByText("plano desatualizado")).toBeDefined();

    await user.click(screen.getByTestId("plan-step-rail-plan"));
    await user.click(screen.getByRole("button", { name: "Recalcular plano" }));
    expect(mock.refresh).toHaveBeenCalled();
    expect(screen.queryByText("plano desatualizado")).toBeNull();
  });
});

describe("Month target rows keep focus while typing", () => {
  it("keeps focus on the strategy rung's Estratégia field", async () => {
    const user = userEvent.setup();
    render(<Month />);
    await user.click(screen.getByTestId("plan-step-rail-targets"));

    const [strategyInput] = screen.getAllByDisplayValue("Top Dividendos");
    await user.type(strategyInput, "X");

    expect(document.activeElement).toBe(strategyInput);
  });

  it("keeps focus on the ticker rung's Estratégia field", async () => {
    const user = userEvent.setup();
    render(<Month />);
    await user.click(screen.getByTestId("plan-step-rail-targets"));

    const [, tickerStrategyInput] = screen.getAllByDisplayValue("Top Dividendos");
    await user.type(tickerStrategyInput, "X");

    expect(document.activeElement).toBe(tickerStrategyInput);
  });

  it("keeps focus on the ticker rung's Ticker field", async () => {
    const user = userEvent.setup();
    render(<Month />);
    await user.click(screen.getByTestId("plan-step-rail-targets"));

    const tickerInput = screen.getByDisplayValue("VALE3");
    await user.type(tickerInput, "X");

    expect(document.activeElement).toBe(tickerInput);
  });
});

describe("Month window states", () => {
  it("chips a backdated month and reopens the opener at the default", async () => {
    const user = userEvent.setup();
    mock.period = "2020-12";

    render(<Month />);

    expect(screen.getByText("histórico — sem fechamento")).toBeDefined();

    await user.click(screen.getByRole("button", { name: "Trocar mês" }));

    expect(screen.getByTestId("month-opener")).toBeDefined();
    expect((screen.getByLabelText("Ano") as HTMLSelectElement).value).toBe("2020");
    expect((screen.getByLabelText("Mês") as HTMLSelectElement).value).toBe("12");
  });
});

describe("Month strategy targets", () => {
  it("writes every rung — class, strategy, sub-class and ticker — through the targets endpoint", async () => {
    const user = userEvent.setup();
    render(<Month />);
    await user.click(screen.getByTestId("plan-step-rail-targets"));

    await user.type(screen.getByLabelText("Pré-fixado"), "20");
    await user.click(screen.getByRole("button", { name: "Salvar alvos" }));

    expect(mock.replace).toHaveBeenCalledWith(
      {
        classes: [{ asset_class: "ACOES", percentage: 40 }],
        strategies: [{ asset_class: "ACOES", strategy: "Top Dividendos", percentage: 34 }],
        sub_classes: [{ sub_class: "FIXED_RATE", percentage: 20 }],
        tickers: [{ strategy: "Top Dividendos", ticker: "VALE3", percentage: 12 }],
      },
      expect.anything(),
    );
  });

  it("shows a read-only remainder for a class that has a legacy bucket", async () => {
    const user = userEvent.setup();
    mock.strategies = [{ id: 9, name: "Legado Ações", asset_class: "ACOES", kind: "MANUAL" }];
    mock.targets = {
      ...defaultTargets(),
      strategies: [{ asset_class: "ACOES", strategy: "Top Dividendos", percentage: 80 }],
    };

    render(<Month />);
    await user.click(screen.getByTestId("plan-step-rail-targets"));

    expect(screen.getByText(/restante: 20% — posições legadas, sem alvo/)).toBeDefined();
    expect(screen.queryByText(/Fundos imob.*restante/)).toBeNull();
  });

  it("rejects strategy shares above 100 inline and disables saving", async () => {
    const user = userEvent.setup();
    mock.targets = {
      ...defaultTargets(),
      strategies: [
        { asset_class: "ACOES", strategy: "Top Dividendos", percentage: 60 },
        { asset_class: "ACOES", strategy: "Small Caps", percentage: 50 },
      ],
    };

    render(<Month />);
    await user.click(screen.getByTestId("plan-step-rail-targets"));

    expect(screen.getByText(/passa de 100%/)).toBeDefined();
    const save = screen.getByRole("button", { name: "Salvar alvos" }) as HTMLButtonElement;
    expect(save.disabled).toBe(true);
  });

  it("accepts strategy shares that sum to exactly 100", async () => {
    const user = userEvent.setup();
    mock.targets = {
      ...defaultTargets(),
      strategies: [{ asset_class: "ACOES", strategy: "Top Dividendos", percentage: 100 }],
    };

    render(<Month />);
    await user.click(screen.getByTestId("plan-step-rail-targets"));

    expect(screen.queryByText(/passa de 100%/)).toBeNull();
    const save = screen.getByRole("button", { name: "Salvar alvos" }) as HTMLButtonElement;
    expect(save.disabled).toBe(false);
  });
});

describe("Execution step", () => {
  it("summarises executed, partial and unexecuted orders and links to Ordens", async () => {
    mock.orders = monthOrders({
      orders: [
        order({ id: 1, ticker: "ITUB4", quantity: 70, executed_quantity: 70, status: "EXECUTED" }),
        order({
          id: 2,
          ticker: "B3SA3",
          quantity: 200,
          executed_quantity: 200,
          status: "IMPORTED",
        }),
        order({ id: 3, ticker: "VALE3", quantity: 24, executed_quantity: 6 }),
        order({ id: 4, ticker: "EGIE3", quantity: 30, executed_quantity: 0 }),
      ],
    });

    render(
      <MemoryRouter>
        <Month />
      </MemoryRouter>,
    );

    expect(screen.getByTestId("plan-step-execution")).toBeDefined();
    expect(screen.getByTestId("execution-executed").textContent).toBe("2");
    expect(screen.getByTestId("execution-partial").textContent).toBe("1");
    expect(screen.getByTestId("execution-unexecuted").textContent).toBe("1");
    const summary = screen.getByTestId("execution-summary");
    expect(within(summary).getByText("Executadas")).toBeDefined();
    expect(within(summary).getByText("Parciais")).toBeDefined();
    expect(within(summary).getByText("Não executadas")).toBeDefined();

    expect(screen.getByText("PARCIAL")).toBeDefined();
    const link = screen.getByRole("link", { name: /Ver ordens/ });
    expect(link.getAttribute("href")).toBe("/plan/orders");
  });
});

describe("Transfer step", () => {
  it("reflects the pending proposals, their quantities, and links to Transferências", async () => {
    const user = userEvent.setup();
    mock.orders = monthOrders({
      transfers: [
        transfer({ id: 1, ticker: "ITUB4", quantity: 60 }),
        transfer({ id: 2, ticker: "VALE3", quantity: 30, to_strategy: "Small Caps" }),
      ],
    });

    render(
      <MemoryRouter>
        <Month />
      </MemoryRouter>,
    );
    await user.click(screen.getByTestId("plan-step-rail-transfer"));

    expect(screen.getByTestId("plan-step-transfer")).toBeDefined();
    expect(screen.getByTestId("transfer-pending-summary").textContent).toBe(
      "2 transferências pendentes · 90 cotas a mover",
    );
    const first = screen.getByTestId("transfer-step-row-1");
    expect(within(first).getByText("ITUB4")).toBeDefined();
    expect(within(first).getByText("60 cotas")).toBeDefined();
    expect(within(first).getByText("Top Dividendos → Small Caps")).toBeDefined();
    expect(
      within(first).getByText("aprovar move 60 cotas · rejeitar não move nenhuma"),
    ).toBeDefined();

    const link = screen.getByRole("link", { name: /Abrir Transferências/ });
    expect(link.getAttribute("href")).toBe("/plan/transfers");
  });

  it("marks the step decided once nothing is pending and still links to the inbox", async () => {
    const user = userEvent.setup();
    mock.orders = monthOrders({
      transfers: [
        transfer({ id: 1, quantity: 60, status: "APPROVED" }),
        transfer({ id: 2, ticker: "VALE3", quantity: 30, status: "REJECTED" }),
      ],
    });

    render(
      <MemoryRouter>
        <Month />
      </MemoryRouter>,
    );
    await user.click(screen.getByTestId("plan-step-rail-transfer"));

    const cleared = screen.getByTestId("transfer-cleared");
    expect(cleared.textContent).toContain("Nenhuma transferência pendente");
    expect(cleared.textContent).toContain("liberado");
    expect(screen.getByText("2 transferências decididas no histórico")).toBeDefined();
    expect(screen.getByRole("link", { name: /Abrir Transferências/ }).getAttribute("href")).toBe(
      "/plan/transfers",
    );
  });

  it("asks for a plan before there is a transfer to decide", async () => {
    const user = userEvent.setup();
    mock.orders = null;

    render(
      <MemoryRouter>
        <Month />
      </MemoryRouter>,
    );
    await user.click(screen.getByTestId("plan-step-rail-transfer"));

    expect(screen.getByText(/Recalcule o plano/)).toBeDefined();
    expect(screen.getByRole("link", { name: /Abrir Transferências/ }).getAttribute("href")).toBe(
      "/plan/transfers",
    );
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

function monthOrders(overrides: Partial<OrderList> = {}): OrderList {
  return { period: "2026-10", orders: [], transfers: [], unplanned_trades: [], ...overrides };
}

function transfer(overrides: Partial<TransferProposal> = {}): TransferProposal {
  return {
    id: 1,
    ticker: "ITUB4",
    from_strategy_id: 1,
    from_strategy: "Top Dividendos",
    to_strategy_id: 2,
    to_strategy: "Small Caps",
    quantity: 60,
    amount: 2100,
    auto_approved: false,
    status: "PENDING",
    ...overrides,
  };
}
