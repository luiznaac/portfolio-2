import { TRADE_LEDGER_PARAM_KEYS, type TradeLedgerRequest } from "../lib/tradeFilters.ts";
import type {
  Allocation,
  AssetClass,
  Attribution,
  AttributionSliceCreation,
  AttributionTransferCreation,
  BackfillReport,
  BenchmarkStatusReport,
  Bond,
  CatalogEntry,
  CheckingAccount,
  CheckingAccountCreation,
  ClassificationQueueItem,
  CorporateAction,
  CurrentMonth,
  Edition,
  EditionActivation,
  EditionImport,
  FixedRateBondCreation,
  FloatingRateBondCreation,
  Gap,
  GapPriceUpdate,
  HarvestOptOutUpdate,
  HarvestPairCreation,
  HarvestReport,
  HealthCheckResult,
  Import,
  ImportApplyResult,
  ImportDestinationObject,
  ImportRow,
  ImportSource,
  ImportSourceStatus,
  ImportStatus,
  Income,
  IncomeQuery,
  LegacyAttributionPreview,
  LegacyAttributionResult,
  Month,
  MonthCreation,
  MonthPerformance,
  MonthPeriodRange,
  MonthUpdate,
  MovementsImportResult,
  OpeningSeedCreation,
  OrderList,
  OrderMatchUpdate,
  OrderPlan,
  OrderPlanPhase,
  PerformanceSeries,
  PortfolioStatus,
  Position,
  PositionDetail,
  PositionReconciliation,
  Quote,
  Snapshot,
  StockTaxReport,
  Strategy,
  StrategyCreation,
  StrategyHoldings,
  SuggestedOrder,
  Targets,
  TickerBatchClassification,
  TickerClassification,
  TickerRefreshResult,
  Trade,
  TradeLedgerPage,
  TransferProposal,
} from "./types.ts";

const BASE = (import.meta.env.VITE_API_BASE ?? "/api").replace(/\/$/, "");

export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

async function unwrap<T>(res: Response): Promise<T> {
  if (!res.ok) {
    let detail = res.statusText;
    try {
      const body = await res.json();
      detail = body.detail ?? detail;
    } catch {
      /* non-JSON body */
    }
    throw new ApiError(res.status, detail);
  }
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

// Builds a path's query string from the values the caller actually set, so an untouched filter is
// absent from the URL and the default read keeps its bare path.
function withQuery(path: string, params: Record<string, string | undefined>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== "") search.set(key, value);
  }
  const query = search.toString();
  return query.length === 0 ? path : `${path}?${query}`;
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  return unwrap<T>(
    await fetch(`${BASE}${path}`, {
      ...init,
      headers: { "Content-Type": "application/json", ...init?.headers },
    }),
  );
}

// Multipart uploads must not set Content-Type: the browser writes the multipart boundary itself.
// The status travels beside the body for the one upload whose 201/200 distinction is part of its
// answer (the import wizard's resume check).
async function postForm<T>(path: string, form: FormData): Promise<{ body: T; status: number }> {
  const response = await fetch(`${BASE}${path}`, { method: "POST", body: form });
  return { body: await unwrap<T>(response), status: response.status };
}

async function upload<T>(path: string, form: FormData): Promise<T> {
  return (await postForm<T>(path, form)).body;
}

// One staged upload: `created` is the 201 the server answers for a new file, absent (false) when
// the hash already named an import and the server answered 200 with that import. The assistant
// needs the distinction to put a resume banner on an existing file and none on a fresh one.
export interface StagedImport {
  import: Import;
  created: boolean;
}

export interface ImportFilters {
  status?: ImportStatus;
  source?: ImportSource;
}

export const api = {
  getHealth(): Promise<HealthCheckResult[]> {
    return request("/health");
  },

  // The landing's single aggregate: the shell's attention counts, quote freshness and month status
  // all read this one route.
  getPortfolioStatus(): Promise<PortfolioStatus> {
    return request("/portfolio/status");
  },

  // The import log; the status and source filters are the server's additive query parameters.
  getImports(filters: ImportFilters = {}): Promise<Import[]> {
    const query = new URLSearchParams();
    if (filters.status !== undefined) query.set("status", filters.status);
    if (filters.source !== undefined) query.set("source", filters.source);
    const suffix = query.toString();
    return request(`/imports${suffix.length > 0 ? `?${suffix}` : ""}`);
  },

  getImport(importId: number): Promise<Import> {
    return request(`/imports/${importId}`);
  },

  // The per-source status read behind Importações · Fontes: one entry per source with attempts.
  getImportSources(): Promise<ImportSourceStatus[]> {
    return request("/imports/sources");
  },

  // The retained original, served as an attachment; a link/download target, not a JSON read.
  importOriginalUrl(importId: number): string {
    return `${BASE}/imports/${importId}/file`;
  },

  async stageImport(
    source: ImportSource,
    file: File,
    destination: ImportDestinationObject,
    destinationEntityId?: number,
  ): Promise<StagedImport> {
    const form = new FormData();
    form.append("source", source);
    form.append("destination_object", destination);
    if (destinationEntityId !== undefined) {
      form.append("destination_entity_id", String(destinationEntityId));
    }
    form.append("file", file);
    const { body, status } = await postForm<Import>("/imports", form);
    return { import: body, created: status === 201 };
  },

  getImportRows(importId: number): Promise<ImportRow[]> {
    return request(`/imports/${importId}/rows`);
  },

  reviewImport(importId: number): Promise<Import> {
    return request(`/imports/${importId}/review`, { method: "POST" });
  },

  applyImport(importId: number): Promise<ImportApplyResult> {
    return request(`/imports/${importId}/apply`, { method: "POST" });
  },

  getPositions(): Promise<Position[]> {
    return request("/positions");
  },

  getTrades(): Promise<Trade[]> {
    return request("/trades");
  },

  // The paged, filtered ledger. Blank filters are omitted so clearing a field widens the read.
  getTradeLedger(query: TradeLedgerRequest): Promise<TradeLedgerPage> {
    const params = new URLSearchParams();
    for (const key of TRADE_LEDGER_PARAM_KEYS) {
      const value = query[key];
      if (value !== undefined && value.length > 0) params.set(key, value);
    }
    const suffix = params.toString();
    return request(`/trades/ledger${suffix.length > 0 ? `?${suffix}` : ""}`);
  },

  getPosition(ticker: string): Promise<PositionDetail> {
    return request(`/positions/${encodeURIComponent(ticker)}`);
  },

  getAttribution(ticker: string): Promise<Attribution> {
    return request(`/positions/${encodeURIComponent(ticker)}/attribution`);
  },

  setAttribution(ticker: string, creation: AttributionSliceCreation): Promise<Attribution> {
    return request(`/positions/${encodeURIComponent(ticker)}/attribution`, {
      method: "POST",
      body: JSON.stringify(creation),
    });
  },

  removeAttribution(ticker: string, strategyId: number): Promise<Attribution> {
    return request(`/positions/${encodeURIComponent(ticker)}/attribution/${strategyId}`, {
      method: "DELETE",
    });
  },

  transferAttribution(ticker: string, creation: AttributionTransferCreation): Promise<Attribution> {
    return request(`/positions/${encodeURIComponent(ticker)}/attribution/transfer`, {
      method: "POST",
      body: JSON.stringify(creation),
    });
  },

  getLegacyAttributionPreview(): Promise<LegacyAttributionPreview> {
    return request("/positions/legacy-attribution/preview");
  },

  applyLegacyAttribution(): Promise<LegacyAttributionResult> {
    return request("/positions/legacy-attribution", { method: "POST" });
  },

  getTickerQuotes(ticker: string): Promise<Quote[]> {
    return request(`/tickers/${encodeURIComponent(ticker)}/quotes`);
  },

  getTicker(ticker: string): Promise<CatalogEntry> {
    return request(`/tickers/${encodeURIComponent(ticker)}`);
  },

  classifyTicker(ticker: string, assetClass: AssetClass): Promise<CatalogEntry> {
    return request(`/tickers/${encodeURIComponent(ticker)}/classification`, {
      method: "PUT",
      body: JSON.stringify({ asset_class: assetClass } satisfies TickerClassification),
    });
  },

  revertTickerClassification(ticker: string): Promise<CatalogEntry> {
    return request(`/tickers/${encodeURIComponent(ticker)}/classification`, { method: "DELETE" });
  },

  // The held-unclassified queue the bulk modal lists.
  getClassificationQueue(): Promise<ClassificationQueueItem[]> {
    return request("/tickers/unclassified");
  },

  // The batch write: only the chosen rows travel, and the response is every updated catalog row.
  classifyTickers(classifications: Record<string, AssetClass>): Promise<CatalogEntry[]> {
    return request("/tickers/classification", {
      method: "PUT",
      body: JSON.stringify({ classifications } satisfies TickerBatchClassification),
    });
  },

  refreshCatalog(): Promise<TickerRefreshResult> {
    return request("/tickers/refresh", { method: "POST" });
  },

  getBonds(): Promise<Bond[]> {
    return request("/fixed-income/bonds");
  },

  getCheckingAccounts(): Promise<CheckingAccount[]> {
    return request("/fixed-income/accounts");
  },

  createFixedRateBond(creation: FixedRateBondCreation): Promise<Bond> {
    return request("/fixed-income/bonds/fixed", {
      method: "POST",
      body: JSON.stringify(creation),
    });
  },

  createFloatingRateBond(creation: FloatingRateBondCreation): Promise<Bond> {
    return request("/fixed-income/bonds/floating", {
      method: "POST",
      body: JSON.stringify(creation),
    });
  },

  createCheckingAccount(creation: CheckingAccountCreation): Promise<CheckingAccount> {
    return request("/fixed-income/accounts", {
      method: "POST",
      body: JSON.stringify(creation),
    });
  },

  getCurrentMonth(): Promise<CurrentMonth> {
    return request("/month/current");
  },

  getMonthPeriodRange(): Promise<MonthPeriodRange> {
    return request("/month/period-range");
  },

  openMonth(creation: MonthCreation): Promise<CurrentMonth> {
    return request("/month", { method: "POST", body: JSON.stringify(creation) });
  },

  updateCurrentMonth(update: MonthUpdate): Promise<CurrentMonth> {
    return request("/month/current", { method: "POST", body: JSON.stringify(update) });
  },

  replaceCurrentTargets(targets: Targets): Promise<CurrentMonth> {
    return request("/month/current/targets", { method: "PUT", body: JSON.stringify(targets) });
  },

  getCurrentAllocation(): Promise<Allocation> {
    return request("/month/current/allocation");
  },

  closeCurrentMonth(): Promise<Snapshot> {
    return request("/month/current/close", { method: "POST" });
  },

  getClosedMonths(): Promise<Month[]> {
    return request("/months");
  },

  getMonthSnapshot(monthId: number): Promise<Snapshot> {
    return request(`/months/${monthId}`);
  },

  getMonthPerformance(monthId: number): Promise<MonthPerformance> {
    return request(`/months/${monthId}/performance`);
  },

  getPerformanceSeries(): Promise<PerformanceSeries> {
    return request("/performance/series");
  },

  getBenchmarkStatus(): Promise<BenchmarkStatusReport> {
    return request("/benchmarks");
  },

  refreshBenchmarks(): Promise<BenchmarkStatusReport> {
    return request("/benchmarks/refresh", { method: "POST" });
  },

  getStockTax(period: string): Promise<StockTaxReport> {
    return request(`/tax/${encodeURIComponent(period)}`);
  },

  getHarvest(): Promise<HarvestReport> {
    return request("/tax/harvest");
  },

  acceptHarvestPair(creation: HarvestPairCreation): Promise<HarvestReport> {
    return request("/tax/harvest", { method: "POST", body: JSON.stringify(creation) });
  },

  setHarvestOptOut(ticker: string, optedOut: boolean): Promise<HarvestReport> {
    const update: HarvestOptOutUpdate = { opted_out: optedOut };
    return request(`/tax/harvest/${encodeURIComponent(ticker)}/opt-out`, {
      method: "PUT",
      body: JSON.stringify(update),
    });
  },

  getOrders(): Promise<OrderList> {
    return request("/orders");
  },

  // The computed plan at the requested depth; the funding phase carries the unfunded/NEUTRO
  // arithmetic and the outside-strategy holdings (present from the candidates phase on).
  getOrderPlan(phase: OrderPlanPhase = "FULL"): Promise<OrderPlan> {
    return request(`/month/current/orders?phase=${phase.toLowerCase()}`);
  },

  refreshOrders(): Promise<OrderList> {
    return request("/orders/plan/refresh", { method: "POST" });
  },

  approveTransfer(id: number): Promise<TransferProposal> {
    return request(`/orders/transfers/${id}/approve`, { method: "POST" });
  },

  rejectTransfer(id: number): Promise<TransferProposal> {
    return request(`/orders/transfers/${id}/reject`, { method: "POST" });
  },

  overrideOrderMatch(orderId: number, update: OrderMatchUpdate): Promise<SuggestedOrder> {
    return request(`/orders/${orderId}/match`, { method: "PUT", body: JSON.stringify(update) });
  },

  getStrategies(): Promise<Strategy[]> {
    return request("/strategies");
  },

  createStrategy(creation: StrategyCreation): Promise<Strategy> {
    return request("/strategies", { method: "POST", body: JSON.stringify(creation) });
  },

  getStrategyHoldings(strategyId: number): Promise<StrategyHoldings> {
    return request(`/strategies/${strategyId}/holdings`);
  },

  getEditions(strategyId: number): Promise<Edition[]> {
    return request(`/strategies/${strategyId}/editions`);
  },

  importEdition(strategyId: number, file: File): Promise<EditionImport> {
    const form = new FormData();
    form.append("file", file);
    return upload(`/strategies/${strategyId}/editions/import`, form);
  },

  activateEdition(strategyId: number, activation: EditionActivation): Promise<Edition> {
    return request(`/strategies/${strategyId}/editions`, {
      method: "POST",
      body: JSON.stringify(activation),
    });
  },

  getIncome(query: IncomeQuery = {}): Promise<Income[]> {
    return request(withQuery("/income", { ...query }));
  },

  importIncome(file: File): Promise<Income[]> {
    const form = new FormData();
    form.append("file", file);
    return upload("/income/import", form);
  },

  getCorporateActions(): Promise<CorporateAction[]> {
    return request("/corporate-actions");
  },

  importMovements(file: File): Promise<MovementsImportResult> {
    const form = new FormData();
    form.append("file", file);
    return upload("/corporate-actions/import", form);
  },

  // Reconstruir histórico: the backend operations Ativos · Negócios owns. The backfill body is
  // optional — no file means "absorb the seeds only", a Posição export rides along as the custody
  // cross-check.
  getGaps(): Promise<Gap[]> {
    return request("/gaps");
  },

  priceGap(id: number, unitPrice: number): Promise<Gap> {
    const update: GapPriceUpdate = { unit_price: unitPrice };
    return request(`/gaps/${id}/price`, { method: "POST", body: JSON.stringify(update) });
  },

  seedOpenings(seeds: OpeningSeedCreation[]): Promise<Trade[]> {
    return request("/history/openings", { method: "POST", body: JSON.stringify(seeds) });
  },

  backfill(file?: File): Promise<BackfillReport> {
    if (file === undefined) return request("/history/backfill", { method: "POST" });
    const form = new FormData();
    form.append("file", file);
    return upload("/history/backfill", form);
  },

  // The read-only custody cross-check: the server compares the upload against the replayed ledger
  // and persists nothing.
  reconcileCustody(file: File): Promise<PositionReconciliation[]> {
    const form = new FormData();
    form.append("file", file);
    return upload("/positions/reconcile", form);
  },
};
