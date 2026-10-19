// @vitest-environment jsdom
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { Month } from "./Month.tsx";

// The targets form renders variable rows (strategies, tickers) whose inputs are controlled by the
// form state. A row must keep its React identity while its fields are edited: a key derived from the
// edited value remounts the input on the first keystroke and the browser drops focus.
vi.mock("../api/queries.ts", () => {
  const current = {
    month: { id: 1, period: "2026-10", capital: 100_000, floor: 0, status: "OPEN" },
    targets: {
      classes: [],
      strategies: [{ asset_class: "ACOES", strategy: "Top Dividendos", percentage: 34 }],
      sub_classes: [],
      tickers: [{ strategy: "Top Dividendos", ticker: "VALE3", percentage: 12 }],
    },
  };
  const mutation = () => ({
    mutate: vi.fn(),
    isPending: false,
    isSuccess: false,
    error: null,
  });

  return {
    useCurrentMonth: () => ({ isLoading: false, isError: false, data: current }),
    useOpenMonth: mutation,
    useUpdateCurrentMonth: mutation,
    useReplaceTargets: mutation,
  };
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
