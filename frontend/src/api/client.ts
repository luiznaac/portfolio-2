import type {
  AllocationPlan,
  AssetClassTarget,
  AssetClassTargetCreation,
  AttributionMovementCreation,
  AttributionSummary,
  Bond,
  BondOrder,
  BondOrderCreation,
  BonusCreation,
  CapitalSnapshot,
  CapitalSnapshotCreation,
  CheckingAccount,
  CheckingAccountCreation,
  CorporateAction,
  DividendDeclaration,
  FixedIncomeSubClassTarget,
  FixedIncomeSubClassTargetCreation,
  FixedRateBondCreation,
  FloatingRateBondCreation,
  Index,
  IndexId,
  IndexValue,
  ListedAsset,
  ListedAssetCreation,
  MovementRequest,
  Position,
  ProductClassification,
  ReverseSplitCreation,
  SplitCreation,
  Strategy,
  StrategyCreation,
  StrategyEditionWithDiff,
  TickerCatalogEntry,
  TickerChangeCreation,
  Trade,
  TradeCreation,
  UploadBroker,
  UploadProduct,
} from "./types.ts";

const BASE = (import.meta.env.VITE_API_BASE ?? "/api").replace(/\/$/, "");

const XLSX_MIME =
  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
const PDF_MIME = "application/pdf";

export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    ...init,
    headers:
      init?.body instanceof Blob
        ? init.headers
        : { "Content-Type": "application/json", ...init?.headers },
  });
  if (!res.ok) {
    let detail = res.statusText;
    try {
      const text = await res.text();
      if (text) {
        try {
          const body = JSON.parse(text);
          detail = body.detail ?? body.message ?? text;
        } catch {
          detail = text;
        }
      }
    } catch {
      /* body already consumed / unavailable */
    }
    throw new ApiError(res.status, detail);
  }
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

const json = (method: string, body?: unknown): RequestInit => ({
  method,
  ...(body === undefined ? {} : { body: JSON.stringify(body) }),
});

export const api = {
  // --- bonds ---
  listBonds(): Promise<Bond[]> {
    return request(`/bonds`);
  },
  createFixedBond(body: FixedRateBondCreation): Promise<Bond> {
    return request(`/bonds/fixed`, json("POST", body));
  },
  createFloatingBond(body: FloatingRateBondCreation): Promise<Bond> {
    return request(`/bonds/floating`, json("POST", body));
  },
  consolidateBond(id: number): Promise<void> {
    return request(`/bonds/${id}/consolidate`, json("POST"));
  },
  bondPositions(id: number): Promise<Position[]> {
    return request(`/bonds/${id}/positions`);
  },
  bondLastPosition(id: number): Promise<Position> {
    return request(`/bonds/${id}/positions/last`);
  },
  createBondOrder(body: BondOrderCreation): Promise<BondOrder> {
    return request(`/bonds/orders`, json("POST", body));
  },

  // --- checking accounts ---
  listCheckingAccounts(): Promise<CheckingAccount[]> {
    return request(`/checking-accounts`);
  },
  createCheckingAccount(body: CheckingAccountCreation): Promise<CheckingAccount> {
    return request(`/checking-accounts`, json("POST", body));
  },
  deposit(id: number, body: MovementRequest): Promise<unknown> {
    return request(`/checking-accounts/${id}/deposit`, json("POST", body));
  },
  withdraw(id: number, body: MovementRequest): Promise<unknown> {
    return request(`/checking-accounts/${id}/withdraw`, json("POST", body));
  },
  fullWithdraw(id: number, body: MovementRequest): Promise<unknown> {
    return request(`/checking-accounts/${id}/full-withdraw`, json("POST", body));
  },
  consolidateCheckingAccount(id: number): Promise<void> {
    return request(`/checking-accounts/${id}/consolidate`, json("POST"));
  },
  checkingAccountPositions(id: number): Promise<Position[]> {
    return request(`/checking-accounts/${id}/positions`);
  },
  checkingAccountLastPosition(id: number): Promise<Position> {
    return request(`/checking-accounts/${id}/positions/last`);
  },

  // --- indexes ---
  listIndexes(): Promise<Index[]> {
    return request(`/indexes`);
  },
  indexValues(indexId: IndexId): Promise<IndexValue[]> {
    return request(`/indexes/${indexId.toLowerCase()}/values`);
  },
  hydrateIndex(indexId: IndexId): Promise<{ count: number }> {
    return request(`/indexes/${indexId.toLowerCase()}/values/hydrate`, json("POST"));
  },

  // --- consolidation ---
  scheduleConsolidations(): Promise<Record<string, unknown>> {
    return request(`/consolidations/schedule`, json("POST"));
  },

  // --- upload: POST the broker's raw .xlsx export ---
  uploadXlsx(
    broker: UploadBroker,
    product: UploadProduct,
    productId: number,
    file: Blob,
  ): Promise<unknown[]> {
    return request(`/upload/${broker}/${product}/${productId}`, {
      method: "POST",
      headers: { "Content-Type": XLSX_MIME },
      body: file,
    });
  },

  // --- health ---
  health(): Promise<unknown> {
    return request(`/health`);
  },

  // --- listed assets (stocks, FIIs, ETFs, BDRs) ---
  listListedAssets(): Promise<ListedAsset[]> {
    return request(`/listed-assets`);
  },
  createListedAsset(body: ListedAssetCreation): Promise<ListedAsset> {
    return request(`/listed-assets`, json("POST", body));
  },
  consolidateListedAsset(id: number): Promise<void> {
    return request(`/listed-assets/${id}/consolidate`, json("POST"));
  },
  listedAssetPositions(id: number): Promise<Position[]> {
    return request(`/listed-assets/${id}/positions`);
  },
  listedAssetLastPosition(id: number): Promise<Position> {
    return request(`/listed-assets/${id}/positions/last`);
  },
  listedAssetDividends(id: number): Promise<DividendDeclaration[]> {
    return request(`/listed-assets/${id}/dividends`);
  },

  // --- trades ---
  listTrades(assetId: number): Promise<Trade[]> {
    return request(`/listed-assets/${assetId}/trades`);
  },
  createTrade(assetId: number, body: TradeCreation): Promise<Trade> {
    return request(`/listed-assets/${assetId}/trades`, json("POST", body));
  },

  // --- corporate actions ---
  listCorporateActions(assetId: number): Promise<CorporateAction[]> {
    return request(`/listed-assets/${assetId}/corporate-actions`);
  },
  createSplit(assetId: number, body: SplitCreation): Promise<CorporateAction> {
    return request(`/listed-assets/${assetId}/corporate-actions/split`, json("POST", body));
  },
  createReverseSplit(assetId: number, body: ReverseSplitCreation): Promise<CorporateAction> {
    return request(`/listed-assets/${assetId}/corporate-actions/reverse-split`, json("POST", body));
  },
  createBonus(assetId: number, body: BonusCreation): Promise<CorporateAction> {
    return request(`/listed-assets/${assetId}/corporate-actions/bonus`, json("POST", body));
  },
  createTickerChange(assetId: number, body: TickerChangeCreation): Promise<CorporateAction> {
    return request(`/listed-assets/${assetId}/corporate-actions/ticker-change`, json("POST", body));
  },

  // --- ticker catalog (search-as-you-type source for asset registration) ---
  searchTickerCatalog(query: string): Promise<TickerCatalogEntry[]> {
    return request(`/ticker-catalog/search?q=${encodeURIComponent(query)}`);
  },

  // --- allocation ---
  allocationPlan(): Promise<AllocationPlan> {
    return request(`/allocation/plan`);
  },
  capitalSnapshots(): Promise<CapitalSnapshot[]> {
    return request(`/allocation/capital-snapshots`);
  },
  recordCapitalSnapshot(body: CapitalSnapshotCreation): Promise<CapitalSnapshot> {
    return request(`/allocation/capital-snapshots`, json("POST", body));
  },
  classTargets(): Promise<AssetClassTarget[]> {
    return request(`/allocation/class-targets`);
  },
  setClassTarget(body: AssetClassTargetCreation): Promise<AssetClassTarget> {
    return request(`/allocation/class-targets`, json("POST", body));
  },
  fixedIncomeSubClassTargets(): Promise<FixedIncomeSubClassTarget[]> {
    return request(`/allocation/fixed-income-subclass-targets`);
  },
  setFixedIncomeSubClassTarget(
    body: FixedIncomeSubClassTargetCreation,
  ): Promise<FixedIncomeSubClassTarget> {
    return request(`/allocation/fixed-income-subclass-targets`, json("POST", body));
  },
  classifications(): Promise<ProductClassification[]> {
    return request(`/allocation/classifications`);
  },
  classify(body: ProductClassification): Promise<ProductClassification> {
    return request(`/allocation/classifications`, json("POST", body));
  },

  // --- strategies ---
  listStrategies(): Promise<Strategy[]> {
    return request(`/strategies`);
  },
  createStrategy(body: StrategyCreation): Promise<Strategy> {
    return request(`/strategies`, json("POST", body));
  },
  listStrategyEditions(strategyId: number): Promise<StrategyEditionWithDiff[]> {
    return request(`/strategies/${strategyId}/editions`);
  },
  uploadStrategyReport(strategyId: number, file: Blob): Promise<unknown> {
    return request(`/strategies/${strategyId}/reports`, {
      method: "POST",
      headers: { "Content-Type": PDF_MIME },
      body: file,
    });
  },

  // --- attribution ---
  attributionSummary(assetId: number): Promise<AttributionSummary> {
    return request(`/listed-assets/${assetId}/attribution`);
  },
  recordAttributionMovement(
    assetId: number,
    body: AttributionMovementCreation,
  ): Promise<unknown> {
    return request(`/listed-assets/${assetId}/attribution/movements`, json("POST", body));
  },
};
