import { useImports } from "../api/queries.ts";
import type { Import } from "../api/types.ts";
import { IMPORT_FILTER_FIELDS, filterImports, serverImportFilters } from "../lib/importFilters.ts";
import { newestFirst, parkedImports } from "../lib/imports.ts";
import { usePageFilters } from "./usePageFilters.ts";

// One import list read: the shared bar's page state, the server-narrowed rows, the file narrowing
// over them, and the full total the `n de m` count needs. The inbox scopes the list to parked
// sessions while the history keeps the whole log; both share the same bar descriptor and read, so
// fonte and estado always travel as the additive `GET /imports` parameters.
export type ImportLogScope = "parked" | "all";

export function useImportLog(scope: ImportLogScope) {
  const filters = usePageFilters(IMPORT_FILTER_FIELDS);
  const imports = useImports(serverImportFilters(filters.values));
  const all = useImports();

  const host = (records: Import[]) => (scope === "parked" ? parkedImports(records) : records);
  const rows = newestFirst(filterImports(host(imports.data ?? []), filters.values));
  const total = host(all.data ?? []).length;

  return { filters, rows, total, loading: imports.isLoading || all.isLoading };
}
