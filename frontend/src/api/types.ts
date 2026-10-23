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

// The open month's stock-sale tax read (GET /month/current/tax). The meter tracks the month's gross
// STOCK sales against the R$20k exemption; crossing it taxes the whole month's net gain at 15%.
// Average prices are the fiscal mean unit cost of every held ticker.
export interface ExemptionMeter {
  gross_sales: number;
  exemption_limit: number;
  exceeded: boolean;
  realized_gain: number;
  taxable_gain: number;
  rate: number;
  tax: number;
}

export interface TickerAveragePrice {
  ticker: string;
  quantity: number;
  average_price: number;
}

// The three tax species a listed-asset sale realizes into. A STOCK sale is SWING unless a
// same-day same-institution buy offsets it (DAY_TRADE); an FII sale is always FII.
export type RealizedKind = "SWING" | "DAY_TRADE" | "FII";

// One kind's realized month result: gains and losses are positive magnitudes, `result` is
// gains − losses.
export interface RealizedResult {
  kind: RealizedKind;
  gross_sales: number;
  gains: number;
  losses: number;
  result: number;
}

// One carry bucket's movement in the month: opening balance, loss banked this month, carry consumed
// by this month's taxable gains, and the balance carried into the next month.
export interface CarryBucket {
  kind: RealizedKind;
  opening_balance: number;
  loss_banked: number;
  loss_applied: number;
  closing_balance: number;
}

// The same-day both-sides guard: a warning only, never a day-trade tax computation.
export interface DayTradeWarning {
  ticker: string;
  institution_id: number;
  date: string;
  quantity: number;
}

export interface StockTaxReport {
  period: string;
  meter: ExemptionMeter;
  average_prices: TickerAveragePrice[];
  realized: RealizedResult[];
  carry: CarryBucket[];
  day_trades: DayTradeWarning[];
  /** The DARF 6015 deadline: the last business day of the following month. */
  darf_due_date: string;
}

// The harvest pass (GET /tax/harvest). A pair sells a losing STOCK position on `sell.date` and
// rebuys it on the next business day, sized to the remaining R$20k headroom. `headroom` is what the
// month's meter still allows; `minimum_loss` is the gate a pair must clear.
export interface HarvestLeg {
  date: string;
  quantity: number;
  price: number;
  amount: number;
}

export interface HarvestProposal {
  ticker: string;
  strategy_id?: number;
  strategy?: string;
  quantity: number;
  average_price: number;
  price: number;
  loss: number;
  neutro: boolean;
  sell: HarvestLeg;
  rebuy: HarvestLeg;
}

/** A loss-making position the owner opted out of harvesting — reported so the choice is reversible. */
export interface HarvestIgnored {
  ticker: string;
  strategy_id?: number;
  strategy?: string;
  quantity: number;
  loss: number;
  neutro: boolean;
}

/** FIIs are report-only: 20% of an unrealized gain, zero on a loss, never a proposed pair. */
export interface FiiHarvestReport {
  ticker: string;
  quantity: number;
  price: number;
  average_price: number;
  unrealized_result: number;
  estimated_tax: number;
}

export interface HarvestPair {
  id: number;
  ticker: string;
  strategy_id?: number;
  strategy?: string;
  loss: number;
  sell: HarvestLeg;
  rebuy: HarvestLeg;
}

/** The acceptance payload — both legs are editable before the pair is stored. */
export interface HarvestPairCreation {
  ticker: string;
  strategy_id?: number;
  sell_date: string;
  sell_quantity: number;
  sell_price: number;
  rebuy_date: string;
  rebuy_quantity: number;
  rebuy_price: number;
}

export interface HarvestOptOutUpdate {
  opted_out: boolean;
}

export interface HarvestReport {
  period: string;
  headroom: number;
  minimum_loss: number;
  suggestions: HarvestProposal[];
  ignored: HarvestIgnored[];
  fii_report: FiiHarvestReport[];
  pairs: HarvestPair[];
}

// The order engine's read (GET /month/current/orders?phase=ideals|candidates|funding|full). Ideal/amount
// are R$; `target_quantity` is the whole-unit floor of a ticker slice and is absent when no quote
// prices it. Candidates aggregate a ticker's target across strategies; a targeted surplus stays a
// hold unless the funding pass sells it to cover a buy. The full phase adds the pro-rata scaling, the
// attribution bias/transfers and the RF sub-class orders.
export type OrderPlanPhase = "IDEALS" | "CANDIDATES" | "FUNDING" | "FULL";
export type OrderSide = "BUY" | "SELL" | "NEW_ENTRY" | "EXIT";

/** Why a funding SELL exists, in the cascade's source order. */
export type FundingSource = "NEUTRO" | "OUTSIDE_STRATEGY" | "SURPLUS";

export interface TickerOrderPlan {
  ticker: string;
  /** The Strategy this slice belongs to — the Attribution ledger's key. */
  strategy: string;
  target: number;
  ideal: number;
  price?: number;
  target_quantity?: number;
  /** The Edition Entry's "não operar" flag for this (strategy, ticker) slice. */
  neutro: boolean;
  /** The stored Attribution slice quantity for this (strategy, ticker). */
  attributed_quantity: number;
  /** Ideal − attributed × price, capped by custody; absent when no quote prices it. */
  deficit?: number;
}

export interface StrategyOrderPlan {
  strategy: string;
  target: number;
  ideal: number;
  tickers: TickerOrderPlan[];
}

/** How an RF sub-class order moves money. */
export type FixedIncomeOrderSide = "CONTRIBUTE" | "REDEEM";

export interface FixedIncomeOrder {
  sub_class: FixedIncomeSubClass;
  side: FixedIncomeOrderSide;
  /** Positive R$; the side carries the direction. */
  amount: number;
}

export interface SubClassOrderPlan {
  sub_class: FixedIncomeSubClass;
  target?: number;
  ideal: number;
  /** Present from the full phase on. */
  actual?: number;
  deficit?: number;
  orders: FixedIncomeOrder[];
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
  /** Set on a SELL the funding pass emits; null on every other candidate. */
  funding_source?: FundingSource;
}

/** The funding pass's arithmetic; present from the funding phase on, restated in scaled terms at full. */
export interface FundingSummary {
  buy_amount: number;
  sell_amount: number;
  unfunded_amount: number;
  neutro_deficit: number;
}

/** The pro-rata pass's arithmetic at the full phase; factor 1 means every buy was funded. */
export interface ProRataSummary {
  original_buy_amount: number;
  factor: number;
  buy_amount: number;
  unfunded_amount: number;
}

/** A held ticker no Target names; held for attention, never auto-exited. */
export interface OutsideStrategyHolding {
  ticker: string;
  quantity: number;
  price?: number;
  amount?: number;
  /** The full-position manual EXIT default, floored to whole units. */
  suggested_exit_quantity: number;
}

export type AttributionSide = "ALLOCATE" | "DEALLOCATE";

/** A suggested change to the Attribution ledger caused by a buy or a sell. */
export interface AttributionMove {
  ticker: string;
  strategy: string;
  side: AttributionSide;
  quantity: number;
}

/** A bookkeeping move between two slices of the same ticker — no tax effect, no custody movement. */
export interface AttributionTransfer {
  ticker: string;
  from_strategy: string;
  to_strategy: string;
  quantity: number;
  price?: number;
  amount?: number;
}

export interface OrderPlan {
  period: string;
  phase: OrderPlanPhase;
  capital: number;
  floor: number;
  investable_capital: number;
  classes: ClassOrderPlan[];
  candidates: OrderCandidate[];
  funding?: FundingSummary;
  outside_strategy: OutsideStrategyHolding[];
  /** Present from the full phase on. */
  pro_rata?: ProRataSummary;
  attribution: AttributionMove[];
  transfers: AttributionTransfer[];
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

// Eventos corporativos: a holder-neutral event template plus the engine's decision. `ratio` keeps
// the event's own direction — DESDOBRAMENTO multiplies the quantity by it, GRUPAMENTO divides — and
// the amounts stay null on kinds that do not carry them. NEEDS_REVIEW is the review queue.
export type CorporateActionKind =
  | "SPLIT"
  | "REVERSE_SPLIT"
  | "BONUS_SHARES"
  | "TICKER_CHANGE"
  | "RIGHTS_ISSUE"
  | "RIGHTS_ASSIGNMENT"
  | "INCORPORATION"
  | "MERGER"
  | "SPIN_OFF"
  | "CAPITAL_RETURN"
  | "AMORTIZATION"
  | "CASH_BONUS"
  | "OTHER";
export type CorporateActionStatus = "APPLIED" | "NEEDS_REVIEW";
export type CorporateActionSource = "B3_MOVEMENTS";

export interface CorporateAction {
  id: number;
  ticker: string;
  kind: CorporateActionKind;
  ex_date: string;
  approval_date?: string;
  ratio?: number;
  declared_value_per_share?: number;
  target_ticker?: string;
  source: CorporateActionSource;
  process_number?: string;
  raw_type?: string;
  ordinal: number;
  status: CorporateActionStatus;
}

// The POST /corporate-actions/import response: how many rows each destination absorbed and the
// corporate-action rows the log now holds.
export interface MovementsImportResult {
  trades: number;
  incomes: number;
  corporate_actions: CorporateAction[];
}

// Ordens: the persisted month plan. A suggested order's lifecycle is the server's match against the
// month's trade ledger (suggested → executed → imported); the frontend only renders the state.
export type SuggestedOrderStatus = "SUGGESTED" | "EXECUTED" | "IMPORTED";
export type TransferStatus = "PENDING" | "APPROVED" | "REJECTED";

export interface SuggestedOrder {
  id: number;
  ticker: string;
  side: OrderSide;
  quantity: number;
  suggested_price?: number;
  executed_quantity: number;
  status: SuggestedOrderStatus;
  matched_trade_id?: number;
}

// An Attribution Transfer proposal: bookkeeping only, sized in whole units at the ticker's quote.
export interface TransferProposal {
  id: number;
  ticker: string;
  from_strategy_id: number;
  from_strategy: string;
  to_strategy_id: number;
  to_strategy: string;
  quantity: number;
  amount?: number;
  auto_approved: boolean;
  status: TransferStatus;
}

export interface UnplannedTrade {
  id: number;
  ticker: string;
  side: TradeSide;
  quantity: number;
}

export interface OrderList {
  period: string;
  orders: SuggestedOrder[];
  transfers: TransferProposal[];
  unplanned_trades: UnplannedTrade[];
}

export interface OrderMatchUpdate {
  trade_id?: number | null;
}

// The monthly close (POST /month/current/close): the immutable snapshot of the month the ritual
// locked. The holdings and attributions are frozen at close time, the class totals are the
// allocation's class rungs, and the tax block is the carry roll plus the exemption meter's finals.
export interface HoldingSnapshot {
  ticker: string;
  quantity: number;
  average_price: number;
}

export interface AttributionSnapshot {
  ticker: string;
  strategy_id: number;
  strategy: string;
  quantity: number;
}

export interface ClassTotalSnapshot {
  asset_class: AssetClass;
  target?: number;
  ideal: number;
  actual: number;
  deficit: number;
  drift: number;
  unattributed: number;
}

export interface IncomeTotals {
  gross: number;
  retained: number;
  net: number;
}

export interface OrdersSnapshot {
  orders: SuggestedOrder[];
  transfers: TransferProposal[];
  unplanned_trades: UnplannedTrade[];
}

export interface TaxCloseSnapshot {
  carry: CarryBucket[];
  meter: ExemptionMeter;
}

export interface CloseState {
  period: string;
  capital: number;
  floor: number;
  caixa: number;
  holdings: HoldingSnapshot[];
  attributions: AttributionSnapshot[];
  classes: ClassTotalSnapshot[];
  income: IncomeTotals;
  orders: OrdersSnapshot;
  tax: TaxCloseSnapshot;
}

export interface Snapshot {
  id: number;
  month_id: number;
  state: CloseState;
  created_at: string;
}

// The 409 body a blocked close returns: the canonical error fields plus the undecided proposals, so
// the caller can approve or reject them by id before retrying the close.
export interface PendingTransfer {
  id: number;
  ticker: string;
  from_strategy: string;
  to_strategy: string;
  quantity: number;
  amount?: number;
}

export interface PendingTransfersError {
  error: string;
  message: string;
  detail: string;
  pending_transfers: PendingTransfer[];
}
