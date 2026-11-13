import { type ChangeEvent, useState } from "react";
import { Link } from "react-router-dom";
import {
  useCorporateActions,
  useImportMovements,
  useResolveCorporateAction,
} from "../api/queries.ts";
import type { CorporateAction } from "../api/types.ts";
import { Button } from "../components/Button.tsx";
import { Chip } from "../components/Chip.tsx";
import { Field, Input } from "../components/Field.tsx";
import { Panel } from "../components/Panel.tsx";
import {
  type CorporateActionRow,
  RESOLUTION_FIELD_LABELS,
  type ResolutionDraft,
  appliedHistory,
  catalogBlocked,
  corporateActionDetail,
  corporateActionKindLabel,
  corporateActionRows,
  initialDraft,
  isManualReview,
  resolutionFields,
  resolutionPayload,
  resolutionReady,
  reviewQueue,
  reviewReason,
} from "../lib/corporateActions.ts";
import { importSourceLabel } from "../lib/imports.ts";
import { movementsImportSummary } from "../lib/movements.ts";
import { positionDetail } from "../lib/routes.ts";

// Importações · Ações corporativas (issue #387): the review surface for the events the engine
// refused to apply on its own, beside the applied history. Resolving a parked event supplies the
// data the export lacked — or, for a kind the engine never computes, acknowledges it as handled —
// and the row moves to the history without a reload. The Movimentações upload is the feed the log
// is built from, kept as a compact strip above the two panels.
export function CorporateActions() {
  const actions = useCorporateActions();
  const importMovements = useImportMovements();

  const all = actions.data ?? [];
  const review = reviewQueue(all);
  const history = appliedHistory(all);

  function handleFile(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (file) importMovements.mutate(file);
    event.target.value = "";
  }

  return (
    <section data-testid="corporate-actions">
      <div className="mb-4">
        <Panel title="Importar movimentações">
          <div className="flex flex-wrap items-center gap-3">
            <input
              type="file"
              accept=".xlsx"
              onChange={handleFile}
              disabled={importMovements.isPending}
              className="block text-[13px] text-ink-dim file:mr-3 file:rounded-sm file:border-0 file:bg-surface-2 file:px-3 file:py-1.5 file:text-ink"
            />
            {importMovements.isPending && <p className="text-[12.5px] text-ink-dim">Importando…</p>}
            {importMovements.isSuccess && (
              <p className="text-[12.5px] text-yield">
                {movementsImportSummary(importMovements.data)}
              </p>
            )}
            {importMovements.error && (
              <p className="text-[12.5px] text-tax">{importMovements.error.message}</p>
            )}
          </div>
        </Panel>
      </div>

      {actions.isLoading ? (
        <p className="text-[13.5px] text-ink-dim">Carregando…</p>
      ) : actions.isError ? (
        <p className="text-[13.5px] text-tax">{actions.error.message}</p>
      ) : (
        <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
          <Panel
            title="Fila de revisão"
            action={
              <Chip tone={review.length === 0 ? "green" : "amber"}>
                {review.length === 0
                  ? "Nada aguardando"
                  : `${review.length} ${review.length === 1 ? "evento" : "eventos"}`}
              </Chip>
            }
          >
            {review.length === 0 ? (
              <p className="text-[13px] text-ink-dim">
                Nenhum evento aguarda decisão. O motor aplicou tudo o que sabia calcular.
              </p>
            ) : (
              <div className="divide-y divide-line-soft">
                {review.map((action) => (
                  <ReviewRow key={action.id} action={action} />
                ))}
              </div>
            )}
          </Panel>

          <Panel
            title="Histórico aplicado"
            action={
              <span className="font-mono text-[11px] text-ink-dim">
                {history.length} {history.length === 1 ? "evento" : "eventos"}
              </span>
            }
          >
            {history.length === 0 ? (
              <p className="text-[13px] text-ink-dim">
                Nenhum evento aplicado. Importe o extrato &quot;Movimentação&quot; da B3.
              </p>
            ) : (
              <div className="divide-y divide-line-soft">
                {corporateActionRows(history).map((row) => (
                  <HistoryRow key={row.key} row={row} />
                ))}
              </div>
            )}
          </Panel>
        </div>
      )}
    </section>
  );
}

// One parked event and the decision it needs. A computable kind shows the fields the export did not
// carry; a manual kind shows the single acknowledgement, because no amount of data lets the engine
// apply a rights issue or a merger ratio on its own.
function ReviewRow({ action }: { action: CorporateAction }) {
  const resolve = useResolveCorporateAction();
  const fields = resolutionFields(action);
  const manual = isManualReview(action);
  const [draft, setDraft] = useState<ResolutionDraft>(() => initialDraft(action));

  function submit() {
    resolve.mutate({ id: action.id, resolution: resolutionPayload(action, draft) });
  }

  return (
    <div data-testid={`corporate-review-${action.id}`} className="py-3 first:pt-0 last:pb-0">
      <div className="flex flex-wrap items-baseline gap-2">
        <span className="font-mono text-[11.5px] text-ink-faint">{action.ex_date}</span>
        <span className="font-mono text-[13px] font-medium">{action.ticker}</span>
        <Chip tone="amber">{corporateActionKindLabel(action.kind)}</Chip>
        <span className="font-mono text-[12px] text-ink-dim">{corporateActionDetail(action)}</span>
      </div>
      <p className="mt-1 text-[12.5px] text-ink-dim">{reviewReason(action)}</p>
      {catalogBlocked(action) && (
        <p className="mt-1 text-[12px]">
          <Link to={positionDetail(action.ticker)} className="text-principal underline">
            Ativos · Posições → {action.ticker}
          </Link>
        </p>
      )}
      <p className="mt-0.5 font-mono text-[10.5px] text-ink-faint">
        {[importSourceLabel(action.source), action.process_number ?? action.raw_type]
          .filter(Boolean)
          .join(" · ")}
      </p>
      <div className="mt-2 flex flex-wrap items-end gap-2">
        {fields.map((field) => (
          <Field key={field} label={RESOLUTION_FIELD_LABELS[field]}>
            <Input
              type={field === "target_ticker" ? "text" : "number"}
              step={field === "target_ticker" ? undefined : "any"}
              min={field === "target_ticker" ? undefined : 0}
              value={draft[field] ?? ""}
              onChange={(event) =>
                setDraft((previous) => ({ ...previous, [field]: event.target.value }))
              }
              className="w-32"
            />
          </Field>
        ))}
        <Button
          variant="primary"
          onClick={submit}
          disabled={resolve.isPending || !resolutionReady(action, draft) || catalogBlocked(action)}
        >
          {manual ? "Marcar como tratada" : "Aplicar"}
        </Button>
      </div>
      {resolve.error && <p className="mt-1 text-[12px] text-tax">{resolve.error.message}</p>}
    </div>
  );
}

// One settled event: the engine's math ran, or the owner acknowledged a manual kind. The two are
// both the applied history; the status chip says the event is settled, not which path it took.
function HistoryRow({ row }: { row: CorporateActionRow }) {
  return (
    <div
      data-testid={`corporate-history-${row.key}`}
      className="flex flex-wrap items-baseline gap-2 py-2 text-[12.5px]"
    >
      <span className="font-mono text-[11.5px] text-ink-faint">{row.date}</span>
      <span className="font-mono font-medium">{row.ticker}</span>
      <span className="text-ink-dim">{row.kindLabel}</span>
      <span className="font-mono text-[11.5px] text-ink-dim">{row.detail}</span>
      <span className="font-mono text-[10.5px] text-ink-faint">{row.source}</span>
      <span className="flex-1" />
      <Chip tone="green">{row.statusLabel}</Chip>
    </div>
  );
}
