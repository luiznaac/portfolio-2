// @vitest-environment jsdom
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { formatCurrency, formatPercent } from "../lib/format.ts";
import { Num } from "./Num.tsx";

afterEach(cleanup);

describe("Num", () => {
  it("renders a positive return with an explicit plus sign and the return-up tone", () => {
    render(<Num value={1234.5} format={formatCurrency} tone="return-up" />);
    const element = screen.getByTestId("num");
    expect(element.textContent).toBe("+R$ 1.234,50");
    expect(element.getAttribute("data-tone")).toBe("return-up");
  });

  it("renders a negative return with an explicit minus sign and the return-down tone", () => {
    render(<Num value={-3.2} format={(value) => formatPercent(value)} tone="return-down" />);
    const element = screen.getByTestId("num");
    expect(element.textContent).toBe("−3,20%");
    expect(element.getAttribute("data-tone")).toBe("return-down");
  });

  it("renders a deficit in the deficit tone and a surplus in the surplus tone, sign included", () => {
    render(
      <>
        <Num value={-23580} format={formatCurrency} tone="deficit" testId="deficit" />
        <Num value={30190} format={formatCurrency} tone="surplus" testId="surplus" />
      </>,
    );
    expect(screen.getByTestId("deficit").textContent).toBe("−R$ 23.580,00");
    expect(screen.getByTestId("deficit").getAttribute("data-tone")).toBe("deficit");
    expect(screen.getByTestId("surplus").textContent).toBe("+R$ 30.190,00");
    expect(screen.getByTestId("surplus").getAttribute("data-tone")).toBe("surplus");
  });

  it("renders zero without a sign", () => {
    render(<Num value={0} format={formatCurrency} tone="return-up" />);
    expect(screen.getByTestId("num").textContent).toBe("R$ 0,00");
  });

  it("leaves an unsigned quantity unsigned", () => {
    render(<Num value={100} />);
    const element = screen.getByTestId("num");
    expect(element.textContent).toBe("100");
    expect(element.getAttribute("data-tone")).toBe("default");
  });
});
