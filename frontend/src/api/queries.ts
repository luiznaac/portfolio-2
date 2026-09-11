import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "./client.ts";
import type {
  AssetClassTargetCreation,
  AttributionMovementCreation,
  BondOrderCreation,
  BonusCreation,
  CapitalSnapshotCreation,
  CheckingAccountCreation,
  FixedIncomeSubClassTargetCreation,
  FixedRateBondCreation,
  FloatingRateBondCreation,
  IndexId,
  ListedAssetCreation,
  MovementRequest,
  ProductClassification,
  ReverseSplitCreation,
  SplitCreation,
  StrategyCreation,
  TickerChangeCreation,
  TradeCreation,
  UploadBroker,
  UploadProduct,
} from "./types.ts";

export const keys = {
  bonds: ["bonds"] as const,
  bondPositions: (id: number) => ["bonds", id, "positions"] as const,
  checkingAccounts: ["checking-accounts"] as const,
  checkingAccountPositions: (id: number) => ["checking-accounts", id, "positions"] as const,
  indexes: ["indexes"] as const,
  indexValues: (id: IndexId) => ["indexes", id, "values"] as const,
  listedAssets: ["listed-assets"] as const,
  listedAssetPositions: (id: number) => ["listed-assets", id, "positions"] as const,
  trades: (assetId: number) => ["listed-assets", assetId, "trades"] as const,
  corporateActions: (assetId: number) => ["listed-assets", assetId, "corporate-actions"] as const,
  dividends: (assetId: number) => ["listed-assets", assetId, "dividends"] as const,
  tickerCatalogSearch: (query: string) => ["ticker-catalog", query] as const,
  allocationPlan: ["allocation", "plan"] as const,
  capitalSnapshots: ["allocation", "capital-snapshots"] as const,
  classTargets: ["allocation", "class-targets"] as const,
  fixedIncomeSubClassTargets: ["allocation", "fixed-income-subclass-targets"] as const,
  classifications: ["allocation", "classifications"] as const,
  strategies: ["strategies"] as const,
  strategyEditions: (strategyId: number) => ["strategies", strategyId, "editions"] as const,
  attribution: (assetId: number) => ["listed-assets", assetId, "attribution"] as const,
};

// --- bonds ---

export function useBonds() {
  return useQuery({ queryKey: keys.bonds, queryFn: () => api.listBonds() });
}

export function useBondPositions(id: number) {
  return useQuery({
    queryKey: keys.bondPositions(id),
    queryFn: () => api.bondPositions(id),
  });
}

export function useCreateBond() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (
      body:
        | ({ kind: "fixed" } & FixedRateBondCreation)
        | ({ kind: "floating" } & FloatingRateBondCreation),
    ) => (body.kind === "fixed" ? api.createFixedBond(body) : api.createFloatingBond(body)),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.bonds }),
  });
}

export function useCreateBondOrder() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: BondOrderCreation) => api.createBondOrder(body),
    onSuccess: (_r, body) => {
      qc.invalidateQueries({ queryKey: keys.bonds });
      if (body.bond_id) {
        qc.invalidateQueries({ queryKey: keys.bondPositions(body.bond_id) });
      }
    },
  });
}

// --- checking accounts ---

export function useCheckingAccounts() {
  return useQuery({
    queryKey: keys.checkingAccounts,
    queryFn: () => api.listCheckingAccounts(),
  });
}

export function useCheckingAccountPositions(id: number) {
  return useQuery({
    queryKey: keys.checkingAccountPositions(id),
    queryFn: () => api.checkingAccountPositions(id),
  });
}

export function useCreateCheckingAccount() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: CheckingAccountCreation) => api.createCheckingAccount(body),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.checkingAccounts }),
  });
}

export function useMovement(id: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      kind,
      body,
    }: {
      kind: "deposit" | "withdraw" | "full-withdraw";
      body: MovementRequest;
    }) =>
      kind === "deposit"
        ? api.deposit(id, body)
        : kind === "withdraw"
          ? api.withdraw(id, body)
          : api.fullWithdraw(id, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: keys.checkingAccounts });
      qc.invalidateQueries({ queryKey: keys.checkingAccountPositions(id) });
    },
  });
}

// --- consolidation ---

export function useConsolidate() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (target: { kind: "bond" | "checking-account"; id: number }) =>
      target.kind === "bond"
        ? api.consolidateBond(target.id)
        : api.consolidateCheckingAccount(target.id),
    onSuccess: (_r, target) => {
      qc.invalidateQueries({
        queryKey:
          target.kind === "bond"
            ? keys.bondPositions(target.id)
            : keys.checkingAccountPositions(target.id),
      });
      qc.invalidateQueries({ queryKey: keys.bonds });
      qc.invalidateQueries({ queryKey: keys.checkingAccounts });
    },
  });
}

export function useScheduleConsolidations() {
  return useMutation({ mutationFn: () => api.scheduleConsolidations() });
}

// --- indexes ---

export function useIndexes() {
  return useQuery({ queryKey: keys.indexes, queryFn: () => api.listIndexes() });
}

export function useIndexValues(id: IndexId) {
  return useQuery({
    queryKey: keys.indexValues(id),
    queryFn: () => api.indexValues(id),
  });
}

export function useHydrateIndex() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: IndexId) => api.hydrateIndex(id),
    onSuccess: (_r, id) => qc.invalidateQueries({ queryKey: keys.indexValues(id) }),
  });
}

// --- upload ---

// --- listed assets ---

export function useListedAssets() {
  return useQuery({
    queryKey: keys.listedAssets,
    queryFn: () => api.listListedAssets(),
  });
}

export function useListedAssetPositions(id: number) {
  return useQuery({
    queryKey: keys.listedAssetPositions(id),
    queryFn: () => api.listedAssetPositions(id),
  });
}

export function useCreateListedAsset() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: ListedAssetCreation) => api.createListedAsset(body),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.listedAssets }),
  });
}

export function useConsolidateListedAsset() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => api.consolidateListedAsset(id),
    onSuccess: (_r, id) => {
      qc.invalidateQueries({ queryKey: keys.listedAssetPositions(id) });
      qc.invalidateQueries({ queryKey: keys.listedAssets });
    },
  });
}

export function useDividends(assetId: number) {
  return useQuery({
    queryKey: keys.dividends(assetId),
    queryFn: () => api.listedAssetDividends(assetId),
  });
}

// --- trades ---

export function useTrades(assetId: number) {
  return useQuery({
    queryKey: keys.trades(assetId),
    queryFn: () => api.listTrades(assetId),
  });
}

export function useCreateTrade(assetId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: TradeCreation) => api.createTrade(assetId, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.trades(assetId) }),
  });
}

// --- corporate actions ---

export function useCorporateActions(assetId: number) {
  return useQuery({
    queryKey: keys.corporateActions(assetId),
    queryFn: () => api.listCorporateActions(assetId),
  });
}

export function useCreateCorporateAction(assetId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (
      body:
        | ({ kind: "split" } & SplitCreation)
        | ({ kind: "reverse-split" } & ReverseSplitCreation)
        | ({ kind: "bonus" } & BonusCreation)
        | ({ kind: "ticker-change" } & TickerChangeCreation),
    ) => {
      switch (body.kind) {
        case "split":
          return api.createSplit(assetId, body);
        case "reverse-split":
          return api.createReverseSplit(assetId, body);
        case "bonus":
          return api.createBonus(assetId, body);
        case "ticker-change":
          return api.createTickerChange(assetId, body);
      }
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: keys.corporateActions(assetId) });
      qc.invalidateQueries({ queryKey: keys.listedAssets });
    },
  });
}

// --- ticker catalog ---

export function useTickerCatalogSearch(query: string) {
  return useQuery({
    queryKey: keys.tickerCatalogSearch(query),
    queryFn: () => api.searchTickerCatalog(query),
    enabled: query.trim().length >= 2,
  });
}

// --- allocation ---

export function useAllocationPlan() {
  return useQuery({
    queryKey: keys.allocationPlan,
    queryFn: () => api.allocationPlan(),
  });
}

export function useCapitalSnapshots() {
  return useQuery({
    queryKey: keys.capitalSnapshots,
    queryFn: () => api.capitalSnapshots(),
  });
}

export function useRecordCapitalSnapshot() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: CapitalSnapshotCreation) => api.recordCapitalSnapshot(body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: keys.capitalSnapshots });
      qc.invalidateQueries({ queryKey: keys.allocationPlan });
    },
  });
}

export function useClassTargets() {
  return useQuery({
    queryKey: keys.classTargets,
    queryFn: () => api.classTargets(),
  });
}

export function useSetClassTarget() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: AssetClassTargetCreation) => api.setClassTarget(body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: keys.classTargets });
      qc.invalidateQueries({ queryKey: keys.allocationPlan });
    },
  });
}

export function useFixedIncomeSubClassTargets() {
  return useQuery({
    queryKey: keys.fixedIncomeSubClassTargets,
    queryFn: () => api.fixedIncomeSubClassTargets(),
  });
}

export function useSetFixedIncomeSubClassTarget() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: FixedIncomeSubClassTargetCreation) => api.setFixedIncomeSubClassTarget(body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: keys.fixedIncomeSubClassTargets });
      qc.invalidateQueries({ queryKey: keys.allocationPlan });
    },
  });
}

export function useClassifications() {
  return useQuery({
    queryKey: keys.classifications,
    queryFn: () => api.classifications(),
  });
}

export function useClassify() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: ProductClassification) => api.classify(body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: keys.classifications });
      qc.invalidateQueries({ queryKey: keys.allocationPlan });
    },
  });
}

// --- strategies ---

export function useStrategies() {
  return useQuery({ queryKey: keys.strategies, queryFn: () => api.listStrategies() });
}

export function useCreateStrategy() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: StrategyCreation) => api.createStrategy(body),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.strategies }),
  });
}

export function useStrategyEditions(strategyId: number) {
  return useQuery({
    queryKey: keys.strategyEditions(strategyId),
    queryFn: () => api.listStrategyEditions(strategyId),
  });
}

export function useUploadStrategyReport(strategyId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (file: Blob) => api.uploadStrategyReport(strategyId, file),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.strategyEditions(strategyId) }),
  });
}

// --- attribution ---

export function useAttributionSummary(assetId: number) {
  return useQuery({
    queryKey: keys.attribution(assetId),
    queryFn: () => api.attributionSummary(assetId),
  });
}

export function useRecordAttributionMovement(assetId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: AttributionMovementCreation) => api.recordAttributionMovement(assetId, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.attribution(assetId) }),
  });
}

export function useUploadXlsx() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (args: {
      broker: UploadBroker;
      product: UploadProduct;
      productId: number;
      file: Blob;
    }) => api.uploadXlsx(args.broker, args.product, args.productId, args.file),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: keys.bonds });
      qc.invalidateQueries({ queryKey: keys.checkingAccounts });
    },
  });
}
