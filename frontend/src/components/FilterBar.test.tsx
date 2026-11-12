// @vitest-environment jsdom
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { FilterField, FilterValues } from "../lib/filters.ts";
import { matchesText } from "../lib/filters.ts";
import { FilterBar } from "./FilterBar.tsx";

interface Row {
  ticker: string;
  side: "BUY" | "SELL";
}

const fields: FilterField<Row>[] = [
  {
    control: { kind: "text", key: "ticker", label: "Ticker", placeholder: "Ticker" },
    match: (row, value) => matchesText(value, row.ticker),
  },
  {
    control: {
      kind: "select",
      key: "side",
      label: "Lado",
      emptyLabel: "Todos os lados",
      options: [
        { value: "BUY", label: "Compra" },
        { value: "SELL", label: "Venda" },
      ],
    },
    match: (row, value) => row.side === value,
  },
];

const blank: FilterValues = { ticker: "", side: "" };

function renderBar(values: FilterValues, shown: number, total: number, onChange = vi.fn()) {
  const onClear = vi.fn();
  const result = render(
    <FilterBar
      fields={fields}
      values={values}
      onChange={onChange}
      onClear={onClear}
      shown={shown}
      total={total}
    >
      <p>linhas filtradas</p>
    </FilterBar>,
  );
  return { ...result, onChange, onClear };
}

afterEach(cleanup);

describe("FilterBar", () => {
  it("renders a text field, a select, the live count and no clear action while nothing is narrowed", () => {
    renderBar(blank, 3, 3);

    expect(screen.getByLabelText("Ticker")).toBeDefined();
    expect(screen.getByLabelText("Lado")).toBeDefined();
    expect(screen.getByText("3 de 3")).toBeDefined();
    expect(screen.queryByRole("button", { name: "limpar" })).toBeNull();
    expect(screen.getByText("linhas filtradas")).toBeDefined();
  });

  it("reports each control change and offers limpar only while narrowing", async () => {
    const user = userEvent.setup();
    const { onChange, onClear } = renderBar({ ticker: "petr", side: "" }, 2, 3);

    await user.type(screen.getByLabelText("Ticker"), "4");
    await user.selectOptions(screen.getByLabelText("Lado"), "SELL");

    expect(onChange).toHaveBeenCalledWith("ticker", "petr4");
    expect(onChange).toHaveBeenCalledWith("side", "SELL");

    await user.click(screen.getByRole("button", { name: "limpar" }));
    expect(onClear).toHaveBeenCalledTimes(1);
  });

  it("replaces the host with the narrowing empty state and its own way out", async () => {
    const user = userEvent.setup();
    const { onClear } = renderBar({ ticker: "zzz", side: "" }, 0, 3);

    expect(screen.getByText("Nenhuma linha com esse filtro.")).toBeDefined();
    expect(screen.queryByText("linhas filtradas")).toBeNull();

    // Both the bar and the empty state offer the same way out; either clears.
    const clears = screen.getAllByRole("button", { name: "limpar" });
    expect(clears).toHaveLength(2);
    await user.click(clears[1]);
    expect(onClear).toHaveBeenCalledTimes(1);
  });

  it("leaves the host's own empty state to the page when the list has no rows at all", () => {
    renderBar(blank, 0, 0);

    expect(screen.queryByText("Nenhuma linha com esse filtro.")).toBeNull();
    expect(screen.getByText("linhas filtradas")).toBeDefined();
  });
});
