// The Proventos ledger filter (#367): ticker plus lifecycle state, declared once so the bar and the
// host table share the same controls and matching rules.

import {
  type FilterField,
  type FilterValues,
  applyFilters,
  emptyFilterValues,
  matchesText,
} from "./filters.ts";
import { INCOME_STATUSES, type IncomeRow, incomeStatusLabel } from "./income.ts";

export const INCOME_FILTER_FIELDS: FilterField<IncomeRow>[] = [
  {
    control: { kind: "text", key: "ticker", label: "Ativo", placeholder: "Ticker" },
    match: (row, value) => matchesText(value, row.ticker),
  },
  {
    control: {
      kind: "select",
      key: "status",
      label: "Situação",
      emptyLabel: "Todas",
      options: INCOME_STATUSES.map((status) => ({
        value: status,
        label: incomeStatusLabel(status),
      })),
    },
    match: (row, value) => row.status === value,
  },
];

export const emptyIncomeFilters: FilterValues = emptyFilterValues(INCOME_FILTER_FIELDS);

export function filterIncome(rows: IncomeRow[], values: FilterValues): IncomeRow[] {
  return applyFilters(rows, values, INCOME_FILTER_FIELDS);
}
