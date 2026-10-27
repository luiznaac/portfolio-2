import type {
  Allocation,
  Attribution,
  AttributionSliceCreation,
  Bond,
  CheckingAccount,
  CheckingAccountCreation,
  CorporateAction,
  CurrentMonth,
  Edition,
  EditionActivation,
  EditionImport,
  FixedRateBondCreation,
  FloatingRateBondCreation,
  HarvestOptOutUpdate,
  HarvestPairCreation,
  HarvestReport,
  HealthCheckResult,
  Import,
  ImportRow,
  ImportSource,
  Income,
  Month,
  MonthCreation,
  MonthPerformance,
  MonthUpdate,
  MovementsImportResult,
  OrderList,
  OrderMatchUpdate,
  OrderPlan,
  OrderPlanPhase,
  PerformanceSeries,
  PositionDetail,
  Quote,
  Snapshot,
  StockTaxReport,
  Strategy,
  StrategyCreation,
  StrategyHoldings,
  SuggestedOrder,
  Targets,
  Trade,
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

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  return unwrap<T>(
    await fetch(`${BASE}${path}`, {
      ...init,
      headers: { "Content-Type": "application/json", ...init?.headers },
    }),
  );
}

// Multipart uploads must not set Content-Type: the browser writes the multipart boundary itself.
async function upload<T>(path: string, form: FormData): Promise<T> {
  return unwrap<T>(await fetch(`${BASE}${path}`, { method: "POST", body: form }));
}

export const api = {
  getHealth(): Promise<HealthCheckResult[]> {
    return request("/health");
  },

  stageImport(source: ImportSource, file: File): Promise<Import> {
    const form = new FormData();
    form.append("source", source);
    form.append("file", file);
    return upload("/imports", form);
  },

  getImportRows(importId: number): Promise<ImportRow[]> {
    return request(`/imports/${importId}/rows`);
  },

  reviewImport(importId: number): Promise<Import> {
    return request(`/imports/${importId}/review`, { method: "POST" });
  },

  applyImport(importId: number): Promise<Import> {
    return request(`/imports/${importId}/apply`, { method: "POST" });
  },

  getTrades(): Promise<Trade[]> {
    return request("/trades");
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

  getTickerQuotes(ticker: string): Promise<Quote[]> {
    return request(`/tickers/${encodeURIComponent(ticker)}/quotes`);
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

  getStockTax(): Promise<StockTaxReport> {
    return request("/month/current/tax");
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

  getIncome(): Promise<Income[]> {
    return request("/income");
  },

  getUpcomingIncome(): Promise<Income[]> {
    return request("/income/upcoming");
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
};
