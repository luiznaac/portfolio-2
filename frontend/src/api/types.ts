// Hand-maintained mirror of the backend's edge DTOs — see AGENTS.md and salgadinhos'
// `api-contract` skill. Any DTO change on the backend must update this file in the same commit.

export interface HealthCheckResult {
  service_name: string;
  is_healthy: boolean;
  timestamp: string;
}

export type AssetKind = "STOCK" | "UNIT" | "FII" | "BDR" | "ETF" | "UNKNOWN";
export type AssetClass = "ACOES" | "FIIS" | "RENDA_FIXA";

export interface CatalogEntry {
  ticker: string;
  short_name: string;
  bdi_type: string;
  isin?: string;
  asset_kind: AssetKind;
  asset_class?: AssetClass;
  first_seen: string;
  last_seen: string;
}

export type QuoteSource = "COTAHIST";

export interface Quote {
  ticker: string;
  date: string;
  close: number;
  source: QuoteSource;
}

export interface Institution {
  id: number;
  name: string;
}

export interface InstitutionCreation {
  name: string;
}

export interface TickerRefreshResult {
  catalog: number;
  quotes: number;
}

export type TradeSide = "BUY" | "SELL";

export type ImportSource = "B3_TRADES" | "B3_POSITION" | "B3_MOVEMENTS" | "XP_REPORT";
export type ImportStatus = "STAGED" | "REVIEWED" | "APPLIED" | "FAILED";

export interface Import {
  id: number;
  source: ImportSource;
  file_name: string;
  file_hash: string;
  status: ImportStatus;
  created_at: string;
}

export interface ImportRow {
  id: number;
  import_id: number;
  date?: string;
  side: TradeSide;
  venue: string;
  ticker: string;
  quantity: number;
  unit_price: number;
  institution_name: string;
  error?: string;
}

export type TradeSource = "MANUAL" | "B3_IMPORT";

export interface Trade {
  id: number;
  ticker: string;
  date: string;
  venue: string;
  side: TradeSide;
  quantity: number;
  unit_price: number;
  institution_id: number;
  fee?: number;
  source: TradeSource;
  confirmed_by_b3: boolean;
  ordinal: number;
}

export interface TradeCreation {
  ticker: string;
  side: TradeSide;
  date: string;
  venue: string;
  quantity: number;
  unit_price: number;
  institution_id: number;
  fee?: number;
}

export interface Position {
  ticker: string;
  quantity: number;
  average_price: number;
}

export interface PositionDetail {
  position: Position;
  trades: Trade[];
}

export interface PositionReconciliation {
  ticker: string;
  custody_quantity: number;
  ledger_quantity: number;
  has_drift: boolean;
}

// The attribution ledger: slices of the position mapped to strategies, plus the computed
// remainder that belongs to no strategy. `quantity` on a slice is its current total, not a delta.
export interface AttributionSlice {
  strategy_id: number;
  strategy_name: string;
  quantity: number;
}

export interface Attribution {
  ticker: string;
  custody_quantity: number;
  attributed_quantity: number;
  unattributed_quantity: number;
  slices: AttributionSlice[];
}

export interface AttributionSliceCreation {
  strategy_id: number;
  quantity: number;
}

export type IndexId = "IPCA" | "CDI" | "SELIC";

// The target bucket a fixed-income product allocates to — derived by the backend from the rate kind
// and the index, never stored (see the backend's FixedIncomeSubClass).
export type FixedIncomeSubClass = "FIXED_RATE" | "FLOATING_RATE" | "INFLATION_LINKED";

export interface Bond {
  id: number;
  name: string;
  rate: number;
  maturity_date: string;
  index_id?: IndexId;
  sub_class: FixedIncomeSubClass;
}

export interface CheckingAccount {
  id: number;
  name: string;
  rate: number;
  index_id: IndexId;
  maturity_duration: string;
  sub_class: FixedIncomeSubClass;
}

export interface FixedRateBondCreation {
  name: string;
  rate: number;
  maturity_date: string;
}

export interface FloatingRateBondCreation {
  name: string;
  rate: number;
  maturity_date: string;
  index_id: IndexId;
}

export interface CheckingAccountCreation {
  name: string;
  rate: number;
  index_id: IndexId;
  maturity_duration: string;
}

export type MonthStatus = "OPEN" | "CLOSED";

export interface Month {
  id: number;
  period: string;
  capital: number;
  floor: number;
  status: MonthStatus;
}

// Target percentages are the percentage number — 50.00 is half (see the backend's Targets).
export interface ClassTarget {
  asset_class: AssetClass;
  percentage: number;
}

export interface StrategyTarget {
  asset_class: AssetClass;
  strategy: string;
  percentage: number;
}

export interface SubClassTarget {
  sub_class: FixedIncomeSubClass;
  percentage: number;
}

export interface TickerTarget {
  strategy: string;
  ticker: string;
  percentage: number;
}

export interface Targets {
  classes: ClassTarget[];
  strategies: StrategyTarget[];
  sub_classes: SubClassTarget[];
  tickers: TickerTarget[];
}

export interface CurrentMonth {
  month: Month;
  targets: Targets;
}

export interface MonthCreation {
  period: string;
  capital: number;
  floor: number;
}

export interface MonthUpdate {
  capital: number;
  floor: number;
}

// The computed allocation tree (GET /month/current/allocation). Ideal/Actual/Deficit are R$; Drift
// is in percentage points (Actual − Ideal over Capital) and `target` is the rung's percentage, absent
// when the month's chain declares none (the backend omits nulls).
export interface ClassAllocation {
  asset_class: AssetClass;
  target?: number;
  ideal: number;
  actual: number;
  deficit: number;
  drift: number;
  unattributed: number;
  strategies: StrategyAllocation[];
  sub_classes: SubClassAllocation[];
}

export interface StrategyAllocation {
  strategy: string;
  target: number;
  ideal: number;
  actual: number;
  deficit: number;
  drift: number;
  tickers: TickerAllocation[];
}

export interface SubClassAllocation {
  sub_class: FixedIncomeSubClass;
  target?: number;
  ideal: number;
  actual: number;
  deficit: number;
  drift: number;
}

export interface TickerAllocation {
  ticker: string;
  target: number;
  ideal: number;
  actual: number;
  deficit: number;
  drift: number;
}

export interface Allocation {
  period: string;
  capital: number;
  floor: number;
  ideal: number;
  actual: number;
  deficit: number;
  drift: number;
  unattributed: number;
  classes: ClassAllocation[];
}

// The order engine's read (GET /month/current/orders?phase=ideals|candidates). Ideal/amount are R$;
// `target_quantity` is the whole-unit floor of a ticker slice and is absent when no quote prices it.
// Candidates aggregate a ticker's target across strategies; a targeted surplus stays a hold.
export type OrderPlanPhase = "IDEALS" | "CANDIDATES";
export type OrderSide = "BUY" | "SELL" | "NEW_ENTRY" | "EXIT";

export interface TickerOrderPlan {
  ticker: string;
  target: number;
  ideal: number;
  price?: number;
  target_quantity?: number;
}

export interface StrategyOrderPlan {
  strategy: string;
  target: number;
  ideal: number;
  tickers: TickerOrderPlan[];
}

export interface SubClassOrderPlan {
  sub_class: FixedIncomeSubClass;
  target?: number;
  ideal: number;
}

// The class rung is a variant: equities carry strategies, fixed income carries sub-classes. The
// backend omits the list that does not apply rather than sending an always-empty one.
export interface EquitiesOrderPlan {
  asset_class: Exclude<AssetClass, "RENDA_FIXA">;
  target?: number;
  ideal: number;
  strategies: StrategyOrderPlan[];
}

export interface FixedIncomeOrderPlan {
  asset_class: Extract<AssetClass, "RENDA_FIXA">;
  target?: number;
  ideal: number;
  sub_classes: SubClassOrderPlan[];
}

export type ClassOrderPlan = EquitiesOrderPlan | FixedIncomeOrderPlan;

export interface OrderCandidate {
  ticker: string;
  side: OrderSide;
  quantity: number;
  price?: number;
  amount?: number;
  /** Ideal − position × price in R$; negative side is a surplus; absent without a quote. */
  deficit?: number;
  ideal: number;
  target_quantity: number;
  position_quantity: number;
}

export interface OrderPlan {
  period: string;
  phase: OrderPlanPhase;
  capital: number;
  floor: number;
  investable_capital: number;
  classes: ClassOrderPlan[];
  candidates: OrderCandidate[];
}

// A strategy report's analyst recommendation — the English key the backend serialises.
export type Rating = "BUY" | "NEUTRAL" | "SELL";

export type EditionStatus = "STAGED" | "ACTIVE";

export interface Strategy {
  id: number;
  name: string;
  asset_class: AssetClass;
  weight: number;
}

export interface StrategyCreation {
  name: string;
  asset_class: AssetClass;
  weight: number;
}

export interface EditionEntry {
  ticker: string;
  weight: number;
  rating?: Rating;
  target_price?: number;
  do_not_trade: boolean;
}

export interface DiffWeight {
  ticker: string;
  weight: number;
}

export interface DiffChange {
  ticker: string;
  weight_before: number;
  weight_after: number;
  rating_before?: Rating;
  rating_after?: Rating;
}

export interface EditionDiff {
  entered: DiffWeight[];
  exited: DiffWeight[];
  changed: DiffChange[];
}

export interface Edition {
  id: number;
  strategy_id: number;
  reference_month: string;
  status: EditionStatus;
  diff_from_previous?: EditionDiff;
  entries: EditionEntry[];
}

// The POST /strategies/{id}/editions/import response: the layout parser that read the report (the
// reference prototype's "Parser" column) beside the staged edition it produced.
export interface EditionImport {
  parser: string;
  edition: Edition;
}

export interface EditionActivation {
  reference_month: string;
  entries: EditionEntry[];
}

// Proventos (Income): a listed asset's cash payment and its lifecycle stage. `tax_nature` is what
// separates income from corporate-action cash; `provenance` is the record's origin. Amounts arrive
// exactly as the B3 export gives them — gross and net stored, retained derived as gross − net.
export type IncomeType = "DIVIDEND" | "JCP" | "FUND_INCOME";
export type IncomeStatus = "ANNOUNCED" | "PAYABLE" | "RECEIVED" | "RECONCILED";
export type TaxNature = "INCOME" | "COST_REDUCTION" | "RETURN_OF_CAPITAL" | "OTHER";
export type Provenance = "B3" | "XP" | "KINVO" | "MANUAL";

export interface Income {
  id: number;
  ticker: string;
  date: string;
  type: IncomeType;
  gross_amount: number;
  retained_amount: number;
  net_amount: number;
  tax_nature: TaxNature;
  provenance: Provenance;
  status: IncomeStatus;
}
