// Hand-maintained mirror of the backend's edge DTOs — see AGENTS.md and salgadinhos'
// `api-contract` skill. Any DTO change on the backend must update this file in the same commit.

export interface HealthCheckResult {
  name: string;
  is_healthy: boolean;
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
