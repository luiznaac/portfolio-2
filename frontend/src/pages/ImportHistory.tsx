import { FilterBar } from "../components/FilterBar.tsx";
import { Panel } from "../components/Panel.tsx";
import { Table } from "../components/Table.tsx";
import { IMPORT_LOG_COLUMNS } from "../components/importLogColumns.tsx";
import { useImportLog } from "../hooks/useImportLog.ts";
import { IMPORT_FILTER_FIELDS } from "../lib/importFilters.ts";

// Importações · Histórico (#385): the import log — every staged, reviewed, applied or failed file —
// narrowed by the same bar as the inbox and read through the same hook.
export function ImportHistory() {
  const { filters, rows, total, loading } = useImportLog("all");

  return (
    <section className="space-y-4">
      <Panel title="Log de importações">
        <p className="mb-3 text-[12.5px] text-ink-dim">
          Todo arquivo lido, revisado, aplicado ou falho, do mais recente ao mais antigo. As sessões
          ainda em revisão podem ser retomadas na Caixa de entrada.
        </p>

        {loading ? (
          <p className="text-[13.5px] text-ink-dim">Carregando…</p>
        ) : (
          <FilterBar
            fields={IMPORT_FILTER_FIELDS}
            values={filters.values}
            onChange={filters.setFilter}
            onClear={filters.clear}
            shown={rows.length}
            total={total}
          >
            {total === 0 ? (
              <p className="text-[13.5px] text-ink-dim">Nenhum arquivo importado.</p>
            ) : (
              <Table columns={IMPORT_LOG_COLUMNS} rows={rows} rowKey={(record) => record.id} />
            )}
          </FilterBar>
        )}
      </Panel>
    </section>
  );
}
