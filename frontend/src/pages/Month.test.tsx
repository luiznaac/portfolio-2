// @vitest-environment jsdom
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { Strategy, Targets } from "../api/types.ts";
import { Month } from "./Month.tsx";

// The targets form renders variable rows (strategies, tickers) whose inputs are controlled by the
// form state. A row must keep its React identity while its fields are edited: a key derived from the
// edited value remounts the input on the first keystroke and the browser drops focus.
const mock = vi.hoisted(() => ({
  targets: undefined as unknown as Targets,
  strategies: [] as Strategy[],
}));

vi.mock("../api/queries.ts", () => {
  const mutation = () => ({
    mutate: vi.fn(),
    isPending: false,
    isSuccess: false,
    error: null,
  });

  return {
    useCurrentMonth: () => ({
      isLoading: false,
      isError: false,
      data: {
        month: { id: 1, period: "2026-10", capital: 100_000, floor: 0, status: "OPEN", aportes: 0 },
        targets: mock.targets,
      },
    }),
    useOpenMonth: mutation,
    useUpdateCurrentMonth: mutation,
    useReplaceTargets: mutation,
    useStrategies: () => ({ data: mock.strategies, isLoading: false, isError: false }),
  };
});

function defaultTargets(): Targets {
  return {
    classes: [],
    strategies: [{ asset_class: "ACOES", strategy: "Top Dividendos", percentage: 34 }],
    sub_classes: [],
    tickers: [{ strategy: "Top Dividendos", ticker: "VALE3", percentage: 12 }],
  };
}

beforeEach(() => {
  mock.targets = defaultTargets();
  mock.strategies = [];
});

afterEach(cleanup);

describe("Month target rows keep focus while typing", () => {
  it("keeps focus on the strategy rung's Estratégia field", async () => {
    const user = userEvent.setup();
    render(<Month />);

    const [strategyInput] = screen.getAllByDisplayValue("Top Dividendos");
    await user.type(strategyInput, "X");

    expect(document.activeElement).toBe(strategyInput);
  });

  it("keeps focus on the ticker rung's Estratégia field", async () => {
    const user = userEvent.setup();
    render(<Month />);

    const [, tickerStrategyInput] = screen.getAllByDisplayValue("Top Dividendos");
    await user.type(tickerStrategyInput, "X");

    expect(document.activeElement).toBe(tickerStrategyInput);
  });

  it("keeps focus on the ticker rung's Ticker field", async () => {
    const user = userEvent.setup();
    render(<Month />);

    const tickerInput = screen.getByDisplayValue("VALE3");
    await user.type(tickerInput, "X");

    expect(document.activeElement).toBe(tickerInput);
  });
});

describe("Month strategy targets", () => {
  it("shows a read-only remainder for a class that has a legacy bucket", () => {
    mock.strategies = [{ id: 9, name: "Legado Ações", asset_class: "ACOES", kind: "MANUAL" }];
    mock.targets = {
      ...defaultTargets(),
      strategies: [{ asset_class: "ACOES", strategy: "Top Dividendos", percentage: 80 }],
    };

    render(<Month />);

    expect(screen.getByText(/restante: 20% — posições legadas, sem alvo/)).toBeDefined();
    expect(screen.queryByText(/Fundos imob.*restante/)).toBeNull();
  });

  it("rejects strategy shares above 100 inline and disables saving", () => {
    mock.targets = {
      ...defaultTargets(),
      strategies: [
        { asset_class: "ACOES", strategy: "Top Dividendos", percentage: 60 },
        { asset_class: "ACOES", strategy: "Small Caps", percentage: 50 },
      ],
    };

    render(<Month />);

    expect(screen.getByText(/passa de 100%/)).toBeDefined();
    const save = screen.getByRole("button", { name: "Salvar alvos" }) as HTMLButtonElement;
    expect(save.disabled).toBe(true);
  });

  it("accepts strategy shares that sum to exactly 100", () => {
    mock.targets = {
      ...defaultTargets(),
      strategies: [{ asset_class: "ACOES", strategy: "Top Dividendos", percentage: 100 }],
    };

    render(<Month />);

    expect(screen.queryByText(/passa de 100%/)).toBeNull();
    const save = screen.getByRole("button", { name: "Salvar alvos" }) as HTMLButtonElement;
    expect(save.disabled).toBe(false);
  });
});
