// The shared filter model behind every unbounded list (#367): one control shape, one pure matcher and
// the page-scoped state helpers. A page declares its fields once and reuses the same declaration to
// render the bar and to narrow its host table, so the label, the control and the matching rule can
// never drift apart. No React imports here — the hook that holds the state is `usePageFilters`.

export type FilterValues = Record<string, string>;

export interface FilterOption {
  value: string;
  label: string;
}

export type FilterControl =
  | { kind: "text"; key: string; label: string; placeholder?: string }
  | { kind: "date"; key: string; label: string }
  | { kind: "select"; key: string; label: string; emptyLabel: string; options: FilterOption[] };

export interface FilterField<Row> {
  control: FilterControl;
  match: (row: Row, value: string) => boolean;
}

// Case-insensitive substring match over any string value: the one text predicate a list needs.
export function matchesText(query: string, ...values: (string | null | undefined)[]): boolean {
  const needle = query.trim().toLowerCase();
  if (!needle) return true;
  return values.some((value) => (value ?? "").toLowerCase().includes(needle));
}

export function emptyFilterValues<Row>(fields: readonly FilterField<Row>[]): FilterValues {
  return Object.fromEntries(fields.map((field) => [field.control.key, ""]));
}

export function setFilterValue(values: FilterValues, key: string, value: string): FilterValues {
  return { ...values, [key]: value };
}

export function clearFilterValues(values: FilterValues): FilterValues {
  return Object.fromEntries(Object.keys(values).map((key) => [key, ""]));
}

// Blank after trimming is no narrowing, matching `matchesText`, so a whitespace-only field neither
// hides rows nor advertises a `limpar`.
export function hasActiveFilter(values: FilterValues): boolean {
  return Object.values(values).some((value) => value.trim() !== "");
}

// Every blank value is no narrowing, so a field whose value is empty never calls its matcher.
export function applyFilters<Row>(
  rows: readonly Row[],
  values: FilterValues,
  fields: readonly FilterField<Row>[],
): Row[] {
  return rows.filter((row) =>
    fields.every((field) => {
      const value = values[field.control.key] ?? "";
      return value === "" || field.match(row, value);
    }),
  );
}

export function filterCountLabel(shown: number, total: number): string {
  return `${shown} de ${total}`;
}
