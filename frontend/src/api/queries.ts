import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiError, api } from "./client.ts";
import type {
  AttributionSliceCreation,
  Bond,
  CheckingAccount,
  CheckingAccountCreation,
  EditionActivation,
  FixedRateBondCreation,
  FloatingRateBondCreation,
  ImportSource,
  MonthCreation,
  MonthUpdate,
  OrderList,
  StrategyCreation,
  SuggestedOrder,
  Targets,
  TransferProposal,
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

export function useAttribution(ticker: string) {
  return useQuery({
    queryKey: ["positions", ticker, "attribution"],
    queryFn: () => api.getAttribution(ticker),
    enabled: ticker.length > 0,
  });
}

// Both attribution writes invalidate the whole `positions/{ticker}` branch, so the detail and its
// attribution ledger refetch together.
export function useSetAttribution(ticker: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (creation: AttributionSliceCreation) => api.setAttribution(ticker, creation),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["positions", ticker] }),
  });
}

export function useRemoveAttribution(ticker: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (strategyId: number) => api.removeAttribution(ticker, strategyId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["positions", ticker] }),
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

export function useOrders() {
  return useQuery({
    queryKey: ["orders"],
    queryFn: async () => {
      try {
        return await api.getOrders();
      } catch (error) {
        // No open month is a normal state, not an error — the page points at the month opener.
        if (error instanceof ApiError && error.status === 404) return null;
        throw error;
      }
    },
  });
}

export function useRefreshOrders() {
  const queryClient = useQueryClient();
  return useMutation<OrderList, Error, void>({
    mutationFn: () => api.refreshOrders(),
    onSuccess: (list) => {
      queryClient.setQueryData(["orders"], list);
      queryClient.invalidateQueries({ queryKey: ["month"] });
    },
  });
}

// One decision hook for both buttons; the inbox row is replaced by the server's decided proposal.
export function useDecideTransfer() {
  const queryClient = useQueryClient();
  return useMutation<TransferProposal, Error, { id: number; decision: "approve" | "reject" }>({
    mutationFn: ({ id, decision }) =>
      decision === "approve" ? api.approveTransfer(id) : api.rejectTransfer(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["orders"] }),
  });
}

export function useOverrideOrderMatch() {
  const queryClient = useQueryClient();
  return useMutation<SuggestedOrder, Error, { orderId: number; tradeId: number | null }>({
    mutationFn: ({ orderId, tradeId }) => api.overrideOrderMatch(orderId, { trade_id: tradeId }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["orders"] }),
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

export function useStrategies() {
  return useQuery({
    queryKey: ["strategies"],
    queryFn: api.getStrategies,
  });
}

export function useCreateStrategy() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (creation: StrategyCreation) => api.createStrategy(creation),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["strategies"] }),
  });
}

export function useEditions(strategyId: number | null) {
  return useQuery({
    queryKey: ["strategies", strategyId, "editions"],
    queryFn: () => {
      if (strategyId === null) throw new Error("strategyId is required to fetch editions");
      return api.getEditions(strategyId);
    },
    enabled: strategyId !== null,
  });
}

export function useImportEdition() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ strategyId, file }: { strategyId: number; file: File }) =>
      api.importEdition(strategyId, file),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["strategies"] }),
  });
}

export function useActivateEdition() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      strategyId,
      activation,
    }: {
      strategyId: number;
      activation: EditionActivation;
    }) => api.activateEdition(strategyId, activation),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["strategies"] }),
  });
}

export function useIncome() {
  return useQuery({
    queryKey: ["income"],
    queryFn: api.getIncome,
  });
}

export function useUpcomingIncome() {
  return useQuery({
    queryKey: ["income", "upcoming"],
    queryFn: api.getUpcomingIncome,
  });
}

export function useImportIncome() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (file: File) => api.importIncome(file),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["income"] }),
  });
}

export function useCorporateActions() {
  return useQuery({
    queryKey: ["corporate-actions"],
    queryFn: api.getCorporateActions,
  });
}

// One Movimentação upload touches four destinations: trades, income, the corporate-action log and
// the positions their replay feeds.
export function useImportMovements() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (file: File) => api.importMovements(file),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["corporate-actions"] });
      queryClient.invalidateQueries({ queryKey: ["income"] });
      queryClient.invalidateQueries({ queryKey: ["trades"] });
      queryClient.invalidateQueries({ queryKey: ["positions"] });
      queryClient.invalidateQueries({ queryKey: ["imports"] });
    },
  });
}
