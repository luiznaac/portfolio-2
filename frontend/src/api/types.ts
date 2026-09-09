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

export interface Trade {
  id: number;
  asset_id: number;
  date: string; // ISO date
  quantity: number; // positive = buy, negative = sell
  price: number;
}

export interface TradeCreation {
  date: string; // ISO date
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

// --- dividends (declared gross per share, from B3 — see the plan's "de onde vêm os dados") ---

export type DividendType = "DIVIDENDO" | "JCP" | "RENDIMENTO";

export interface DividendDeclaration {
  type: DividendType;
  value_per_share: number;
  ex_date: string;
  payment_date?: string;
}

// --- allocation (Fase 1: the rebalancing engine) ---
//
// AssetClass is user policy, not a product property — distinct from AssetKind (STOCK/FII/ETF/BDR)
// or the product_type used for classification overrides below. See usecase/allocation/model/AssetClass.kt.

export type AssetClass = "ACOES" | "REAL_STATE" | "RENDA_FIXA" | "ALTERNATIVOS" | "CAIXA";
export type FixedIncomeSubClass = "POS_FIXADO" | "PRE_FIXADO" | "INFLACAO";
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
  weight: number; // fraction of the RENDA_FIXA bucket, not of total capital
  effective_from: string;
}

export interface FixedIncomeSubClassTargetCreation {
  sub_class: FixedIncomeSubClass;
  weight: number;
  effective_from: string;
}

// Override map: a product with no entry here uses the backend's default for its ProductType
// (BOND/CHECKING_ACCOUNT -> RENDA_FIXA, STOCK/ETF/BDR -> ACOES, FII -> REAL_STATE).
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
  sub_classes: SubClassNode[]; // only populated for RENDA_FIXA until Fase 2
}

// GET /allocation/plan — Capital -> Classe -> Sub-classe (Renda Fixa only, for now). Ticker-level
// detail needs per-strategy targets from Fase 2.
export interface AllocationPlan {
  capital: number;
  classes: ClassNode[];
}

// --- strategies (the XP model portfolios) ---
//
// Minimal in Fase 1: registration only. Per-ticker weights (StrategyEdition/StrategyTarget,
// parsed from the XP PDFs) arrive in Fase 2.

export interface Strategy {
  id: number;
  name: string;
}

export interface StrategyCreation {
  name: string;
}

// --- attribution (splitting custody across strategies — decided by the user, never derived) ---

export type AttributionReason = "COMPRA" | "VENDA" | "TRANSFERENCIA" | "AJUSTE";

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

// --- strategy editions (Fase 2: ingesting the XP model-portfolio PDFs) ---

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
  changes_text?: string; // the XP "Estamos adicionando/removendo..." paragraph, verbatim
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
