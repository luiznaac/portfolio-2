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
