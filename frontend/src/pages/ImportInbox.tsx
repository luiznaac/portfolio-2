import { Link } from "react-router-dom";
import type { Import } from "../api/types.ts";
import { FilterBar } from "../components/FilterBar.tsx";
import { Panel } from "../components/Panel.tsx";
import { type Column, Table } from "../components/Table.tsx";
import { IMPORT_LOG_COLUMNS } from "../components/importLogColumns.tsx";
import { useImportLog } from "../hooks/useImportLog.ts";
import { assistantPath, sessionPath } from "../lib/importDestinations.ts";
import { IMPORT_FILTER_FIELDS } from "../lib/importFilters.ts";

const RESUME_COLUMN: Column<Import> = {
  key: "resume",
  header: "",
  align: "right",
  render: (record) => (
    <Link
      to={sessionPath(record.id)}
      className="text-[12px] font-medium text-accent hover:underline"
    >
      Retomar →
    </Link>
  ),
};

const COLUMNS = [...IMPORT_LOG_COLUMNS, RESUME_COLUMN];

// Importações · Caixa de entrada (#385): the queue of parked sessions — staged or reviewed imports
// still waiting for a decision — with the shared filter bar. A row resumes the assistant session it
// parked; the applied records continue in the import history.
export function ImportInbox() {
  const { filters, rows, total, loading } = useImportLog("parked");

  return (
    <section className="space-y-4">
      <Panel
        title="Na fila"
        action={
          <Link
            to={assistantPath("TRADES")}
            className="text-[12px] font-medium text-accent hover:underline"
          >
            Enviar arquivo →
          </Link>
        }
      >
        <p className="mb-3 text-[12.5px] text-ink-dim">
          Sessões em revisão — o arquivo foi lido e nada foi aplicado ainda. Retomar abre o
          assistente na etapa em que a sessão parou; os aplicados continuam no Histórico.
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
              <p className="text-[13.5px] text-ink-dim">Nenhuma sessão em revisão.</p>
            ) : (
              <Table columns={COLUMNS} rows={rows} rowKey={(record) => record.id} />
            )}
          </FilterBar>
        )}
      </Panel>
    </section>
  );
}
