import { type FormEvent, useState } from "react";
import { useApplyImport, useImportRows, useReviewImport, useStageImport } from "../api/queries.ts";
import type { Import, ImportSource } from "../api/types.ts";
import { Badge } from "../components/Badge.tsx";
import { Button } from "../components/Button.tsx";
import { ImportStatusChip } from "../components/ImportStatusChip.tsx";
import { Panel } from "../components/Panel.tsx";
import { SituationChip } from "../components/SituationChip.tsx";
import { type Column, Table } from "../components/Table.tsx";
import { WizardStepper } from "../components/WizardStepper.tsx";
import { formatNumber, formatTradeSide } from "../lib/format.ts";
import { type StagedRow, deriveStagedRows, summarizeSituations } from "../lib/importSituations.ts";

// The B3 Negociação extract is the only layout the backend parses today (see
// B3NegotiationParser), so the wizard pins the source rather than offering options the pipeline
// cannot stage.
const SOURCE: ImportSource = "B3_TRADES";

const STEPS = ["Selecionar arquivo", "Revisar", "Confirmar", "Resultado"] as const;

type WizardState =
  | { step: "select" }
  | { step: "review"; importId: number }
  | { step: "confirm"; importId: number }
  | { step: "result"; applied: Import };

const STEP_INDEX: Record<WizardState["step"], number> = {
  select: 0,
  review: 1,
  confirm: 2,
  result: 3,
};

const CRIT_ALERT =
  "rounded-sm border-l-[3px] border-tax bg-surface-2 px-3 py-2 text-[12.5px] text-ink";

const ROW_COLUMNS: Column<StagedRow>[] = [
  { key: "date", header: "Data", render: ({ row }) => row.date ?? "—" },
  {
    key: "side",
    header: "Lado",
    render: ({ row }) => (
      <Badge tone={row.side === "BUY" ? "ok" : "warn"}>{formatTradeSide(row.side)}</Badge>
    ),
  },
  {
    key: "ticker",
    header: "Ticker",
    render: ({ row }) => <span className="font-mono font-medium">{row.ticker}</span>,
  },
  {
    key: "quantity",
    header: "Qtd",
    align: "right",
    render: ({ row }) => formatNumber(row.quantity),
  },
  {
    key: "unit_price",
    header: "Preço",
    align: "right",
    render: ({ row }) => formatNumber(row.unit_price),
  },
  { key: "institution", header: "Instituição", render: ({ row }) => row.institution_name },
  {
    key: "situation",
    header: "Situação",
    render: ({ situation }) => <SituationChip situation={situation} />,
  },
  {
    key: "error",
    header: "Observação",
    render: ({ row }) => <span className="text-tax">{row.error ?? ""}</span>,
  },
];

export function ImportWizard() {
  const [state, setState] = useState<WizardState>({ step: "select" });
  const [file, setFile] = useState<File | null>(null);

  const stageImport = useStageImport();
  const reviewImport = useReviewImport();
  const applyImport = useApplyImport();

  // Rows are only meaningful while reviewing/confirming; reading them outside those steps would be
  // a wasted fetch.
  const importId = state.step === "review" || state.step === "confirm" ? state.importId : null;
  const rows = useImportRows(importId);
  const staged = deriveStagedRows(rows.data ?? []);
  const counts = summarizeSituations(staged);

  const mutationError = stageImport.error ?? reviewImport.error ?? applyImport.error;

  function handleStage(event: FormEvent) {
    event.preventDefault();
    if (!file) return;
    stageImport.mutate(
      { source: SOURCE, file },
      { onSuccess: (created) => setState({ step: "review", importId: created.id }) },
    );
  }

  function handleReview() {
    if (state.step !== "review") return;
    const id = state.importId;
    reviewImport.mutate(id, { onSuccess: () => setState({ step: "confirm", importId: id }) });
  }

  function handleApply() {
    if (state.step !== "confirm") return;
    const id = state.importId;
    applyImport.mutate(id, { onSuccess: (applied) => setState({ step: "result", applied }) });
  }

  function backToReview() {
    if (state.step !== "confirm") return;
    setState({ step: "review", importId: state.importId });
  }

  function reset() {
    setFile(null);
    stageImport.reset();
    reviewImport.reset();
    applyImport.reset();
    setState({ step: "select" });
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
          <div className="mb-4 flex flex-wrap gap-x-4 gap-y-1 text-[13.5px]">
            <span className="text-yield">{counts.new} nova(s)</span>
            <span className="text-accent">{counts.duplicate} duplicada(s)</span>
            <span className="text-tax">{counts.warning} com atenção</span>
          </div>

          {rows.isLoading ? (
            <p className="text-[13.5px] text-ink-dim">Carregando…</p>
          ) : staged.length === 0 ? (
            <p className="text-[13.5px] text-ink-dim">Nenhuma linha no arquivo.</p>
          ) : (
            <Table columns={ROW_COLUMNS} rows={staged} rowKey={({ row }) => row.id} />
          )}

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
          <p className="text-[13px] text-ink">
            Resumo da revisão: {counts.new} nova(s), {counts.duplicate} duplicada(s),{" "}
            {counts.warning} com atenção. O servidor decide o que gravar ao aplicar, incluindo
            duplicatas.
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
          <div className="flex items-center gap-2 text-[13px] text-ink">
            <span>Arquivo: {state.applied.file_name}</span>
            <ImportStatusChip status={state.applied.status} />
          </div>
          <Button variant="primary" className="mt-4" onClick={reset}>
            Nova importação
          </Button>
        </Panel>
      )}
    </section>
  );
}
