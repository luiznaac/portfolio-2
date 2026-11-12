import { type FormEvent, useState } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import {
  useApplyImport,
  useImport,
  useImportRows,
  useReviewImport,
  useStageImport,
} from "../api/queries.ts";
import type { Import, ImportRow } from "../api/types.ts";
import { Badge } from "../components/Badge.tsx";
import { Button } from "../components/Button.tsx";
import { ImportOutcomeSummary } from "../components/ImportOutcomeSummary.tsx";
import { ImportProvenance, shortHash } from "../components/ImportProvenance.tsx";
import { Panel } from "../components/Panel.tsx";
import { SituationChip } from "../components/SituationChip.tsx";
import { type Column, Table } from "../components/Table.tsx";
import { WizardStepper } from "../components/WizardStepper.tsx";
import { formatNumber, formatTradeSide } from "../lib/format.ts";
import {
  DESTINATIONS,
  type DestinationMeta,
  destinationRoute,
  resolveDestination,
  sessionPath,
  sessionStep,
} from "../lib/importDestinations.ts";
import { rejectedBatch, summarizeOutcomes, summarizeSituations } from "../lib/importSituations.ts";
import { ROUTES } from "../lib/routes.ts";

const STEPS = ["Arquivo", "Destino e mapeamento", "Linhas", "Aplicar"] as const;

const ROW_COLUMNS: Column<ImportRow>[] = [
  { key: "date", header: "Data", render: (row) => row.date ?? "—" },
  {
    key: "side",
    header: "Lado",
    render: (row) => (
      <Badge tone={row.side === "BUY" ? "ok" : "warn"}>{formatTradeSide(row.side)}</Badge>
    ),
  },
  {
    key: "ticker",
    header: "Ticker",
    render: (row) => <span className="font-mono font-medium">{row.ticker}</span>,
  },
  { key: "quantity", header: "Qtd", align: "right", render: (row) => formatNumber(row.quantity) },
  {
    key: "unit_price",
    header: "Preço",
    align: "right",
    render: (row) => formatNumber(row.unit_price),
  },
  { key: "institution", header: "Instituição", render: (row) => row.institution_name },
  {
    key: "situation",
    header: "Situação",
    render: (row) => <SituationChip situation={row.situation} />,
  },
  {
    key: "error",
    header: "Observação",
    render: (row) => <span className="text-tax">{row.error ?? ""}</span>,
  },
];

const CALLOUT =
  "rounded-sm border-l-[3px] border-accent bg-surface-2 px-3 py-2 text-[12.5px] text-ink";
const DANGER = "rounded-sm border-l-[3px] border-tax bg-surface-2 px-3 py-2 text-[12.5px] text-ink";

// The one review surface: a full-screen four-step assistant opened by every upload. Step 1 reads
// the file and stages it — nothing is applied there. The staged record is the session: leaving at
// any point parks it, and the record's destination decides where an apply lands.
export function ImportAssistant() {
  const { importId } = useParams();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const id = importId === undefined ? null : Number(importId);
  const sessionId = id !== null && Number.isFinite(id) && id > 0 ? id : null;

  const record = useImport(sessionId);
  const rows = useImportRows(sessionId);
  const stageImport = useStageImport();
  const reviewImport = useReviewImport();
  const applyImport = useApplyImport();

  const [file, setFile] = useState<File | null>(null);
  const [stepOverride, setStepOverride] = useState<number | null>(null);
  const contextDestination = resolveDestination(searchParams.get("object"));
  const destination =
    record.data === undefined
      ? contextDestination
      : DESTINATIONS[record.data.destination.object_type];
  const step = stepOverride ?? sessionStep(sessionId !== null, record.data?.status);

  const rowItems = rows.data ?? [];
  const situations = summarizeSituations(rowItems);
  const outcomes = summarizeOutcomes(rowItems);
  const rejected = rejectedBatch(rowItems);
  const validRows = situations.new + situations.duplicate_in_file;

  const mutationError = stageImport.error ?? reviewImport.error ?? applyImport.error;

  function handleStage(event: FormEvent) {
    event.preventDefault();
    if (!file || !destination.supported) return;
    stageImport.mutate(
      { source: destination.source, file, destination: destination.object },
      {
        onSuccess: ({ import: staged, created }) => {
          // A fresh upload walks the full flow; a hash match resumes wherever the record already is.
          setStepOverride(created ? 2 : sessionStep(true, staged.status));
          navigate(sessionPath(staged.id), { replace: true });
        },
      },
    );
  }

  function handleContinue() {
    if (sessionId === null || record.data === undefined) return;
    if (record.data.status === "REVIEWED") {
      setStepOverride(4);
      return;
    }
    reviewImport.mutate(sessionId, {
      onSuccess: () => setStepOverride(4),
    });
  }

  function handleApply() {
    if (sessionId === null) return;
    applyImport.mutate(sessionId, {
      onSuccess: (result) =>
        navigate(destinationRoute(result.record.destination.object_type), {
          state: { importApplied: result },
        }),
    });
  }

  return (
    <div
      data-testid="import-assistant"
      className="fixed inset-0 z-50 flex flex-col overflow-hidden bg-bg"
    >
      <header className="flex h-11 flex-none items-center gap-3 border-b border-line px-5">
        <span className="text-[13px] font-semibold text-ink">Importar arquivo</span>
        <span className="text-[12.5px] text-ink-dim">{destination.label}</span>
        <span className="flex-1" />
        {record.data !== undefined && (
          <ImportProvenance record={record.data} className="text-ink-faint" />
        )}
        <Button variant="ghost" onClick={() => navigate(ROUTES.imports)}>
          Sair
        </Button>
      </header>

      <div className="flex-1 overflow-y-auto px-5 py-4">
        <div className="mx-auto max-w-[1040px]">
          <WizardStepper steps={STEPS} current={step - 1} />
          {mutationError && <p className={`mb-4 ${DANGER}`}>{mutationError.message}</p>}

          {step === 1 && (
            <StepFile
              destination={destination}
              file={file}
              onFile={setFile}
              onStage={handleStage}
              pending={stageImport.isPending}
            />
          )}

          {step === 2 && record.data !== undefined && (
            <StepDestination destination={destination} record={record.data} />
          )}

          {step === 3 && record.data !== undefined && (
            <StepRows
              rows={rowItems}
              situations={situations}
              rejected={rejected.length}
              loading={rows.isLoading}
            />
          )}

          {step === 4 && record.data !== undefined && (
            <StepApply
              destination={destination}
              record={record.data}
              situations={situations}
              outcomes={outcomes}
              validRows={validRows}
              rejectedRows={rejected}
              pending={applyImport.isPending}
              onApply={handleApply}
            />
          )}
        </div>
      </div>

      <footer className="flex flex-none items-center gap-3 border-t border-line px-5 py-3">
        {step > 1 && (
          <Button variant="ghost" onClick={() => setStepOverride(step - 1)}>
            ← Voltar
          </Button>
        )}
        <span className="flex-1" />
        <span className="text-[11.5px] text-ink-faint">
          {step < 4
            ? "Sair a qualquer momento guarda a sessão na Caixa de entrada."
            : "Aplicar é a decisão; o registro guarda o hash e o original."}
        </span>
        {step === 2 && record.data !== undefined && (
          <Button variant="primary" onClick={() => setStepOverride(3)}>
            Continuar →
          </Button>
        )}
        {step === 3 && (
          <Button
            variant="primary"
            onClick={handleContinue}
            disabled={reviewImport.isPending || record.data?.status === "APPLIED"}
          >
            {reviewImport.isPending ? "Revisando…" : "Continuar →"}
          </Button>
        )}
      </footer>
    </div>
  );
}

function StepFile({
  destination,
  file,
  onFile,
  onStage,
  pending,
}: {
  destination: DestinationMeta;
  file: File | null;
  onFile: (file: File | null) => void;
  onStage: (event: FormEvent) => void;
  pending: boolean;
}) {
  return (
    <Panel
      title="Arquivo"
      action={
        <span className="text-[11.5px] text-ink-faint">destino e parser pré-preenchidos</span>
      }
    >
      {!destination.supported ? (
        <p className={CALLOUT}>
          O assistente para {destination.label} chega numa próxima fatia. Por enquanto, envie um
          extrato B3 de negociação para Ativos · Negócios.
        </p>
      ) : (
        <form onSubmit={onStage} className="space-y-4">
          <div className="rounded-sm border border-line bg-surface-2 px-3 py-2.5 text-[12.5px]">
            <p className="text-ink">
              Destino definido pelo contexto: <b>{destination.label}</b>
            </p>
            <p className="mt-1 text-ink-dim">Parser reconhecido: {destination.parser}</p>
          </div>
          <input
            type="file"
            accept=".xlsx"
            aria-label="Arquivo"
            onChange={(event) => onFile(event.target.files?.[0] ?? null)}
            className="block w-full text-[13px] text-ink-dim file:mr-3 file:rounded-sm file:border-0 file:bg-surface-2 file:px-3 file:py-1.5 file:text-ink"
          />
          <p className={CALLOUT}>
            Nada é aplicado no upload: o arquivo é lido, tem o hash calculado e fica em revisão. A
            aplicação é uma decisão separada.
          </p>
          <Button type="submit" variant="primary" disabled={!file || pending}>
            {pending ? "Enviando…" : "Enviar e revisar"}
          </Button>
        </form>
      )}
    </Panel>
  );
}

function StepDestination({
  destination,
  record,
}: { destination: DestinationMeta; record: Import }) {
  return (
    <div className="space-y-4">
      <Panel
        title="Destino"
        action={<span className="text-[11.5px] text-ink-faint">pré-preenchido</span>}
      >
        <dl className="divide-y divide-line-soft text-[12.5px]">
          <Detail label="Destino" value={destination.label} />
          <Detail label="Parser" value={destination.parser} />
          <Detail label="Aplicação" value="aplicar as linhas válidas" />
          <Detail
            label="Arquivo"
            value={`${record.file_name} · sha256 ${shortHash(record.file_hash)}`}
          />
        </dl>
      </Panel>

      <Panel
        title="Mapeamento"
        action={<span className="text-[11.5px] text-ink-faint">template lembrado</span>}
      >
        <div className="grid grid-cols-2 gap-x-6 gap-y-1 text-[12.5px]">
          {destination.mapping.map((cell) => (
            <div key={cell.from} className="flex items-center gap-2">
              <span className="text-ink-dim">{cell.from}</span>
              <span className="text-ink-faint">→</span>
              <span className="text-ink">{cell.to}</span>
            </div>
          ))}
        </div>
        <p className={`mt-3 ${CALLOUT}`}>
          O template reconhecido preenche as colunas, mas nada entra sem esta revisão.
        </p>
      </Panel>
    </div>
  );
}

function StepRows({
  rows,
  situations,
  rejected,
  loading,
}: {
  rows: ImportRow[];
  situations: ReturnType<typeof summarizeSituations>;
  rejected: number;
  loading: boolean;
}) {
  return (
    <Panel
      title="Linhas"
      action={
        <div className="flex flex-wrap items-center gap-3 text-[11.5px]">
          <span className="text-yield">{situations.new} aceitas</span>
          <span className="text-accent">{situations.duplicate_in_ledger} já no livro</span>
          <span className="text-accent">{situations.duplicate_in_file} repetidas no arquivo</span>
          <span className="text-tax">{rejected} com atenção</span>
        </div>
      }
    >
      {rejected > 0 && (
        <p className={`mb-3 ${CALLOUT}`}>
          {rejected} linha(s) com atenção ficam no lote de reenvio: as linhas válidas aplicam, a
          rejeitada não bloqueia o lote.
        </p>
      )}
      {loading ? (
        <p className="text-[13.5px] text-ink-dim">Carregando…</p>
      ) : rows.length === 0 ? (
        <p className="text-[13.5px] text-ink-dim">Nenhuma linha no arquivo.</p>
      ) : (
        <Table columns={ROW_COLUMNS} rows={rows} rowKey={(row) => row.id} />
      )}
    </Panel>
  );
}

function StepApply({
  destination,
  record,
  situations,
  outcomes,
  validRows,
  rejectedRows,
  pending,
  onApply,
}: {
  destination: DestinationMeta;
  record: Import;
  situations: ReturnType<typeof summarizeSituations>;
  outcomes: ReturnType<typeof summarizeOutcomes>;
  validRows: number;
  rejectedRows: ImportRow[];
  pending: boolean;
  onApply: () => void;
}) {
  return (
    <div className="space-y-4">
      <Panel title="O que esta importação muda">
        <ul className="divide-y divide-line-soft text-[12.5px]">
          {destination.changes.map((change) => (
            <li key={change.object} className="flex flex-wrap items-baseline gap-2 py-1.5">
              <span className="font-medium text-ink">{change.object}</span>
              <span className="text-ink-dim">{change.what}</span>
            </li>
          ))}
        </ul>
        <Link
          to={destination.route}
          className="mt-2 inline-block text-[12px] font-medium text-accent hover:underline"
        >
          Abrir {destination.label} →
        </Link>
      </Panel>

      <Panel title={record.status === "APPLIED" ? "Aplicada" : "Aplicar"}>
        {record.status === "APPLIED" ? (
          <div className="text-[12.5px] text-ink">
            <ImportOutcomeSummary
              applied={outcomes.applied}
              rejected={outcomes.rejected}
              skippedDuplicate={outcomes.skipped_duplicate}
            />
          </div>
        ) : (
          <div className="space-y-3 text-[12.5px] text-ink">
            <p>
              {validRows} linha(s) válida(s) · {situations.duplicate_in_ledger} já no livro ·{" "}
              {rejectedRows.length} no lote de reenvio.
            </p>
            <p className={DANGER}>
              Nada foi aplicado ainda. Aplicar grava as linhas válidas no destino; as rejeitadas
              ficam guardadas no lote de reenvio.
            </p>
            {rejectedRows.length > 0 && (
              <div className="text-ink-dim">
                {rejectedRows.map((row) => (
                  <p key={row.id}>
                    {row.ticker}: {row.error ?? "linha com atenção"}
                  </p>
                ))}
              </div>
            )}
            <Button variant="danger" onClick={onApply} disabled={pending}>
              {pending ? "Aplicando…" : `Aplicar ${validRows} linha(s) válida(s)`}
            </Button>
          </div>
        )}
      </Panel>
    </div>
  );
}

function Detail({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-baseline gap-3 py-1.5">
      <dt className="w-24 flex-none text-ink-dim">{label}</dt>
      <dd className="text-ink">{value}</dd>
    </div>
  );
}
