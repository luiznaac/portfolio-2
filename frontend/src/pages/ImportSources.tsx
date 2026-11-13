import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { useImportSources } from "../api/queries.ts";
import type { ImportAttempt, ImportSourceStatus } from "../api/types.ts";
import { ImportStatusChip } from "../components/ImportStatusChip.tsx";
import { Panel } from "../components/Panel.tsx";
import { sessionPath } from "../lib/importDestinations.ts";
import {
  importAge,
  importSourceDetail,
  importSourceLabel,
  importStatusLabel,
} from "../lib/imports.ts";

// Importações · Fontes (#386): the per-source status surface. One card per source carrying its last
// attempt, its last applied file, how many sessions still wait for a decision, and — when the latest
// attempt failed — the failed file beside the accepted one, each opening its own import record.
export function ImportSources() {
  const sources = useImportSources();
  const rows = sources.data ?? [];

  if (sources.isLoading) {
    return (
      <Panel title="Fontes">
        <p className="text-[13.5px] text-ink-dim">Carregando…</p>
      </Panel>
    );
  }

  if (rows.length === 0) {
    return (
      <Panel title="Fontes">
        <p className="text-[13.5px] text-ink-dim">
          Nenhuma fonte com tentativas ainda. O primeiro arquivo enviado abre o estado da sua fonte.
        </p>
      </Panel>
    );
  }

  return (
    <section className="grid gap-4 md:grid-cols-2">
      {rows.map((status) => (
        <SourceCard key={status.source} status={status} />
      ))}
    </section>
  );
}

function SourceCard({ status }: { status: ImportSourceStatus }) {
  const { source, last_attempt: lastAttempt, last_success: lastSuccess, pending, failure } = status;

  return (
    <div data-testid={`source-${source}`} className="min-w-0">
      <Panel
        title={importSourceLabel(source)}
        action={<ImportStatusChip status={lastAttempt.status} />}
      >
        <p className="text-[12px] text-ink-dim">{importSourceDetail(source)}</p>

        <dl className="mt-2 divide-y divide-line-soft text-[12.5px]">
          <CardRow label="Última tentativa">
            <FileAttempt attempt={lastAttempt} />
          </CardRow>
          <CardRow label="Última aplicação">
            {lastSuccess === undefined ? (
              <span className="text-ink-dim">—</span>
            ) : (
              <FileAttempt attempt={lastSuccess} />
            )}
          </CardRow>
          <CardRow label="Pendentes">
            <span className="text-ink">
              {pending === 0 ? "Nenhuma sessão em revisão" : `${pending} em revisão`}
            </span>
          </CardRow>
        </dl>

        {failure === undefined ? (
          <p className="mt-3 text-[12px] text-yield">Sem falhas na última tentativa.</p>
        ) : (
          <FailureRow failure={failure} accepted={lastSuccess} />
        )}
      </Panel>
    </div>
  );
}

function CardRow({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex items-baseline gap-3 py-1.5">
      <dt className="w-[112px] flex-none text-ink-dim">{label}</dt>
      <dd className="min-w-0">{children}</dd>
    </div>
  );
}

function FileAttempt({ attempt }: { attempt: ImportAttempt }) {
  return (
    <>
      <span className="font-mono text-ink">{attempt.file_name}</span>
      <span className="text-ink-dim"> · {importAge(attempt.created_at)}</span>
    </>
  );
}

const FAILURE_LABEL = "font-mono text-label font-semibold tracking-[.13em] uppercase";

function FailureRow({
  failure,
  accepted,
}: {
  failure: ImportAttempt;
  accepted: ImportAttempt | undefined;
}) {
  return (
    <div className="mt-3 grid gap-3 rounded-sm border-l-[3px] border-tax bg-surface-2 px-3 py-2 text-[12.5px] sm:grid-cols-2">
      <div className="min-w-0">
        <p className={`${FAILURE_LABEL} text-tax`}>{importStatusLabel(failure.status)}</p>
        <RecordLink attempt={failure} />
      </div>
      <div className="min-w-0">
        <p className={`${FAILURE_LABEL} text-yield`}>
          {accepted === undefined ? "—" : importStatusLabel(accepted.status)}
        </p>
        {accepted === undefined ? (
          <p className="text-ink-dim">—</p>
        ) : (
          <RecordLink attempt={accepted} />
        )}
      </div>
    </div>
  );
}

function RecordLink({ attempt }: { attempt: ImportAttempt }) {
  return (
    <Link
      to={sessionPath(attempt.import_id)}
      className="font-mono text-ink underline decoration-line hover:text-accent"
    >
      {attempt.file_name}
    </Link>
  );
}
