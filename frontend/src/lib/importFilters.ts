// The Importações filter (#367/#385): arquivo + fonte + estado, declared once so the inbox and the
// history render the same bar and narrow the same way. Fonte and estado are the server's additive
// `GET /imports` parameters; the file name has no server parameter, so the client narrows it over
// the rows the server returned.

import type { ImportFilters } from "../api/client.ts";
import type { Import, ImportSource, ImportStatus } from "../api/types.ts";
import { type FilterField, type FilterValues, applyFilters, matchesText } from "./filters.ts";
import {
  IMPORT_SOURCES,
  IMPORT_STATUSES,
  importSourceLabel,
  importStatusLabel,
} from "./imports.ts";

export const IMPORT_FILTER_FIELDS: FilterField<Import>[] = [
  {
    control: { kind: "text", key: "file", label: "Arquivo", placeholder: "Arquivo" },
    match: (row, value) => matchesText(value, row.file_name),
  },
  {
    control: {
      kind: "select",
      key: "source",
      label: "Fonte",
      emptyLabel: "Todas",
      options: IMPORT_SOURCES.map((source) => ({
        value: source,
        label: importSourceLabel(source),
      })),
    },
    match: (row, value) => row.source === value,
  },
  {
    control: {
      kind: "select",
      key: "status",
      label: "Estado",
      emptyLabel: "Todos",
      options: IMPORT_STATUSES.map((status) => ({
        value: status,
        label: importStatusLabel(status),
      })),
    },
    match: (row, value) => row.status === value,
  },
];

// The server arguments for one bar state: only fonte and estado travel as query parameters, and a
// blank control is no narrowing rather than an empty filter.
export function serverImportFilters(values: FilterValues): ImportFilters {
  const filters: ImportFilters = {};
  const source = (values.source ?? "").trim();
  const status = (values.status ?? "").trim();
  if (source !== "") filters.source = source as ImportSource;
  if (status !== "") filters.status = status as ImportStatus;
  return filters;
}

export function filterImports(rows: readonly Import[], values: FilterValues): Import[] {
  return applyFilters(rows, values, IMPORT_FILTER_FIELDS);
}
