import type { ChangeEvent } from "react";
import { useCorporateActions, useImportMovements } from "../api/queries.ts";
import type { CorporateActionStatus } from "../api/types.ts";
import { Chip, type ChipTone } from "../components/Chip.tsx";
import { Panel } from "../components/Panel.tsx";
import { type Column, Table } from "../components/Table.tsx";
import {
  type CorporateActionRow,
  corporateActionRows,
  reviewQueue,
} from "../lib/corporateActions.ts";
import { movementsImportSummary } from "../lib/movements.ts";

// The engine's decision maps to the reference palette: applied is settled (green), a queued row
// needs attention (amber).
const STATUS_TONES: Record<CorporateActionStatus, ChipTone> = {
  APPLIED: "green",
  NEEDS_REVIEW: "amber",
};

const COLUMNS: Column<CorporateActionRow>[] = [
  {
    key: "date",
    header: "Data-ex",
    render: (row) => <span className="font-mono text-[11.5px] text-ink-dim">{row.date}</span>,
  },
  {
    key: "ticker",
    header: "Ticker",
    render: (row) => <span className="font-mono font-medium">{row.ticker}</span>,
  },
  { key: "kind", header: "Evento", render: (row) => row.kindLabel },
  {
    key: "detail",
    header: "Efeito",
    render: (row) => <span className="font-mono text-[12px]">{row.detail}</span>,
  },
  {
    key: "status",
    header: "Situação",
    render: (row) => <Chip tone={STATUS_TONES[row.status]}>{row.statusLabel}</Chip>,
  },
  {
    key: "source",
    header: "Origem",
    render: (row) => <span className="text-[11.5px] text-ink-dim">{row.source}</span>,
  },
];

// Eventos corporativos (issue #165): the event log with its review queue and the "Movimentação"
// import. The log is the engine's record — quantity events that already moved the position, cash
// events that already landed in the cash ledger, and the rows that wait for a human.
export function CorporateActions() {
  const actions = useCorporateActions();
  const importMovements = useImportMovements();

  const rows = corporateActionRows(actions.data ?? []);
  const review = reviewQueue(actions.data ?? []);

  function handleFile(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (file) importMovements.mutate(file);
    event.target.value = "";
  }

  return (
    <section>
      <div className="mb-4">
        <h1 className="font-cond text-[24px] font-bold tracking-[.005em]">Eventos corporativos</h1>
        <p className="text-[13px] text-ink-dim">
          Desdobramentos, grupamentos, bonificações e proventos em dinheiro aplicados ao livro, com
          os eventos que precisam de decisão humana.
        </p>
      </div>

      <div className="mb-5">
        <Panel title="Importar movimentações">
          <input
            type="file"
            accept=".xlsx"
            onChange={handleFile}
            disabled={importMovements.isPending}
            className="block w-full text-[13px] text-ink-dim file:mr-3 file:rounded-sm file:border-0 file:bg-surface-2 file:px-3 file:py-1.5 file:text-ink"
          />
          {importMovements.isPending && (
            <p className="mt-2 text-[12.5px] text-ink-dim">Importando…</p>
          )}
          {importMovements.isSuccess && (
            <p className="mt-2 text-[12.5px] text-yield">
              {movementsImportSummary(importMovements.data)}
            </p>
          )}
          {importMovements.error && (
            <p className="mt-2 text-[12.5px] text-tax">{importMovements.error.message}</p>
          )}
        </Panel>
      </div>

      {review.length > 0 && (
        <div className="mb-5">
          <Panel title="Fila de revisão">
            <p className="mb-3 text-[13px] text-ink-dim">
              {review.length} {review.length === 1 ? "evento aguarda" : "eventos aguardam"} decisão.
              Subscrição, cessão, reorganizações e linhas de renda fixa não são aplicadas
              automaticamente.
            </p>
            <div className="space-y-1.5">
              {review.map((action) => (
                <div key={action.id} className="flex items-center gap-3 text-[13px]">
                  <span className="font-mono text-[11.5px] text-ink-faint">{action.ex_date}</span>
                  <span className="font-mono font-medium">{action.ticker}</span>
                  <Chip tone="amber">{action.raw_type ?? action.kind}</Chip>
                </div>
              ))}
            </div>
          </Panel>
        </div>
      )}

      <h2 className="mb-3 font-cond text-[16px] font-bold">Registro</h2>
      {actions.isLoading ? (
        <p className="text-[13.5px] text-ink-dim">Carregando…</p>
      ) : actions.isError ? (
        <p className="text-[13.5px] text-tax">{actions.error.message}</p>
      ) : rows.length === 0 ? (
        <Panel>
          <p className="text-[13px] text-ink-dim">
            Nenhum evento corporativo. Importe o extrato &quot;Movimentação&quot; da B3.
          </p>
        </Panel>
      ) : (
        <Table columns={COLUMNS} rows={rows} rowKey={(row) => row.key} />
      )}
    </section>
  );
}
