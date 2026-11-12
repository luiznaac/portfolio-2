// @vitest-environment jsdom
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type {
  Allocation,
  BackfillReport,
  Gap,
  OpeningSeedCreation,
  PositionReconciliation,
  Quote,
  Trade,
} from "../api/types.ts";
import { History } from "./History.tsx";

const mock = vi.hoisted(() => ({
  allocation: null as Allocation | null,
  trades: [] as Trade[],
  gaps: [] as Gap[],
  quotes: {} as Record<string, Quote[]>,
  seeds: [] as OpeningSeedCreation[],
  backfillFile: null as File | null,
  backfillRuns: 0,
  report: {
    absorbed_seeds: 1,
    adjusted_seeds: 0,
    gaps_created: 2,
    gaps_replaced: 1,
    positions_reprojected: 3,
  } as BackfillReport | null,
  prices: [] as Array<{ id: number; unitPrice: number }>,
  custodyFile: null as File | null,
  reconcile: null as PositionReconciliation[] | null,
}));

vi.mock("../api/queries.ts", () => ({
  useCurrentAllocation: () => ({ data: mock.allocation, isLoading: false }),
  useTrades: () => ({ data: mock.trades, isLoading: false }),
  useGaps: () => ({ data: mock.gaps, isLoading: false }),
  useTickerQuotesList: (tickers: string[]) =>
    tickers.map((ticker) => ({ data: mock.quotes[ticker] ?? [] })),
  useSeedOpenings: () => ({
    mutate: (seeds: OpeningSeedCreation[], options?: { onSuccess?: () => void }) => {
      mock.seeds = seeds;
      options?.onSuccess?.();
    },
    isPending: false,
    error: null,
  }),
  useBackfill: () => ({
    mutate: (
      file: File | undefined,
      options?: { onSuccess?: (report: BackfillReport) => void },
    ) => {
      mock.backfillFile = file ?? null;
      mock.backfillRuns += 1;
      if (mock.report) options?.onSuccess?.(mock.report);
    },
    isPending: false,
    error: null,
    data: mock.report,
  }),
  usePriceGap: () => ({
    mutate: (
      input: { id: number; unitPrice: number },
      options?: { onSuccess?: (gap: Gap) => void },
    ) => {
      mock.prices.push(input);
      options?.onSuccess?.(mock.gaps[0]);
    },
    isPending: false,
    error: null,
  }),
  useReconcileCustody: () => ({
    mutate: (file: File, options?: { onSuccess?: (rows: PositionReconciliation[]) => void }) => {
      mock.custodyFile = file;
      if (mock.reconcile) options?.onSuccess?.(mock.reconcile);
    },
    isPending: false,
    error: null,
    data: mock.reconcile,
  }),
}));

function allocationWith(negative: Array<{ ticker: string; quantity: number }>): Allocation {
  return {
    capital: 1000,
    floor: 0,
    ideal: 0,
    actual: 0,
    deficit: 0,
    drift: 0,
    unattributed: 0,
    negative,
    unpriced: [],
    unclassified: { actual: 0, tickers: [] },
    classes: [],
  };
}

function trade(id: number, date: string, source: Trade["source"] = "B3_IMPORT"): Trade {
  return {
    id,
    ticker: "PETR4",
    date,
    venue: "VISTA",
    side: "BUY",
    quantity: 10,
    unit_price: 10,
    institution_id: 1,
    source,
    confirmed_by_b3: false,
    ordinal: 1,
    absorbed_quantity: 0,
  };
}

function quote(date: string, close: number): Quote {
  return { ticker: "PETR4", date, close, source: "COTAHIST" };
}

function pendingSale(): Gap {
  return {
    id: 11,
    ticker: "VALE3",
    date: "2026-01-10",
    quantity: 5,
    status: "OPEN",
    realized_pending: true,
    trade_watermark: 0,
    action_watermark: 0,
  };
}

function pricedSale(): Gap {
  return {
    id: 13,
    ticker: "PETR4",
    date: "2026-01-10",
    quantity: 3,
    unit_price: 18,
    status: "OPEN",
    realized_pending: false,
    trade_watermark: 0,
    action_watermark: 0,
  };
}

function missingBuy(): Gap {
  return {
    id: 12,
    ticker: "ITUB4",
    date: "2026-01-10",
    quantity: 8,
    unit_price: 9,
    status: "OPEN",
    trade_watermark: 0,
    action_watermark: 0,
  };
}

function renderHistory() {
  return render(
    <MemoryRouter>
      <History />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  mock.allocation = allocationWith([{ ticker: "PETR4", quantity: -10 }]);
  mock.trades = [trade(2, "2026-01-10"), trade(1, "2025-12-01", "OPENING")];
  mock.gaps = [];
  mock.quotes = { PETR4: [quote("2026-01-31", 9), quote("2026-02-27", 12)] };
  mock.seeds = [];
  mock.backfillFile = null;
  mock.backfillRuns = 0;
  mock.prices = [];
  mock.custodyFile = null;
  mock.reconcile = null;
});

afterEach(cleanup);

describe("Histórico", () => {
  it("lists a negative position as an adoption row with the default date and latest close", () => {
    renderHistory();

    expect(screen.getByTestId("adoption-quantity-PETR4")).toHaveProperty("value", "10");
    expect(screen.getByTestId("adoption-date-PETR4")).toHaveProperty("value", "2026-01-09");
    expect(screen.getByTestId("adoption-price-PETR4")).toHaveProperty("value", "12");
  });

  it("posts one opening seed per adoption row and unlocks the replay on save", async () => {
    const user = userEvent.setup();
    renderHistory();

    expect(screen.getByRole("button", { name: "Reconstruir histórico" })).toHaveProperty(
      "disabled",
      true,
    );

    await user.click(screen.getByRole("button", { name: "Salvar adoção" }));

    expect(mock.seeds).toEqual([
      { ticker: "PETR4", quantity: 10, unit_price: 12, date: "2026-01-09" },
    ]);
    expect(screen.getByRole("button", { name: "Reconstruir histórico" })).toHaveProperty(
      "disabled",
      false,
    );
  });

  it("unlocks the replay after an explicit skip", async () => {
    const user = userEvent.setup();
    renderHistory();

    await user.click(screen.getByRole("button", { name: "Pular adoção" }));

    expect(screen.getByRole("button", { name: "Reconstruir histórico" })).toHaveProperty(
      "disabled",
      false,
    );
  });

  it("runs the backfill without a file and renders what it absorbed, created and reprojected", async () => {
    const user = userEvent.setup();
    renderHistory();
    await user.click(screen.getByRole("button", { name: "Pular adoção" }));

    await user.click(screen.getByRole("button", { name: "Reconstruir histórico" }));

    expect(mock.backfillRuns).toBe(1);
    expect(mock.backfillFile).toBeNull();
    expect(screen.getByTestId("backfill-report").textContent).toContain("1 seeds absorvidos");
    expect(screen.getByTestId("backfill-report").textContent).toContain("2 lacunas criadas");
    expect(screen.getByTestId("backfill-report").textContent).toContain("3 posições reprojetadas");
  });

  it("prices a missing sale's proceeds inline and marks a missing buy as approximate", async () => {
    mock.gaps = [pendingSale(), missingBuy()];
    const user = userEvent.setup();
    renderHistory();

    const lacunas = screen.getByTestId("lacunas");
    expect(within(lacunas).getByTestId("gap-approximate-ITUB4").textContent).toContain(
      "aproximado",
    );

    await user.type(within(lacunas).getByTestId("gap-price-11"), "18");
    await user.click(within(lacunas).getByTestId("gap-price-save-11"));

    expect(mock.prices).toEqual([{ id: 11, unitPrice: 18 }]);
  });

  it("corrects an already priced missing sale's proceeds inline", async () => {
    mock.gaps = [pricedSale()];
    const user = userEvent.setup();
    renderHistory();

    const lacunas = screen.getByTestId("lacunas");
    const input = within(lacunas).getByTestId("gap-price-13");
    expect(input).toHaveProperty("value", "18");

    await user.clear(input);
    await user.type(input, "20");
    await user.click(within(lacunas).getByTestId("gap-price-save-13"));

    expect(mock.prices).toEqual([{ id: 13, unitPrice: 20 }]);
  });

  it("renders custody drift from an upload and persists nothing", async () => {
    mock.reconcile = [
      { ticker: "PETR4", custody_quantity: 90, ledger_quantity: 100, has_drift: true },
    ];
    const user = userEvent.setup();
    renderHistory();

    await user.upload(screen.getByLabelText("Posição"), new File(["sheet"], "posicao.xlsx"));
    await user.click(screen.getByRole("button", { name: "Conferir custódia" }));

    expect(mock.custodyFile?.name).toBe("posicao.xlsx");
    const table = screen.getByTestId("custody-drift");
    expect(within(table).getByText("PETR4")).toBeDefined();
    expect(within(table).getByText("90")).toBeDefined();
  });
});
