import { Link } from "react-router-dom";
import { useImports } from "../api/queries.ts";
import type { Import, ImportDestinationObject } from "../api/types.ts";
import { formatLocalDateTime } from "../lib/format.ts";
import { DESTINATIONS, sessionPath, sessionStep } from "../lib/importDestinations.ts";
import { Panel } from "./Panel.tsx";

// A parked import is the assistant session: STAGED or REVIEWED is still waiting for a decision.
// The strip is the one-line resume affordance on the owning object and in the inbox; an applied
// import is history, not a session.
function parked(record: Import): boolean {
  return record.status === "STAGED" || record.status === "REVIEWED";
}

export function ResumableImportStrip({
  destination,
}: {
  destination?: ImportDestinationObject;
}) {
  const imports = useImports();
  const sessions = (imports.data ?? []).filter(
    (record) =>
      parked(record) &&
      (destination === undefined || record.destination.object_type === destination),
  );

  if (sessions.length === 0) return null;

  return (
    <Panel
      title="Sessão em revisão"
      action={<span className="text-[11.5px] text-ink-faint">nada aplicado ainda</span>}
    >
      <ul className="divide-y divide-line-soft">
        {sessions.map((record) => (
          <li key={record.id} className="flex flex-wrap items-center gap-x-3 gap-y-1 py-1.5">
            <Link
              to={sessionPath(record.id)}
              className="font-mono text-[12.5px] font-medium text-ink hover:text-accent"
            >
              {record.file_name}
            </Link>
            <span className="text-[12px] text-ink-dim">
              {DESTINATIONS[record.destination.object_type].label}
            </span>
            <span className="text-[11.5px] text-ink-faint">
              etapa {sessionStep(true, record.status)} de 4 ·{" "}
              {formatLocalDateTime(record.created_at)}
            </span>
            <span className="flex-1" />
            <Link
              to={sessionPath(record.id)}
              className="text-[12px] font-medium text-accent hover:underline"
            >
              Retomar →
            </Link>
          </li>
        ))}
      </ul>
    </Panel>
  );
}
