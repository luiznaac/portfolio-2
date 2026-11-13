// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, cleanup, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "./client.ts";
import {
  useApplyImport,
  useCloseMonth,
  useCurrentAllocation,
  useOrders,
  usePositions,
} from "./queries.ts";
import type { Allocation, OrderList, Position } from "./types.ts";

const empty: Allocation = {
  capital: 0,
  floor: 0,
  ideal: 0,
  actual: 0,
  deficit: 0,
  drift: 0,
  unattributed: 0,
  unclassified: { actual: 0, tickers: [] },
  classes: [],
};

const applied = {
  ...empty,
  unpriced: [{ ticker: "PETR4", quantity: -50 }],
  negative: [{ ticker: "PETR4", quantity: -50 }],
  unclassified: {
    actual: 0,
    tickers: [{ ticker: "PETR4", quantity: -50 }],
  },
};

const positions: Position[] = [{ ticker: "PETR4", quantity: -50, average_price: 0 }];

// Applying an import changes the custody and data-quality reads the result summary displays, so
// those hooks must surface the post-apply book without a manual refetch.
describe("useApplyImport", () => {
  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it("refetches the positions and allocation reads after the ledger changes", async () => {
    vi.spyOn(api, "getPositions").mockResolvedValueOnce([]).mockResolvedValue(positions);
    vi.spyOn(api, "getCurrentAllocation").mockResolvedValueOnce(empty).mockResolvedValue(applied);
    vi.spyOn(api, "applyImport").mockResolvedValue({
      record: {
        id: 7,
        source: "B3_TRADES",
        file_name: "neg.xlsx",
        file_hash: "hash",
        status: "APPLIED",
        destination: { object_type: "TRADES" },
        has_original: true,
        created_at: "2026-10-14T10:00:00",
      },
      applied: 1,
      rejected: 0,
      skipped_duplicate: 0,
    });

    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    );

    const book = renderHook(() => usePositions(), { wrapper });
    const allocation = renderHook(() => useCurrentAllocation(), { wrapper });
    const apply = renderHook(() => useApplyImport(), { wrapper });

    await waitFor(() => expect(book.result.current.data).toEqual([]));
    await waitFor(() => expect(allocation.result.current.data).toEqual(empty));

    await act(async () => {
      apply.result.current.mutate(7);
    });

    await waitFor(() => expect(book.result.current.data).toEqual(positions));
    await waitFor(() => expect(allocation.result.current.data).toEqual(applied));
  });
});

// A refused close is not a no-op: the server refreshed the plan and finalised transfers before the
// gate refused, so the inbox must be re-read even though the month did not lock.
describe("useCloseMonth", () => {
  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it("refetches the transfer inbox after a refused close", async () => {
    const pendingTransfer = {
      id: 1,
      ticker: "ITUB4",
      from_strategy_id: 1,
      from_strategy: "Dividendos",
      to_strategy_id: 2,
      to_strategy: "Small Caps",
      quantity: 20,
      amount: 600,
      auto_approved: false,
      status: "PENDING" as const,
    };
    const pending: OrderList = {
      period: "2026-10",
      orders: [],
      transfers: [pendingTransfer],
      unplanned_trades: [],
    };
    const rejected: OrderList = {
      ...pending,
      transfers: [{ ...pendingTransfer, status: "REJECTED" }],
    };

    vi.spyOn(api, "getOrders").mockResolvedValueOnce(pending).mockResolvedValue(rejected);
    vi.spyOn(api, "closeCurrentMonth").mockRejectedValue(new Error("pending-transfers"));

    const client = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    });
    const wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    );

    const orders = renderHook(() => useOrders(), { wrapper });
    const close = renderHook(() => useCloseMonth(), { wrapper });

    await waitFor(() => expect(orders.result.current.data).toEqual(pending));

    await act(async () => {
      close.result.current.mutate();
    });

    await waitFor(() => expect(orders.result.current.data).toEqual(rejected));
  });
});
