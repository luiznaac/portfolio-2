import {
  type QueryClient,
  useMutation,
  useQueries,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import { ApiError, api } from "./client.ts";
import type {
  AssetClass,
  AttributionSliceCreation,
  AttributionTransferCreation,
  BackfillReport,
  BenchmarkStatusReport,
  Bond,
  CheckingAccount,
  CheckingAccountCreation,
  Edition,
  EditionActivation,
  FixedRateBondCreation,
  FloatingRateBondCreation,
  Gap,
  HarvestPairCreation,
  HarvestReport,
  ImportSource,
  LegacyAttributionResult,
  MonthCreation,
  MonthUpdate,
  OpeningSeedCreation,
  OrderList,
  OrderPlanPhase,
  PositionReconciliation,
  Quote,
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

// The portfolio's standing status, not a screen read: the shell's sidebar counts, the top bar's
// freshness line and the landing all consume it, so every write that changes attention refreshes it.
export function usePortfolioStatus() {
  return useQuery({
    queryKey: ["portfolio", "status"],
    queryFn: api.getPortfolioStatus,
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

// The stored custody projection — the import result counts how many positions the book holds and
// how many are negative, which trades alone cannot answer.
export function usePositions() {
  return useQuery({
    queryKey: ["positions"],
    queryFn: api.getPositions,
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

// The month-free manual transfer: the attribution ledger and the open month's tree both read the
// moved units, so one invalidation refreshes the detail, the leaf and the class Actual.
export function useTransferAttribution(ticker: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (creation: AttributionTransferCreation) =>
      api.transferAttribution(ticker, creation),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["positions", ticker] });
      queryClient.invalidateQueries({ queryKey: ["month"] });
      invalidatePortfolioStatus(queryClient);
    },
  });
}

// The bulk legacy action's dry-run. It is enabled only while the Alocação banner has outside
// custody to offer, so the page does not pay for the read when there is nothing to attribute.
export function useLegacyAttributionPreview(enabled: boolean) {
  return useQuery({
    queryKey: ["positions", "legacy-attribution", "preview"],
    queryFn: api.getLegacyAttributionPreview,
    enabled,
  });
}

// The apply writes slices through the attribution path, so the position, allocation, strategy and
// order reads are all stale in one step.
export function useApplyLegacyAttribution() {
  const queryClient = useQueryClient();
  return useMutation<LegacyAttributionResult, Error, void>({
    mutationFn: () => api.applyLegacyAttribution(),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["positions"] });
      queryClient.invalidateQueries({ queryKey: ["month"] });
      queryClient.invalidateQueries({ queryKey: ["strategies"] });
      invalidatePortfolioStatus(queryClient);
    },
  });
}

export function useTickerQuotes(ticker: string) {
  return useQuery({
    queryKey: ["tickers", ticker, "quotes"],
    queryFn: () => api.getTickerQuotes(ticker),
    enabled: ticker.length > 0,
  });
}

// The Histórico adoption rows need every draft ticker's latest close at once: one read per ticker,
// sharing the single-ticker cache key, so the default price is in place before the form is saved.
export function useTickerQuotesList(tickers: string[]) {
  return useQueries({
    queries: tickers.map((ticker) => ({
      queryKey: ["tickers", ticker, "quotes"],
      queryFn: (): Promise<Quote[]> => api.getTickerQuotes(ticker),
      enabled: ticker.length > 0,
    })),
  });
}

// The ticker's catalog row — its BDI-derived class and any manual override. A 404 (a ticker the
// catalog has never seen) is a normal state for the classification action, not an error.
export function useTicker(ticker: string) {
  return useQuery({
    queryKey: ["tickers", ticker],
    queryFn: () => api.getTicker(ticker),
    enabled: ticker.length > 0,
    retry: false,
  });
}

// The reads derived from the ledger, catalog and quotes. A classification, a catalog refresh or an
// import apply changes at least one of them, so they share one invalidation instead of each caller
// spelling out the set.
// The shell's aggregate is stale whenever attention can change; every write that touches the
// month, orders, imports, strategies or the ledger refreshes it with this one call.
function invalidatePortfolioStatus(queryClient: QueryClient) {
  queryClient.invalidateQueries({ queryKey: ["portfolio", "status"] });
}

function invalidateLedgerReads(queryClient: QueryClient) {
  queryClient.invalidateQueries({ queryKey: ["tickers"] });
  queryClient.invalidateQueries({ queryKey: ["month"] });
  queryClient.invalidateQueries({ queryKey: ["months"] });
  queryClient.invalidateQueries({ queryKey: ["orders"] });
  queryClient.invalidateQueries({ queryKey: ["positions"] });
  queryClient.invalidateQueries({ queryKey: ["performance"] });
  invalidatePortfolioStatus(queryClient);
}

export function useClassifyTicker(ticker: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (assetClass: AssetClass) => api.classifyTicker(ticker, assetClass),
    onSuccess: () => invalidateLedgerReads(queryClient),
  });
}

export function useRevertTickerClassification(ticker: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => api.revertTickerClassification(ticker),
    onSuccess: () => invalidateLedgerReads(queryClient),
  });
}

export function useRefreshCatalog() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: api.refreshCatalog,
    onSuccess: () => invalidateLedgerReads(queryClient),
  });
}

// The held-unclassified classification queue the bulk modal lists. The modal mounts only while it
// is open, so the read starts on demand and the page never pays for it otherwise.
export function useClassificationQueue() {
  return useQuery({
    queryKey: ["tickers", "classification-queue"],
    queryFn: api.getClassificationQueue,
  });
}

// The bulk classification write: one atomic request for the chosen rows, then the same class-keyed
// reads the single classify invalidates, so the tree, panel and plan all refresh together.
export function useClassifyTickers() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (classifications: Record<string, AssetClass>) =>
      api.classifyTickers(classifications),
    onSuccess: () => invalidateLedgerReads(queryClient),
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

// The same window every month picker reads: the opener, its validation and Impostos' period picker.
export function useMonthPeriodRange() {
  return useQuery({
    queryKey: ["month", "period-range"],
    queryFn: api.getMonthPeriodRange,
  });
}

export function useOpenMonth() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (creation: MonthCreation) => api.openMonth(creation),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["month"] });
      invalidatePortfolioStatus(queryClient);
    },
  });
}

export function useUpdateCurrentMonth() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (update: MonthUpdate) => api.updateCurrentMonth(update),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["month"] });
      invalidatePortfolioStatus(queryClient);
    },
  });
}

export function useReplaceTargets() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (targets: Targets) => api.replaceCurrentTargets(targets),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["month"] });
      invalidatePortfolioStatus(queryClient);
    },
  });
}

export function useCurrentAllocation() {
  return useQuery({
    queryKey: ["month", "current", "allocation"],
    // The read is month-free: it answers even without an open month, with Actual only.
    queryFn: api.getCurrentAllocation,
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
      invalidatePortfolioStatus(queryClient);
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

// The stored benchmark series' freshness, read by the "Dados da carteira" panel. The manual re-check
// returns the new freshness in the same round trip and invalidates the performance reads it feeds.
export function useBenchmarkStatus() {
  return useQuery({
    queryKey: ["benchmarks", "status"],
    queryFn: api.getBenchmarkStatus,
  });
}

export function useRefreshBenchmarks() {
  const queryClient = useQueryClient();
  return useMutation<BenchmarkStatusReport, Error, void>({
    mutationFn: api.refreshBenchmarks,
    onSuccess: (report) => {
      queryClient.setQueryData(["benchmarks", "status"], report);
      queryClient.invalidateQueries({ queryKey: ["performance"] });
    },
  });
}

export function useStockTax(period: string) {
  return useQuery({
    queryKey: ["tax", period],
    queryFn: () => api.getStockTax(period),
    enabled: period.length > 0,
  });
}

// Harvest is an open-month write-shaped pass: the page disables the read when the selected period is
// not the open month, so a historical read never pays for it.
export function useHarvest(enabled = true) {
  return useQuery({
    queryKey: ["tax", "harvest"],
    queryFn: nullOn404(api.getHarvest),
    enabled,
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

// The computed plan read (never the persisted lifecycle): Alocação reads the funding arithmetic and
// the outside-strategy holdings the persisted list does not carry. The funding phase is the
// shallowest depth that carries both, so the screen does not pay for the pro-rata/attribution pass.
export function useOrderPlan(phase: OrderPlanPhase = "FUNDING") {
  return useQuery({
    queryKey: ["month", "current", "orders", phase.toLowerCase()],
    queryFn: nullOn404(() => api.getOrderPlan(phase)),
  });
}

// One editions read per strategy, sharing the `useEditions` cache key, so the catalog's status chips
// ask in parallel rather than opening each detail.
function useEditionsFor(strategies: Strategy[]) {
  return useQueries({
    queries: strategies.map((strategy) => ({
      queryKey: ["strategies", strategy.id, "editions"],
      queryFn: (): Promise<Edition[]> => api.getEditions(strategy.id),
    })),
  });
}

// The catalog's chips always fetch: a chip shows the latest edition even when no month is open.
export function useStrategyEditions(strategies: Strategy[]) {
  return useEditionsFor(strategies);
}

export function useRefreshOrders() {
  const queryClient = useQueryClient();
  return useMutation<OrderList, Error, void>({
    mutationFn: () => api.refreshOrders(),
    onSuccess: (list) => {
      queryClient.setQueryData(["orders"], list);
      queryClient.invalidateQueries({ queryKey: ["month"] });
      invalidatePortfolioStatus(queryClient);
    },
  });
}

// One decision hook for both buttons; the inbox row is replaced by the server's decided proposal.
export function useDecideTransfer() {
  const queryClient = useQueryClient();
  return useMutation<TransferProposal, Error, { id: number; decision: "approve" | "reject" }>({
    mutationFn: ({ id, decision }) =>
      decision === "approve" ? api.approveTransfer(id) : api.rejectTransfer(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["orders"] });
      invalidatePortfolioStatus(queryClient);
    },
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
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["imports"] });
      invalidatePortfolioStatus(queryClient);
    },
  });
}

export function useReviewImport() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (importId: number) => api.reviewImport(importId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["imports"] });
      invalidatePortfolioStatus(queryClient);
    },
  });
}

export function useApplyImport() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (importId: number) => api.applyImport(importId),
    // Apply writes the ledger and auto-enters unknown tickers into the catalog, so the result's
    // custody and data-quality counts must refresh alongside the import and trade lists.
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["imports"] });
      queryClient.invalidateQueries({ queryKey: ["trades"] });
      invalidateLedgerReads(queryClient);
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
  return useMutation<Strategy, Error, StrategyCreation>({
    mutationFn: (creation: StrategyCreation) => api.createStrategy(creation),
    // The created Strategy enters the cached catalog immediately, so the page can select it
    // without waiting for the refetch; the invalidation still reconciles with the server's order.
    onSuccess: (created) => {
      queryClient.setQueryData<Strategy[]>(["strategies"], (current) =>
        current === undefined
          ? [created]
          : [...current.filter((item) => item.id !== created.id), created],
      );
      queryClient.invalidateQueries({ queryKey: ["strategies"] });
    },
  });
}

export function useStrategyHoldings(strategyId: number | null) {
  return useQuery({
    queryKey: ["strategies", strategyId, "holdings"],
    queryFn: () => {
      if (strategyId === null) throw new Error("strategyId is required to fetch holdings");
      return api.getStrategyHoldings(strategyId);
    },
    enabled: strategyId !== null,
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
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["strategies"] });
      invalidatePortfolioStatus(queryClient);
    },
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
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["strategies"] });
      invalidatePortfolioStatus(queryClient);
    },
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

// The open and replaced GAP records. The data-quality panel and the gaps screen share one cache
// entry, so their counts stay in step.
export function useGaps() {
  return useQuery({
    queryKey: ["gaps"],
    queryFn: api.getGaps,
  });
}

// Every history write replays positions from the ledger: the GAP list, the trades, the custody
// projection and the month-scoped reads they feed are stale in one step.
function invalidateHistoryReads(queryClient: QueryClient) {
  queryClient.invalidateQueries({ queryKey: ["gaps"] });
  queryClient.invalidateQueries({ queryKey: ["positions"] });
  queryClient.invalidateQueries({ queryKey: ["trades"] });
  queryClient.invalidateQueries({ queryKey: ["month"] });
  invalidatePortfolioStatus(queryClient);
}

export function usePriceGap() {
  const queryClient = useQueryClient();
  return useMutation<Gap, Error, { id: number; unitPrice: number }>({
    mutationFn: ({ id, unitPrice }) => api.priceGap(id, unitPrice),
    onSuccess: () => invalidateHistoryReads(queryClient),
  });
}

export function useSeedOpenings() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (seeds: OpeningSeedCreation[]) => api.seedOpenings(seeds),
    onSuccess: () => invalidateHistoryReads(queryClient),
  });
}

export function useBackfill() {
  const queryClient = useQueryClient();
  return useMutation<BackfillReport, Error, File | undefined>({
    mutationFn: (file) => api.backfill(file),
    onSuccess: () => invalidateHistoryReads(queryClient),
  });
}

// Read-only: a custody cross-check never edits the ledger, so nothing is invalidated.
export function useReconcileCustody() {
  return useMutation<PositionReconciliation[], Error, File>({
    mutationFn: (file) => api.reconcileCustody(file),
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
      invalidatePortfolioStatus(queryClient);
    },
  });
}
