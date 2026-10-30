import { type FormEvent, useState } from "react";
import { Link } from "react-router-dom";
import type { StagedImport } from "../api/client.ts";
import { useApplyImport, useImportRows, useReviewImport, useStageImport } from "../api/queries.ts";
import type { Import, ImportRow, ImportSource } from "../api/types.ts";
import { Badge } from "../components/Badge.tsx";
import { Button } from "../components/Button.tsx";
import { ImportStatusChip } from "../components/ImportStatusChip.tsx";
import { Panel } from "../components/Panel.tsx";
import { SituationChip } from "../components/SituationChip.tsx";
import { type Column, Table } from "../components/Table.tsx";
import { WizardStepper } from "../components/WizardStepper.tsx";
import { formatLocalDateTime, formatNumber, formatTradeSide } from "../lib/format.ts";
import { summarizeSituations } from "../lib/importSituations.ts";

// The B3 Negociação extract is the only layout the backend parses today (see
// B3NegotiationParser), so the wizard pins the source rather than offering options the pipeline
// cannot stage.
const SOURCE: ImportSource = "B3_TRADES";

const STEPS = ["Selecionar arquivo", "Revisar", "Confirmar", "Resultado"] as const;

// The staged import plus whether the upload resumed it. `resumed` is true only when the server
// answered 200 for a known hash, so a brand-new file keeps the plain flow with no banner.
interface UploadState {
  import: Import;
  resumed: boolean;
}

type WizardState =
  | { step: "select" }
  | { step: "review"; upload: UploadState }
  | { step: "confirm"; upload: UploadState }
  | { step: "result"; upload: UploadState };

const STEP_INDEX: Record<WizardState["step"], number> = {
  select: 0,
  review: 1,
  confirm: 2,
  result: 3,
};

const CRIT_ALERT =
  "rounded-sm border-l-[3px] border-tax bg-surface-2 px-3 py-2 text-[12.5px] text-ink";

const RESUME_ALERT =
  "mb-4 rounded-sm border-l-[3px] border-accent bg-surface-2 px-3 py-2 text-[12.5px] text-ink";

function landing(staged: Import, created: boolean): WizardState {
  const upload = { import: staged, resumed: !created };
  switch (staged.status) {
    case "STAGED":
      return { step: "review", upload };
    case "REVIEWED":
      return { step: "confirm", upload };
    case "APPLIED":
    case "FAILED":
      return { step: "result", upload };
  }
}

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
  {
    key: "quantity",
    header: "Qtd",
    align: "right",
    render: (row) => formatNumber(row.quantity),
  },
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

export function ImportWizard() {
  const [state, setState] = useState<WizardState>({ step: "select" });
  const [file, setFile] = useState<File | null>(null);

  const stageImport = useStageImport();
  const reviewImport = useReviewImport();
  const applyImport = useApplyImport();

  // Rows are only meaningful from the review step on; a failed import has none at all, so reading
  // them would be a wasted fetch.
  const importId =
    state.step === "select" || (state.step === "result" && state.upload.import.status === "FAILED")
      ? null
      : state.upload.import.id;
  const rows = useImportRows(importId);
  const rowItems = rows.data ?? [];
  const counts = summarizeSituations(rowItems);

  const mutationError = stageImport.error ?? reviewImport.error ?? applyImport.error;

  function handleStage(event: FormEvent) {
    event.preventDefault();
    if (!file) return;
    stageImport.mutate(
      { source: SOURCE, file },
      { onSuccess: (staged: StagedImport) => setState(landing(staged.import, staged.created)) },
    );
  }

  function handleReview() {
    if (state.step !== "review") return;
    const upload = state.upload;
    // A review just done in this session is not a resume of an already-reviewed file, so the
    // Confirmar step starts without a banner even when the upload itself resumed a staged file.
    reviewImport.mutate(upload.import.id, {
      onSuccess: (reviewed) =>
        setState({ step: "confirm", upload: { ...upload, import: reviewed, resumed: false } }),
    });
  }

  function handleApply() {
    if (state.step !== "confirm") return;
    const upload = state.upload;
    applyImport.mutate(upload.import.id, {
      onSuccess: (applied) => setState({ step: "result", upload: { ...upload, import: applied } }),
    });
  }

  function backToReview() {
    if (state.step !== "confirm") return;
    setState({ step: "review", upload: state.upload });
  }

  function reset() {
    setFile(null);
    stageImport.reset();
    reviewImport.reset();
    applyImport.reset();
    setState({ step: "select" });
  }

  function rowTable() {
    return rows.isLoading ? (
      <p className="text-[13.5px] text-ink-dim">Carregando…</p>
    ) : rowItems.length === 0 ? (
      <p className="text-[13.5px] text-ink-dim">Nenhuma linha no arquivo.</p>
    ) : (
      <Table columns={ROW_COLUMNS} rows={rowItems} rowKey={(row) => row.id} />
    );
  }

  return (
    <section>
      <h1 className="mb-4 font-cond text-[24px] font-bold tracking-[.005em]">Importar extrato</h1>
      <WizardStepper steps={STEPS} current={STEP_INDEX[state.step]} />

      {mutationError && <p className={`mb-4 ${CRIT_ALERT}`}>{mutationError.message}</p>}

      {state.step === "select" && (
        <Panel title="Selecionar arquivo">
          <form onSubmit={handleStage} className="space-y-4">
            <p className="text-[13.5px] text-ink-dim">
              Selecione o extrato de negociação da B3 (planilha .xlsx).
            </p>
            <input
              type="file"
              accept=".xlsx"
              aria-label="Arquivo"
              onChange={(event) => setFile(event.target.files?.[0] ?? null)}
              className="block w-full text-[13px] text-ink-dim file:mr-3 file:rounded-sm file:border-0 file:bg-surface-2 file:px-3 file:py-1.5 file:text-ink"
            />
            <Button type="submit" variant="primary" disabled={!file || stageImport.isPending}>
              {stageImport.isPending ? "Enviando…" : "Enviar arquivo"}
            </Button>
          </form>
        </Panel>
      )}

      {state.step === "review" && (
        <Panel title="Revisar linhas">
          {state.upload.resumed && (
            <p className={RESUME_ALERT}>
              Este arquivo já foi enviado em {formatLocalDateTime(state.upload.import.created_at)} e
              aguarda revisão. Retomando a revisão.
            </p>
          )}

          <div className="mb-4 flex flex-wrap gap-x-4 gap-y-1 text-[13.5px]">
            <span className="text-yield">{counts.new} nova(s)</span>
            <span className="text-accent">{counts.duplicate_in_ledger} já no livro</span>
            <span className="text-accent">{counts.duplicate_in_file} repetida(s) no arquivo</span>
            <span className="text-tax">{counts.error} com atenção</span>
          </div>

          {rowTable()}

          <div className="mt-4 flex gap-2">
            <Button variant="ghost" onClick={reset}>
              Cancelar
            </Button>
            <Button variant="primary" onClick={handleReview} disabled={reviewImport.isPending}>
              {reviewImport.isPending ? "Confirmando…" : "Continuar"}
            </Button>
          </div>
        </Panel>
      )}

      {state.step === "confirm" && (
        <Panel title="Confirmar importação">
          {state.upload.resumed && (
            <p className={RESUME_ALERT}>
              Este arquivo já foi revisado em {formatLocalDateTime(state.upload.import.created_at)}.
              Retome para confirmar.
            </p>
          )}
          <p className="text-[13px] text-ink">
            Resumo da revisão: {counts.new} nova(s), {counts.duplicate_in_ledger} já no livro,{" "}
            {counts.duplicate_in_file} repetida(s) no arquivo, {counts.error} com atenção. O
            servidor decide o que gravar ao aplicar, incluindo duplicatas.
          </p>
          <p className={`mt-3 ${CRIT_ALERT}`}>
            Esta ação é irreversível: as linhas válidas são gravadas no livro e a importação não
            pode ser desfeita.
          </p>
          <div className="mt-4 flex gap-2">
            <Button variant="ghost" onClick={backToReview}>
              Voltar
            </Button>
            <Button variant="danger" onClick={handleApply} disabled={applyImport.isPending}>
              {applyImport.isPending ? "Importando…" : "Importar definitivamente"}
            </Button>
          </div>
        </Panel>
      )}

      {state.step === "result" && (
        <Panel title="Resultado">
          {state.upload.import.status === "APPLIED" && state.upload.resumed && (
            <p className={RESUME_ALERT}>
              Este arquivo já foi importado em {formatLocalDateTime(state.upload.import.created_at)}
              .
            </p>
          )}
          {state.upload.import.status === "FAILED" && state.upload.resumed && (
            <p className={`mb-4 ${CRIT_ALERT}`}>
              Este arquivo falhou no processamento em{" "}
              {formatLocalDateTime(state.upload.import.created_at)} e não pode ser reutilizado.
              Selecione outro arquivo.
            </p>
          )}

          <div className="flex flex-wrap items-center gap-2 text-[13px] text-ink">
            <span>Arquivo: {state.upload.import.file_name}</span>
            <ImportStatusChip status={state.upload.import.status} />
            <span className="text-ink-dim">
              Enviado em {formatLocalDateTime(state.upload.import.created_at)}
            </span>
          </div>

          {state.upload.import.status === "APPLIED" && <div className="mt-4">{rowTable()}</div>}

          <div className="mt-4 flex items-center gap-4">
            <Button variant="primary" onClick={reset}>
              {state.upload.import.status === "FAILED"
                ? "Selecionar outro arquivo"
                : "Nova importação"}
            </Button>
            {state.upload.import.status === "APPLIED" && (
              <Link
                to="/trades"
                className="text-[13px] font-medium text-accent hover:text-accent-2"
              >
                Ver negócios
              </Link>
            )}
          </div>
        </Panel>
      )}
    </section>
  );
}
