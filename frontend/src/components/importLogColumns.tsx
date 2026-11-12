import type { Import } from "../api/types.ts";
import { formatLocalDateTime } from "../lib/format.ts";
import { DESTINATIONS } from "../lib/importDestinations.ts";
import { importAge, importSourceLabel } from "../lib/imports.ts";
import { ImportStatusChip } from "./ImportStatusChip.tsx";
import type { Column } from "./Table.tsx";

// The five columns the inbox queue and the import log share — file, source, destination, state and
// age — declared once so the two screens cannot drift. Each page appends its own action column.
export const IMPORT_LOG_COLUMNS: Column<Import>[] = [
  {
    key: "file",
    header: "Arquivo",
    render: (record) => <span className="font-mono font-medium">{record.file_name}</span>,
  },
  {
    key: "source",
    header: "Fonte",
    render: (record) => importSourceLabel(record.source),
  },
  {
    key: "destination",
    header: "Destino",
    render: (record) => (
      <span className="text-ink-dim">{DESTINATIONS[record.destination.object_type].label}</span>
    ),
  },
  {
    key: "status",
    header: "Estado",
    render: (record) => <ImportStatusChip status={record.status} />,
  },
  {
    key: "age",
    header: "Idade",
    render: (record) => (
      <span
        className="font-mono text-[11.5px] text-ink-dim"
        title={formatLocalDateTime(record.created_at)}
      >
        {importAge(record.created_at)}
      </span>
    ),
  },
];
