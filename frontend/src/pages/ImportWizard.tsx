import { type FormEvent, useState } from "react";
import { useApplyImport, useImportRows, useReviewImport, useStageImport } from "../api/queries.ts";
import type { Import, ImportSource } from "../api/types.ts";
import { ImportStatusChip } from "../components/ImportStatusChip.tsx";
import { Panel } from "../components/Panel.tsx";
import { SituationChip } from "../components/SituationChip.tsx";
import { formatNumber, formatTradeSide } from "../lib/format.ts";
import { deriveStagedRows, summarizeSituations } from "../lib/importSituations.ts";

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

const SECONDARY_BUTTON =
  "rounded-md border border-slate-700 px-3 py-1.5 text-sm text-slate-300 hover:bg-slate-800 disabled:opacity-50";
const PRIMARY_BUTTON =
  "rounded-md bg-emerald-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-emerald-500 disabled:opacity-50";
const DANGER_BUTTON =
  "rounded-md bg-rose-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-rose-500 disabled:opacity-50";

function Stepper({ active }: { active: number }) {
  return (
    <ol className="mb-6 flex flex-wrap gap-x-6 gap-y-2">
      {STEPS.map((label, index) => {
        const done = index < active;
        return (
          <li key={label} className="flex items-center gap-2 text-sm">
            <span
              className={[
                "flex h-6 w-6 items-center justify-center rounded-full text-xs",
                index === active
                  ? "bg-emerald-600 text-white"
                  : done
                    ? "bg-emerald-900 text-emerald-300"
                    : "bg-slate-800 text-slate-500",
              ].join(" ")}
            >
              {index + 1}
            </span>
            <span className={index === active ? "text-slate-100" : "text-slate-500"}>{label}</span>
          </li>
        );
      })}
    </ol>
  );
}

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
      <h1 className="mb-4 text-lg font-semibold text-slate-100">Importar extrato</h1>
      <Stepper active={STEP_INDEX[state.step]} />

      {mutationError && (
        <p className="mb-4 rounded-md border border-rose-900 bg-rose-950/40 px-3 py-2 text-sm text-rose-300">
          {mutationError.message}
        </p>
      )}

      {state.step === "select" && (
        <Panel title="Selecionar arquivo">
          <form onSubmit={handleStage} className="space-y-4">
            <p className="text-sm text-slate-400">
              Selecione o extrato de negociação da B3 (planilha .xlsx).
            </p>
            <input
              type="file"
              accept=".xlsx"
              onChange={(event) => setFile(event.target.files?.[0] ?? null)}
              className="block w-full text-sm text-slate-300 file:mr-3 file:rounded-md file:border-0 file:bg-slate-800 file:px-3 file:py-1.5 file:text-slate-200"
            />
            <button
              type="submit"
              disabled={!file || stageImport.isPending}
              className={PRIMARY_BUTTON}
            >
              {stageImport.isPending ? "Enviando…" : "Enviar arquivo"}
            </button>
          </form>
        </Panel>
      )}

      {state.step === "review" && (
        <Panel title="Revisar linhas">
          <div className="mb-4 flex flex-wrap gap-x-4 gap-y-1 text-sm">
            <span className="text-emerald-400">{counts.new} nova(s)</span>
            <span className="text-amber-400">{counts.duplicate} duplicada(s)</span>
            <span className="text-rose-400">{counts.warning} com atenção</span>
          </div>

          {rows.isLoading ? (
            <p className="text-sm text-slate-400">Carregando…</p>
          ) : staged.length === 0 ? (
            <p className="text-sm text-slate-400">Nenhuma linha no arquivo.</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead>
                  <tr className="border-b border-slate-800 text-xs text-slate-500 uppercase">
                    <th className="py-2 pr-3">Data</th>
                    <th className="pr-3">Lado</th>
                    <th className="pr-3">Ticker</th>
                    <th className="pr-3 text-right">Qtd</th>
                    <th className="pr-3 text-right">Preço</th>
                    <th className="pr-3">Instituição</th>
                    <th className="pr-3">Situação</th>
                    <th>Observação</th>
                  </tr>
                </thead>
                <tbody>
                  {staged.map(({ row, situation }) => (
                    <tr key={row.id} className="border-b border-slate-900 align-top">
                      <td className="py-2 pr-3">{row.date ?? "—"}</td>
                      <td className="pr-3">{formatTradeSide(row.side)}</td>
                      <td className="pr-3">{row.ticker}</td>
                      <td className="pr-3 text-right">{formatNumber(row.quantity)}</td>
                      <td className="pr-3 text-right">{formatNumber(row.unit_price)}</td>
                      <td className="pr-3">{row.institution_name}</td>
                      <td className="pr-3">
                        <SituationChip situation={situation} />
                      </td>
                      <td className="text-rose-300">{row.error ?? ""}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <div className="mt-4 flex gap-2">
            <button type="button" onClick={reset} className={SECONDARY_BUTTON}>
              Cancelar
            </button>
            <button
              type="button"
              onClick={handleReview}
              disabled={reviewImport.isPending}
              className={PRIMARY_BUTTON}
            >
              {reviewImport.isPending ? "Confirmando…" : "Continuar"}
            </button>
          </div>
        </Panel>
      )}

      {state.step === "confirm" && (
        <Panel title="Confirmar importação">
          <p className="text-sm text-slate-300">
            Resumo da revisão: {counts.new} nova(s), {counts.duplicate} duplicada(s),{" "}
            {counts.warning} com atenção. O servidor decide o que gravar ao aplicar, incluindo
            duplicatas.
          </p>
          <p className="mt-3 rounded-md border border-rose-900 bg-rose-950/40 px-3 py-2 text-sm text-rose-300">
            Esta ação é irreversível: as linhas válidas são gravadas no livro e a importação não
            pode ser desfeita.
          </p>
          <div className="mt-4 flex gap-2">
            <button type="button" onClick={backToReview} className={SECONDARY_BUTTON}>
              Voltar
            </button>
            <button
              type="button"
              onClick={handleApply}
              disabled={applyImport.isPending}
              className={DANGER_BUTTON}
            >
              {applyImport.isPending ? "Importando…" : "Importar definitivamente"}
            </button>
          </div>
        </Panel>
      )}

      {state.step === "result" && (
        <Panel title="Resultado">
          <div className="flex items-center gap-2 text-sm text-slate-300">
            <span>Arquivo: {state.applied.file_name}</span>
            <ImportStatusChip status={state.applied.status} />
          </div>
          <button type="button" onClick={reset} className={`${PRIMARY_BUTTON} mt-4`}>
            Nova importação
          </button>
        </Panel>
      )}
    </section>
  );
}
