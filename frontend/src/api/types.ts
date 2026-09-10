// Hand-maintained mirror of the DTOs the controllers in
// backend/http-api/.../controller/ serialize. Jackson is configured in
// backend/usecase/.../configuration/JsonMapper.kt as:
//   - PropertyNamingStrategies.SNAKE_CASE   (so `maturityDate` -> `maturity_date`)
//   - JsonInclude.NON_NULL                  (nullable fields are omitted, not `null`)
//   - dates as ISO strings, never timestamps
//
// Money & rates are `BigDecimal` on the backend and serialize as JSON numbers.
// They are `number` here for DISPLAY ONLY — never do money arithmetic in the
// frontend; ask the backend to consolidate instead.
//
// The `Bond` sealed class has NO Jackson type discriminator: a fixed-rate bond
// and a floating-rate bond differ only by the presence of `index_id`.

export type IndexId = "IPCA" | "CDI" | "SELIC";

export type BondOrderType =
  | "BUY"
  | "SELL"
  | "FULL_REDEMPTION"
  | "MATURITY"
  | "DEPOSIT"
  | "WITHDRAWAL"
  | "FULL_WITHDRAWAL";

// --- indexes ---

export interface Index {
  id: IndexId;
}

export interface IndexValue {
  date: string; // ISO date
  value: number; // daily factor, percent
}

// --- bonds ---

export interface Bond {
  id: number;
  name: string;
  value: number; // contracted rate
  maturity_date: string; // ISO date
  index_id?: IndexId; // present => floating-rate bond
}

export interface FixedRateBondCreation {
  name: string;
  value: number;
  maturity_date: string;
}

export interface FloatingRateBondCreation extends FixedRateBondCreation {
  index_id: IndexId;
}

export interface BondOrderCreation {
  bond_id?: number;
  type: BondOrderType;
  date: string; // ISO date
  amount?: number;
  checking_account_id?: number;
}

// POST /bonds/orders echoes back a `BondOrder` (sealed, no discriminator). The
// frontend only needs to know the write succeeded, so this stays permissive.
export interface BondOrder {
  id: number;
  date: string;
  amount?: number;
  bond?: Bond;
  checking_account_id?: number;
}

// --- checking accounts ---

export interface CheckingAccount {
  id: number;
  name: string;
  value: number;
  index_id: IndexId;
  maturity_duration: string; // ISO-8601 period, e.g. "P2Y"
}

export interface CheckingAccountCreation {
  name: string;
  value: number;
  index_id: IndexId;
  maturity_duration: string; // ISO-8601 period
}

// body of POST /checking-accounts/{id}/{deposit|withdraw|full-withdraw}
export interface MovementRequest {
  date: string; // ISO date
  amount?: number; // omitted for full-withdraw
}

// --- positions (shared shape for BondPosition & CheckingAccountPosition) ---

export interface Position {
  date: string; // ISO date
  principal: number;
  yield: number;
  taxes: number;
}

// --- upload ---

export type UploadBroker = "kinvo" | "picpay";
export type UploadProduct = "bond" | "checking-account";

// --- listed assets (stocks, FIIs, ETFs, BDRs) ---
//
// AssetKind is a property of the paper (how it trades on B3), separate from any allocation
// class (a user policy like "Ações" vs "Real State" in the CARTEIRA IDEAL spreadsheet) — the
// backend intentionally doesn't conflate the two. See usecase/listedasset/model/AssetKind.kt.

export type AssetKind = "STOCK" | "FII" | "ETF" | "BDR";

export interface ListedAsset {
  id: number;
  ticker: string;
  kind: AssetKind;
  name: string;
  b3_identifier: string; // trading name (stocks) or fund identifier without "11" (FIIs)
}

export interface ListedAssetCreation {
  ticker: string;
  kind: AssetKind;
  name: string;
  b3_identifier: string;
}

// A row of B3's ticker universe (stocks/FIIs/ETFs/BDRs), synced from brapi — powers the
// search-as-you-type box on the asset registration screen so the user picks a ticker instead of
// typing its company name and kind by hand.
export interface TickerCatalogEntry {
  ticker: string;
  name: string;
  kind: AssetKind;
}

// --- trades ---
//
// Which side a trade is, is carried by `side`, never by the sign of `quantity` — the domain
// models Trade as a Buy/Sell sealed hierarchy, so quantity is always positive.

export type TradeSide = "BUY" | "SELL";

export interface Trade {
  id: number;
  asset_id: number;
  date: string; // ISO date
  side: TradeSide;
  quantity: number; // always positive
  price: number;
}

export interface TradeCreation {
  date: string; // ISO date
  side: TradeSide;
  quantity: number;
  price: number;
}

// --- corporate actions ---
//
// The domain model (CorporateAction) is sealed with no discriminator on the wire — same
// "permissive GET response" pattern as BondOrder. Creation is one flat interface per type,
// posted to its own route (POST .../corporate-actions/{split|reverse-split|bonus|ticker-change}).

export type CorporateActionType = "SPLIT" | "REVERSE_SPLIT" | "BONUS" | "TICKER_CHANGE";

export interface CorporateAction {
  id: number;
  asset_id: number;
  date: string;
  ratio?: number;
  value_per_new_share?: number;
  new_ticker?: string;
}

export interface SplitCreation {
  date: string;
  ratio: number;
}

export interface ReverseSplitCreation {
  date: string;
  ratio: number;
}

export interface BonusCreation {
  date: string;
  ratio: number;
  value_per_new_share: number;
}

export interface TickerChangeCreation {
  date: string;
  new_ticker: string;
}

// --- dividends (declared gross per share, from B3) ---

export type DividendType = "DIVIDEND" | "JCP" | "FUND_INCOME";

export interface DividendDeclaration {
  type: DividendType;
  value_per_share: number;
  ex_date: string;
  payment_date?: string;
}

// --- allocation (the rebalancing engine) ---
//
// AssetClass is user policy, not a product property — distinct from AssetKind (STOCK/FII/ETF/BDR)
// or the product_type used for classification overrides below. See usecase/allocation/model/AssetClass.kt.

export type AssetClass = "STOCKS" | "REAL_ESTATE" | "FIXED_INCOME" | "ALTERNATIVES" | "CASH";
export type FixedIncomeSubClass = "FLOATING_RATE" | "FIXED_RATE" | "INFLATION_LINKED";
export type ProductType = "BOND" | "CHECKING_ACCOUNT" | "LISTED_ASSET";

export interface CapitalSnapshot {
  id: number;
  date: string; // ISO date
  external_balance: number;
  planned_contribution: number;
}

export interface CapitalSnapshotCreation {
  date: string;
  external_balance: number;
  planned_contribution: number;
}

// Versioned by effective_from — changing a target is a dated fact, never an overwrite.
export interface AssetClassTarget {
  id: number;
  asset_class: AssetClass;
  weight: number; // fraction, e.g. 0.15 for 15%
  effective_from: string;
}

export interface AssetClassTargetCreation {
  asset_class: AssetClass;
  weight: number;
  effective_from: string;
}

export interface FixedIncomeSubClassTarget {
  id: number;
  sub_class: FixedIncomeSubClass;
  weight: number; // fraction of the FIXED_INCOME bucket, not of total capital
  effective_from: string;
}

export interface FixedIncomeSubClassTargetCreation {
  sub_class: FixedIncomeSubClass;
  weight: number;
  effective_from: string;
}

// Override map: a product with no entry here uses the backend's default for its ProductType
// (BOND/CHECKING_ACCOUNT -> FIXED_INCOME, STOCK/ETF/BDR -> STOCKS, FII -> REAL_ESTATE).
export interface ProductClassification {
  product_type: ProductType;
  product_id: number;
  asset_class: AssetClass;
}

export interface SubClassNode {
  sub_class: FixedIncomeSubClass;
  ideal_weight: number;
  ideal: number;
  current: number;
  delta: number; // current - ideal
}

export interface ClassNode {
  asset_class: AssetClass;
  ideal_weight: number;
  ideal: number;
  current: number;
  delta: number;
  sub_classes: SubClassNode[]; // only populated for FIXED_INCOME
}

// GET /allocation/plan — Capital -> Classe -> Sub-classe (Renda Fixa only, for now). Ticker-level
// detail needs per-strategy targets, which the order engine resolves.
export interface AllocationPlan {
  capital: number;
  classes: ClassNode[];
}

// --- strategies (the broker's model portfolios) ---
//
// assetClass says which class's ideal capital this strategy draws from (see StrategyWeight) — a
// strategy belongs to exactly one class. Per-ticker weights (StrategyEdition/StrategyTarget,
// parsed from broker model-portfolio PDFs) arrive via the strategy-edition endpoints below.

export interface Strategy {
  id: number;
  name: string;
  asset_class: AssetClass;
}

export interface StrategyCreation {
  name: string;
  asset_class: AssetClass;
}

// The strategy's own share of its AssetClass's ideal capital (e.g. within STOCKS, Top=40%,
// Dividendos=30%) — versioned by effective_from, same convention as AssetClassTarget.
export interface StrategyWeight {
  id: number;
  strategy_id: number;
  weight: number;
  effective_from: string;
}

export interface StrategyWeightCreation {
  weight: number;
  effective_from: string;
}

// --- attribution (splitting custody across strategies — decided by the user, never derived) ---

export type AttributionReason = "BUY" | "SELL" | "TRANSFER" | "ADJUSTMENT";

export interface AttributionMovementCreation {
  strategy_id: number;
  date: string;
  quantity: number; // signed, same convention as Trade
  reason: AttributionReason;
  note?: string;
}

export interface StrategyBalance {
  strategy_id: number;
  strategy_name: string;
  quantity: number;
}

// GET /listed-assets/{id}/attribution — custody (fiscal truth) vs. the sum of what's been
// attributed to strategies; unattributed_quantity is the explicit "not yet decided" bucket.
export interface AttributionSummary {
  custody_quantity: number;
  balances: StrategyBalance[];
  attributed_quantity: number;
  unattributed_quantity: number;
}

// --- strategy editions (ingesting broker model-portfolio PDFs) ---

export interface StrategyTarget {
  ticker: string;
  weight: number; // fraction, e.g. 0.05 for 5%
  rating?: string; // stock reports only (COMPRA/NEUTRO/VENDA) — FII reports have no equivalent
  target_price?: number;
}

// One imported report, immutable — a corrected report is a new edition, never an overwrite.
export interface StrategyEdition {
  id: number;
  strategy_id: number;
  reference_date: string; // the report's competência, normalized to the 1st of the month
  changes_text?: string; // the broker's "Estamos adicionando/removendo..." paragraph, verbatim
  targets: StrategyTarget[];
}

export interface StrategyTargetChange {
  ticker: string;
  before: StrategyTarget;
  after: StrategyTarget;
}

export interface StrategyTargetDiff {
  entered: StrategyTarget[];
  exited: StrategyTarget[]; // keeps the weight it had before leaving, not a "current" weight
  changed: StrategyTargetChange[];
}

// GET /strategies/{id}/editions — diff is omitted (not null) for a strategy's first edition,
// which has no prior edition to compare against.
export interface StrategyEditionWithDiff {
  edition: StrategyEdition;
  diff?: StrategyTargetDiff;
}

// --- orders (grouped orders + the monthly sale-exemption ceiling) ---

export type OrderKind = "BUY" | "SELL" | "EXIT" | "NEW_ENTRY";

export interface StrategyDelta {
  strategy_id: number;
  strategy_name: string;
  // current (attributed) - ideal, in shares. Positive = holding more than it should; negative = less.
  delta: number;
}

// One ticker's net order — already the residual after transfer_suggestions are applied, so
// quantity is the smallest trade that actually needs to happen.
export interface Order {
  listed_asset_id: number;
  ticker: string;
  is_fii: boolean;
  kind: OrderKind;
  quantity: number;
  notional: number;
  contributions: StrategyDelta[];
  // A trade already exists today for this ticker in the opposite direction — this order would be
  // a day trade (loses the sale exemption, taxed at 20% instead). Flagged, never blocked.
  day_trade_risk: boolean;
}

// Moving custody attribution between two strategies for the same ticker costs nothing — no
// brokerage, no tax, doesn't touch the sale-exemption ceiling — versus selling from one strategy
// and buying back for the other. Has a real lifecycle scoped to one month (competência):
// PENDING -> APPLIED or REJECTED. A rejection only holds for that month — next month the
// engine proposes fresh if the situation still calls for it. Approving and applying are the same
// action here (POST /orders/transfers/{id}/approve) — there's no separate execution step for a
// transfer the way there is for a real trade.
export type TransferProposalStatus = "PENDING" | "APPLIED" | "REJECTED";

export interface TransferProposal {
  id: number;
  month: string;
  listed_asset_id: number;
  ticker: string;
  from_strategy_id: number;
  from_strategy_name: string;
  to_strategy_id: number;
  to_strategy_name: string;
  proposed_quantity: number;
  applied_quantity: number | null;
  status: TransferProposalStatus;
  decided_at: string | null;
}

export interface ApproveTransferRequest {
  quantity?: number;
}

export interface TransferSettings {
  auto_approval_threshold: number;
}

// Stock sales (never FIIs — always taxed at 20%, no exemption) up to R$20,000/month are exempt
// from capital-gains tax; the ceiling is on the amount *sold*, not the gain. month_sold includes
// both already-executed trades this month and this plan's own pending SELL/EXIT orders.
export interface SaleCeiling {
  month_sold: number;
  limit: number;
  remaining: number;
  exceeded: boolean;
}

export interface OrderPlan {
  orders: Order[];
  transfer_proposals: TransferProposal[];
  sale_ceiling: SaleCeiling;
}

// --- brokerage note import ---

// One parsed statement row, staged for review — nothing is persisted until POST
// /notes/import/confirm. quantity is positive and `side` says the direction, like Trade.
// listed_asset_id is absent when the ticker couldn't be resolved to a registered asset.
export interface ImportedTrade {
  ticker: string;
  listed_asset_id?: number;
  date: string;
  side: TradeSide;
  quantity: number;
  price: number;
  notional: number;
  resolvable: boolean;
  matches_plan: boolean;
}

export interface ImportPreview {
  trades: ImportedTrade[];
  unresolved_tickers: string[];
}

export interface ImportedTradeConfirmation {
  ticker: string;
  date: string;
  side: TradeSide;
  quantity: number;
  price: number;
}

// --- tax: capital gains + step-up ---

// One (month, asset group) bucket. is_fii mirrors Order.is_fii — FIIs have no sale exemption and
// are always taxed at 20%; stocks/ETFs/BDRs share the R$20,000/month exemption at 15%.
export interface MonthlyCapitalGain {
  month: string;
  is_fii: boolean;
  proceeds: number;
  gross_gain: number;
  exempt: boolean;
  loss_compensated: number;
  taxable_gain: number;
  tax_due: number;
  loss_carried_forward: number;
}

export interface StepUpSuggestion {
  listed_asset_id: number;
  ticker: string;
  quantity: number;
  notional: number;
  realized_gain: number;
  rebuy_date: string;
}

export interface StepUpPlan {
  suggestions: StepUpSuggestion[];
  total_realized_gain: number;
  remaining_ceiling_after: number;
}

// --- income + monthly close ---

// A DividendDeclaration turned into money for the position actually held on the ex-date. This is
// the expected amount, not the received one — see IncomeReconciliation for the two compared.
export interface IncomeEvent {
  listed_asset_id: number;
  ticker: string;
  type: DividendType;
  ex_date: string;
  payment_date: string | null;
  quantity_held: number;
  gross_amount: number;
  retained_tax: number;
  net_amount: number;
}

export interface AssetIncomeSummary {
  listed_asset_id: number;
  ticker: string;
  total_net: number;
  cost_basis: number;
  yield_on_cost: number;
}

export interface IncomeReconciliation {
  ticker: string;
  month: string;
  type: DividendType;
  expected: number;
  received: number;
}

export type MonthlyCloseStatus = "OPEN" | "CLOSED";

export interface MonthlyClose {
  id: number;
  month: string;
  status: MonthlyCloseStatus;
  closed_at: string | null;
}

export interface DriftAlert {
  asset_class: AssetClass;
  ideal_weight: number;
  current_weight: number;
  drift_pp: number;
}
