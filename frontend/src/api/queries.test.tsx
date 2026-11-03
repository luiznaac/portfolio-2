// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, cleanup, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "./client.ts";
import { useApplyImport, useCurrentAllocation, usePositions } from "./queries.ts";
import type { Allocation, Position } from "./types.ts";

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
      id: 7,
      source: "B3_TRADES",
      file_name: "neg.xlsx",
      file_hash: "hash",
      status: "APPLIED",
      created_at: "2026-10-14T10:00:00",
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
