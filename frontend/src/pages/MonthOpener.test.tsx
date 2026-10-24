// @vitest-environment jsdom
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { Month } from "./Month.tsx";

const { mutate } = vi.hoisted(() => ({ mutate: vi.fn() }));

// No open month: the page renders the opener.
vi.mock("../api/queries.ts", () => {
  const mutation = () => ({ mutate, isPending: false, isSuccess: false, error: null });

  return {
    useCurrentMonth: () => ({ isLoading: false, isError: false, data: null }),
    useOpenMonth: mutation,
    useUpdateCurrentMonth: mutation,
    useReplaceTargets: mutation,
  };
});

afterEach(() => {
  cleanup();
  mutate.mockReset();
});

// Firefox and Safari do not implement `<input type="month">`, so it renders as a plain text box with
// nothing to pick. The opener must expose the period as concrete month and year options instead.
describe("month opener period", () => {
  it("offers month and year options instead of a native month input", () => {
    render(<Month />);

    const month = screen.getByLabelText("Mês");
    const year = screen.getByLabelText("Ano");

    expect(month).toBeInstanceOf(HTMLSelectElement);
    expect(year).toBeInstanceOf(HTMLSelectElement);
    expect(month.querySelectorAll("option")).toHaveLength(12);
    expect(document.querySelector('input[type="month"]')).toBeNull();
  });

  it("opens the month built from the two selects", async () => {
    const user = userEvent.setup();
    render(<Month />);

    const yearSelect = screen.getByLabelText("Ano") as HTMLSelectElement;
    const year = yearSelect.options[0].value;
    await user.selectOptions(yearSelect, year);
    await user.selectOptions(screen.getByLabelText("Mês"), "02");
    await user.type(screen.getByLabelText("Capital (R$)"), "1000");
    await user.click(screen.getByRole("button", { name: "Abrir mês" }));

    expect(mutate).toHaveBeenCalledWith({ period: `${year}-02`, capital: 1000, floor: 0 });
  });
});
