import { useMutation, useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiError, api } from "./client.ts";
import type {
  AttributionSliceCreation,
  Bond,
  CheckingAccount,
  CheckingAccountCreation,
  Edition,
  EditionActivation,
  FixedRateBondCreation,
  FloatingRateBondCreation,
  HarvestPairCreation,
  HarvestReport,
  ImportSource,
  MonthCreation,
  MonthUpdate,
  OrderList,
  Snapshot,
  Strategy,
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

// No open month is a normal state, not an error — every month-scoped read resolves to null so the
// page points at the month opener instead of rendering a failure.
function nullOn404<T>(query: () => Promise<T>): () => Promise<T | null> {
  return async () => {
    try {
      return await query();
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) return null;
      throw error;
    }
  };
}

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
    queryFn: nullOn404(api.getCurrentMonth),
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
    queryFn: nullOn404(api.getCurrentAllocation),
  });
}

export function useClosedMonths() {
  return useQuery({
    queryKey: ["months"],
    queryFn: api.getClosedMonths,
  });
}

export function useMonthSnapshot(monthId: number | null) {
  return useQuery({
    queryKey: ["months", monthId],
    queryFn: () => {
      if (monthId === null) throw new Error("monthId is required to fetch a snapshot");
      return api.getMonthSnapshot(monthId);
    },
    enabled: monthId !== null,
  });
}

// Closing a month locks it and rewrites the order plan on the way in; the current month, the closed
// list, the order inbox and every performance read are stale in one step.
export function useCloseMonth() {
  const queryClient = useQueryClient();
  return useMutation<Snapshot, Error, void>({
    mutationFn: () => api.closeCurrentMonth(),
    onSuccess: (snapshot) => {
      queryClient.setQueryData(["months", snapshot.month_id], snapshot);
      queryClient.invalidateQueries({ queryKey: ["month"] });
      queryClient.invalidateQueries({ queryKey: ["months"] });
      queryClient.invalidateQueries({ queryKey: ["orders"] });
      queryClient.invalidateQueries({ queryKey: ["performance"] });
    },
  });
}

export function useMonthPerformance(monthId: number | null) {
  return useQuery({
    queryKey: ["months", monthId, "performance"],
    queryFn: () => {
      if (monthId === null) throw new Error("monthId is required to fetch performance");
      return api.getMonthPerformance(monthId);
    },
    enabled: monthId !== null,
  });
}

export function usePerformanceSeries() {
  return useQuery({
    queryKey: ["performance", "series"],
    queryFn: api.getPerformanceSeries,
  });
}

export function useStockTax() {
  return useQuery({
    queryKey: ["tax", "current"],
    queryFn: nullOn404(api.getStockTax),
  });
}

export function useHarvest() {
  return useQuery({
    queryKey: ["tax", "harvest"],
    queryFn: nullOn404(api.getHarvest),
  });
}

// Both harvest writes return the fresh report, so the page updates in one round trip and the tax
// read (whose headroom the pass depends on) is refreshed beside it.
export function useAcceptHarvestPair() {
  const queryClient = useQueryClient();
  return useMutation<HarvestReport, Error, HarvestPairCreation>({
    mutationFn: (creation) => api.acceptHarvestPair(creation),
    onSuccess: (report) => queryClient.setQueryData(["tax", "harvest"], report),
  });
}

export function useSetHarvestOptOut() {
  const queryClient = useQueryClient();
  return useMutation<HarvestReport, Error, { ticker: string; optedOut: boolean }>({
    mutationFn: ({ ticker, optedOut }) => api.setHarvestOptOut(ticker, optedOut),
    onSuccess: (report) => queryClient.setQueryData(["tax", "harvest"], report),
  });
}

export function useOrders() {
  return useQuery({
    queryKey: ["orders"],
    queryFn: nullOn404(api.getOrders),
  });
}

// The computed plan read (never the persisted lifecycle): the Cockpit reads the funding arithmetic
// and the outside-strategy holdings the persisted list does not carry. The funding phase is the
// shallowest depth that carries both, so the Cockpit does not pay for the pro-rata/attribution pass.
export function useOrderPlan() {
  return useQuery({
    queryKey: ["month", "current", "orders", "funding"],
    queryFn: nullOn404(() => api.getOrderPlan("FUNDING")),
  });
}

// One editions read per strategy, sharing the `useEditions` cache key. The Cockpit only needs the
// coverage count, so it asks in parallel rather than opening each strategy's detail.
export function useEditionCoverage(strategies: Strategy[], period: string | undefined) {
  return useQueries({
    queries: strategies.map((strategy) => ({
      queryKey: ["strategies", strategy.id, "editions"],
      queryFn: (): Promise<Edition[]> => api.getEditions(strategy.id),
      enabled: period !== undefined,
    })),
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
