import { type FilterField, type FilterValues, applyFilters, matchesText } from "./filters.ts";
import type { PositionRow } from "./position.ts";

// The class option that is not an AssetClass: every held ticker the catalog has not classified.
export const UNCLASSIFIED_CLASS_FILTER = "sem-classe";

// Ativos · Posições' filter (#378): ticker and class, declared once so the shared bar and the table
// narrowing read the same fields. The class options are the two listed classes plus the
// classification residue the page can fix.
export const POSITION_FILTER_FIELDS: FilterField<PositionRow>[] = [
  {
    control: { kind: "text", key: "ticker", label: "Ticker", placeholder: "Ticker" },
    match: (row, value) => matchesText(value, row.ticker),
  },
  {
    control: {
      kind: "select",
      key: "class",
      label: "Classe",
      emptyLabel: "Todas as classes",
      options: [
        { value: "ACOES", label: "Ações" },
        { value: "FIIS", label: "FIIs" },
        { value: "RENDA_FIXA", label: "Renda fixa" },
        { value: UNCLASSIFIED_CLASS_FILTER, label: "Sem classe" },
      ],
    },
    match: (row, value) =>
      value === UNCLASSIFIED_CLASS_FILTER ? row.assetClass === null : row.assetClass === value,
  },
];

export function filterPositions(rows: readonly PositionRow[], values: FilterValues): PositionRow[] {
  return applyFilters(rows, values, POSITION_FILTER_FIELDS);
}
