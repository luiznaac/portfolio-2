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
    useMonthPeriodRange: () => ({
      isLoading: false,
      isError: false,
      data: { min: "2020-03", max: "2026-10", default: "2020-12" },
    }),
    useOpenMonth: mutation,
    useUpdateCurrentMonth: mutation,
    useReplaceTargets: mutation,
    useStrategies: () => ({ data: [], isLoading: false, isError: false }),
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

  it("bounds the years to the server window and names the history floor", () => {
    render(<Month />);

    const years = [...(screen.getByLabelText("Ano") as HTMLSelectElement).options].map(
      (option) => option.value,
    );
    expect(years).toEqual(["2020", "2021", "2022", "2023", "2024", "2025", "2026"]);
    expect(screen.getByText("Histórico desde mar/2020")).toBeDefined();
    expect((screen.getByLabelText("Mês") as HTMLSelectElement).options[0].disabled).toBe(true);
    expect((screen.getByLabelText("Mês") as HTMLSelectElement).options[2].disabled).toBe(false);
  });

  it("opens the month built from the two selects", async () => {
    const user = userEvent.setup();
    render(<Month />);

    await user.selectOptions(screen.getByLabelText("Ano"), "2020");
    await user.selectOptions(screen.getByLabelText("Mês"), "03");
    await user.type(screen.getByLabelText("Capital (R$)"), "1000");
    await user.click(screen.getByRole("button", { name: "Abrir mês" }));

    expect(mutate).toHaveBeenCalledWith(
      { period: "2020-03", capital: 1000, floor: 0 },
      expect.anything(),
    );
  });
});
