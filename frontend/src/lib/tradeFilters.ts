import type { TradeSide } from "../api/types.ts";
import type { FilterBarField, FilterValues } from "./filters.ts";
import { formatTradeSide } from "./format.ts";

// The Ativos · Negócios filter (#367): the controls are declared once here and fed to the shared bar.
// The ledger is narrowed by the server through GET /trades/ledger, so these are control-only fields
// and `tradeLedgerQuery` turns the page's values into the request.
export const TRADE_FILTER_FIELDS: FilterBarField[] = [
  {
    control: { kind: "text", key: "ticker", label: "Ticker", placeholder: "Ticker" },
  },
  {
    control: { kind: "date", key: "from", label: "De" },
  },
  {
    control: { kind: "date", key: "to", label: "Até" },
  },
  {
    control: {
      kind: "select",
      key: "side",
      label: "Lado",
      emptyLabel: "Todos os lados",
      options: (["BUY", "SELL"] as const).map((side) => ({
        value: side,
        label: formatTradeSide(side),
      })),
    },
  },
];

export interface TradeLedgerQuery {
  ticker?: string;
  from?: string;
  to?: string;
  side?: TradeSide;
}

export interface TradeLedgerRequest extends TradeLedgerQuery {
  cursor?: string;
}

// The request's query-string keys, drawn from the declared controls plus the cursor; the client
// reads them from here so adding a filter field edits the field list rather than a second list.
export const TRADE_LEDGER_PARAM_KEYS: readonly (keyof TradeLedgerRequest)[] = [
  ...TRADE_FILTER_FIELDS.map((field) => field.control.key as keyof TradeLedgerQuery),
  "cursor",
];

// The page's filter values as the ledger request: blank controls are omitted, so clearing a field
// widens the read instead of sending an empty predicate.
export function tradeLedgerQuery(values: FilterValues): TradeLedgerQuery {
  const ticker = values.ticker?.trim() ?? "";
  const side = values.side ?? "";
  return {
    ticker: ticker.length > 0 ? ticker : undefined,
    from: values.from?.trim() || undefined,
    to: values.to?.trim() || undefined,
    side: side === "BUY" || side === "SELL" ? side : undefined,
  };
}
