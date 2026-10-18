import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiError, api } from "./client.ts";
import type {
  Bond,
  CheckingAccount,
  CheckingAccountCreation,
  FixedRateBondCreation,
  FloatingRateBondCreation,
  ImportSource,
  MonthCreation,
  MonthUpdate,
  Targets,
} from "./types.ts";

// The three create shapes the RF form can produce, tagged so one mutation dispatches to the right
// endpoint. It is the mutation's input contract, not an edge DTO, so it lives here rather than in
// types.ts (the hand-maintained mirror of the backend's DTOs).
export type FixedIncomeProductInput =
  | { kind: "FIXED_BOND"; creation: FixedRateBondCreation }
  | { kind: "FLOATING_BOND"; creation: FloatingRateBondCreation }
  | { kind: "ACCOUNT"; creation: CheckingAccountCreation };

export function useHealth() {
  return useQuery({
    queryKey: ["health"],
    queryFn: api.getHealth,
  });
}

export function useImportRows(importId: number | null) {
  return useQuery({
    queryKey: ["imports", importId, "rows"],
    queryFn: () => {
      if (importId === null) throw new Error("importId is required to fetch rows");
      return api.getImportRows(importId);
    },
    enabled: importId !== null,
  });
}

export function useTrades() {
  return useQuery({
    queryKey: ["trades"],
    queryFn: api.getTrades,
  });
}

export function usePosition(ticker: string) {
  return useQuery({
    queryKey: ["positions", ticker],
    queryFn: () => api.getPosition(ticker),
    enabled: ticker.length > 0,
  });
}

export function useTickerQuotes(ticker: string) {
  return useQuery({
    queryKey: ["tickers", ticker, "quotes"],
    queryFn: () => api.getTickerQuotes(ticker),
    enabled: ticker.length > 0,
  });
}

export function useBonds() {
  return useQuery({
    queryKey: ["fixed-income", "bonds"],
    queryFn: api.getBonds,
  });
}

export function useCheckingAccounts() {
  return useQuery({
    queryKey: ["fixed-income", "accounts"],
    queryFn: api.getCheckingAccounts,
  });
}

// One hook for the three create shapes the lançamentos form produces; the tag picks the endpoint and
// a single invalidation refreshes both product lists.
export function useCreateFixedIncomeProduct() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: FixedIncomeProductInput): Promise<Bond | CheckingAccount> => {
      switch (input.kind) {
        case "FIXED_BOND":
          return api.createFixedRateBond(input.creation);
        case "FLOATING_BOND":
          return api.createFloatingRateBond(input.creation);
        case "ACCOUNT":
          return api.createCheckingAccount(input.creation);
      }
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["fixed-income"] }),
  });
}

export function useCurrentMonth() {
  return useQuery({
    queryKey: ["month", "current"],
    queryFn: async () => {
      try {
        return await api.getCurrentMonth();
      } catch (error) {
        // No open month is a normal state, not an error — the page offers the opener instead.
        if (error instanceof ApiError && error.status === 404) return null;
        throw error;
      }
    },
  });
}

export function useOpenMonth() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (creation: MonthCreation) => api.openMonth(creation),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["month"] }),
  });
}

export function useUpdateCurrentMonth() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (update: MonthUpdate) => api.updateCurrentMonth(update),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["month"] }),
  });
}

export function useReplaceTargets() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (targets: Targets) => api.replaceCurrentTargets(targets),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["month"] }),
  });
}

export function useCurrentAllocation() {
  return useQuery({
    queryKey: ["month", "current", "allocation"],
    queryFn: async () => {
      try {
        return await api.getCurrentAllocation();
      } catch (error) {
        // No open month is a normal state, not an error — the page points at the opener instead.
        if (error instanceof ApiError && error.status === 404) return null;
        throw error;
      }
    },
  });
}

export function useStageImport() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ source, file }: { source: ImportSource; file: File }) =>
      api.stageImport(source, file),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["imports"] }),
  });
}

export function useReviewImport() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (importId: number) => api.reviewImport(importId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["imports"] }),
  });
}

export function useApplyImport() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (importId: number) => api.applyImport(importId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["imports"] });
      queryClient.invalidateQueries({ queryKey: ["trades"] });
    },
  });
}
