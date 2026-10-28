import type { ChangeEvent } from "react";
import { useImportIncome, useIncome, useUpcomingIncome } from "../api/queries.ts";
import type { IncomeStatus } from "../api/types.ts";
import { Chip, type ChipTone } from "../components/Chip.tsx";
import { Panel } from "../components/Panel.tsx";
import { type Column, Table } from "../components/Table.tsx";
import { formatCurrency } from "../lib/format.ts";
import {
  type IncomeRow,
  byPayableDate,
  incomeRows,
  incomeStatusLabel,
  netTotal,
} from "../lib/income.ts";

// The lifecycle stage maps to the reference sign palette (Chip names a tone, never a colour): a
// forecast is neutral/blue, a settled payment green, a reconciled one violet.
const STATUS_TONES: Record<IncomeStatus, ChipTone> = {
  ANNOUNCED: "neutral",
  PAYABLE: "blue",
  RECEIVED: "green",
  RECONCILED: "violet",
};

const COLUMNS: Column<IncomeRow>[] = [
  {
    key: "date",
    header: "Pagamento",
    render: (row) => <span className="font-mono text-[11.5px] text-ink-dim">{row.date}</span>,
  },
  {
    key: "ticker",
    header: "Ticker",
    render: (row) => <span className="font-mono font-medium">{row.ticker}</span>,
  },
  { key: "type", header: "Tipo", render: (row) => row.typeLabel },
  {
    key: "status",
    header: "Situação",
    render: (row) => <Chip tone={STATUS_TONES[row.status]}>{row.statusLabel}</Chip>,
  },
  { key: "gross", header: "Bruto", align: "right", render: (row) => formatCurrency(row.gross) },
  {
    key: "retained",
    header: "Retido",
    align: "right",
    render: (row) =>
      row.retained > 0 ? (
        <span className="text-tax">{formatCurrency(row.retained)}</span>
      ) : (
        <span className="text-ink-faint">—</span>
      ),
  },
  {
    key: "net",
    header: "Líquido",
    align: "right",
    render: (row) => <span className="text-yield">{formatCurrency(row.net)}</span>,
  },
];

// Proventos (issue #164): the income ledger with its lifecycle column (anunciado / pagável /
// recebido / conciliado), the upcoming forecast, and the "Proventos a Receber" import.
export function Income() {
  const income = useIncome();
  const upcoming = useUpcomingIncome();
  const importIncome = useImportIncome();

  const rows = incomeRows(income.data ?? []);
  const forecast = byPayableDate(upcoming.data ?? []);
  const forecastTotal = netTotal(forecast);

  function handleFile(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (file) importIncome.mutate(file);
    event.target.value = "";
  }

  return (
    <section>
      <div className="mb-4">
        <h1 className="font-cond text-[24px] font-bold tracking-[.005em]">Proventos</h1>
        <p className="text-[13px] text-ink-dim">
          Previsto a partir da B3, na posição da data-com. Conciliação contra o extrato
          &quot;Movimentação&quot;.
        </p>
      </div>

      <div className="mb-5">
        <Panel title="Proventos previstos">
          {upcoming.isLoading ? (
            <p className="text-[13.5px] text-ink-dim">Carregando…</p>
          ) : forecast.length === 0 ? (
            <p className="text-[13.5px] text-ink-dim">Nenhum provento a receber.</p>
          ) : (
            <>
              <div className="flex items-baseline gap-3">
                <span className="font-mono text-[22px] text-yield">
                  {formatCurrency(forecastTotal)}
                </span>
                <span className="text-[12.5px] text-ink-dim">
                  {forecast.length} {forecast.length === 1 ? "pagamento" : "pagamentos"} a receber
                </span>
              </div>
              <div className="mt-3 space-y-1.5">
                {forecast.map((item) => (
                  <div key={item.id} className="flex items-center gap-3 text-[13px]">
                    <span className="font-mono text-[11.5px] text-ink-faint">{item.date}</span>
                    <span className="font-mono font-medium">{item.ticker}</span>
                    <Chip tone={STATUS_TONES[item.status]}>{incomeStatusLabel(item.status)}</Chip>
                    <span className="ml-auto font-mono text-yield">
                      {formatCurrency(item.net_amount)}
                    </span>
                  </div>
                ))}
              </div>
            </>
          )}
        </Panel>
      </div>

      <div className="mb-5">
        <Panel title="Importar proventos">
          <input
            type="file"
            accept=".xlsx"
            onChange={handleFile}
            disabled={importIncome.isPending}
            className="block w-full text-[13px] text-ink-dim file:mr-3 file:rounded-sm file:border-0 file:bg-surface-2 file:px-3 file:py-1.5 file:text-ink"
          />
          {importIncome.isPending && <p className="mt-2 text-[12.5px] text-ink-dim">Importando…</p>}
          {importIncome.isSuccess && (
            <p className="mt-2 text-[12.5px] text-yield">
              {importIncome.data.length} proventos importados.
            </p>
          )}
          {importIncome.error && (
            <p className="mt-2 text-[12.5px] text-tax">{importIncome.error.message}</p>
          )}
        </Panel>
      </div>

      <h2 className="mb-3 font-cond text-[16px] font-bold">Movimentos</h2>
      {income.isLoading ? (
        <p className="text-[13.5px] text-ink-dim">Carregando…</p>
      ) : income.isError ? (
        <p className="text-[13.5px] text-tax">{income.error.message}</p>
      ) : rows.length === 0 ? (
        <Panel>
          <p className="text-[13px] text-ink-dim">
            Nenhum provento importado. Importe o arquivo &quot;Proventos a Receber&quot; da B3.
          </p>
        </Panel>
      ) : (
        <Table columns={COLUMNS} rows={rows} rowKey={(row) => row.key} />
      )}
    </section>
  );
}
