// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, within } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { Month, Snapshot } from "../api/types.ts";
import { PortfolioHistory } from "./PortfolioHistory.tsx";
import { PortfolioHistoryDetail } from "./PortfolioHistoryDetail.tsx";

const mock = vi.hoisted(() => ({
  months: [] as Month[],
  snapshot: null as Snapshot | null,
}));

vi.mock("../api/client.ts", () => ({
  api: {
    getClosedMonths: () => Promise.resolve(mock.months),
    getMonthSnapshot: (monthId: number) => {
      if (mock.snapshot === null || monthId !== mock.snapshot.month_id) {
        return Promise.reject(new Error(`no snapshot for month ${monthId}`));
      }
      return Promise.resolve(mock.snapshot);
    },
  },
}));

// The live closed-month row carries a Capital the snapshot no longer matches; the frozen detail must
// render the snapshot's value, never the current row's.
const liveMonth: Month = {
  id: 9,
  period: "2026-09",
  capital: 999_999,
  floor: 20_000,
  status: "CLOSED",
  aportes: 2_000,
};

const olderMonth: Month = {
  id: 8,
  period: "2026-08",
  capital: 230_000,
  floor: 20_000,
  status: "CLOSED",
  aportes: 0,
};

const snapshot: Snapshot = {
  id: 3,
  month_id: 9,
  created_at: "2026-09-30T18:05:00",
  state: {
    period: "2026-09",
    capital: 240_000,
    floor: 20_000,
    aportes: 2_000,
    caixa: 45_000,
    holdings: [{ ticker: "ITUB4", quantity: 100, average_price: 10 }],
    attributions: [],
    classes: [
      {
        asset_class: "ACOES",
        target: 60,
        ideal: 144_000,
        actual: 150_000,
        deficit: -6_000,
        drift: 2.5,
        unattributed: 0,
      },
      {
        asset_class: "RENDA_FIXA",
        ideal: 96_000,
        actual: 45_000,
        deficit: 51_000,
        drift: -21.2,
        unattributed: 45_000,
      },
    ],
    strategies: [
      {
        strategy_id: 1,
        strategy: "Top Dividendos",
        kind: "REPORT_DRIVEN",
        value_end: 90_000,
        flow: 2_000,
      },
      {
        strategy_id: 2,
        strategy: "Legado",
        kind: "MANUAL",
        value_end: 60_000,
        flow: -1_000,
      },
    ],
    income: { gross: 100, retained: 15, net: 85 },
    orders: {
      orders: [
        {
          id: 1,
          ticker: "ITUB4",
          side: "BUY",
          quantity: 10,
          suggested_price: 30,
          executed_quantity: 5,
          status: "SUGGESTED",
        },
      ],
      transfers: [],
      unplanned_trades: [{ id: 5, ticker: "VALE3", side: "SELL", quantity: 3 }],
    },
    tax: {
      carry: [],
      meter: {
        gross_sales: 0,
        exemption_limit: 20_000,
        exceeded: false,
        realized_gain: 0,
        taxable_gain: 0,
        rate: 15,
        tax: 250,
      },
    },
  },
};

function renderAt(path: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/portfolio/history" element={<PortfolioHistory />} />
          <Route path="/portfolio/history/:period" element={<PortfolioHistoryDetail />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

afterEach(() => {
  cleanup();
  mock.months = [];
  mock.snapshot = null;
});

describe("Carteira · Histórico", () => {
  it("shows one record per closed month and links it to the frozen detail", async () => {
    mock.months = [liveMonth, olderMonth];
    renderAt("/portfolio/history");

    const list = await screen.findByTestId("portfolio-history");
    const links = within(list).getAllByRole("link");
    expect(links.map((link) => link.textContent)).toEqual(["set/2026", "ago/2026"]);
    expect(links[0]?.getAttribute("href")).toBe("/portfolio/history/2026-09");
    expect(within(list).getByText("R$ 999.999,00")).toBeTruthy();
  });

  it("renders the frozen classes, orders, unplanned trades and strategy slices", async () => {
    mock.months = [liveMonth, olderMonth];
    mock.snapshot = snapshot;
    renderAt("/portfolio/history/2026-09");

    const detail = await screen.findByTestId("frozen-month");
    expect(within(detail).getByText("Ações")).toBeTruthy();
    expect(within(detail).getByText("R$ 150.000,00")).toBeTruthy();
    expect(within(detail).getByText("60,0%")).toBeTruthy();
    expect(within(detail).getByText("ITUB4")).toBeTruthy();
    expect(within(detail).getByText("PARCIAL")).toBeTruthy();
    expect(within(detail).getByText("Não planejada")).toBeTruthy();
    expect(within(detail).getByText("VALE3")).toBeTruthy();
    expect(within(detail).getByText("Top Dividendos")).toBeTruthy();
    expect(within(detail).getByText("Manual (legado)")).toBeTruthy();
    expect(within(detail).getByTestId("frozen-caption").textContent).toContain(
      "Nada aqui é recalculado depois",
    );
  });

  it("renders the snapshot values even when the live month no longer matches them", async () => {
    mock.months = [liveMonth, olderMonth];
    mock.snapshot = snapshot;
    renderAt("/portfolio/history/2026-09");

    const detail = await screen.findByTestId("frozen-month");
    expect(within(detail).getByText("R$ 240.000,00")).toBeTruthy();
    expect(within(detail).queryByText("R$ 999.999,00")).toBeNull();
  });

  it("offers no write action anywhere on the frozen surface", async () => {
    mock.months = [liveMonth, olderMonth];
    mock.snapshot = snapshot;
    renderAt("/portfolio/history/2026-09");

    await screen.findByTestId("frozen-month");
    expect(screen.queryAllByRole("button")).toHaveLength(0);
  });
});
