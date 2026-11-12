import { type ChangeEvent, useEffect, useRef } from "react";
import { useSearchParams } from "react-router-dom";
import { useClosedMonths, useImportIncome, useIncome } from "../api/queries.ts";
import type { IncomeStatus } from "../api/types.ts";
import { Chip, type ChipTone } from "../components/Chip.tsx";
import { FilterBar } from "../components/FilterBar.tsx";
import { IncomeChart } from "../components/IncomeChart.tsx";
import { Kpi } from "../components/Kpi.tsx";
import { Panel } from "../components/Panel.tsx";
import { PeriodRail } from "../components/PeriodRail.tsx";
import { type Column, Table } from "../components/Table.tsx";
import { useAnalysisWindow } from "../hooks/useAnalysisWindow.tsx";
import { usePageFilters } from "../hooks/usePageFilters.ts";
import { windowDayRange, windowRangeLabel } from "../lib/analysisWindow.ts";
import { monthKey } from "../lib/dates.ts";
import { hasActiveFilter } from "../lib/filters.ts";
import { formatCurrency, formatIsoDate } from "../lib/format.ts";
import {
  INCOME_STATUSES,
  type IncomeLedgerRow,
  incomeLedgerRows,
  incomeRows,
  incomeWindowView,
  isOpenIncome,
} from "../lib/income.ts";
import { INCOME_FILTER_FIELDS, filterIncome } from "../lib/incomeFilters.ts";

// The lifecycle stage maps to the reference sign palette (Chip names a tone, never a colour): a
// forecast is neutral/blue, a settled payment green, a reconciled one violet.
const STATUS_TONES: Record<IncomeStatus, ChipTone> = {
  ANNOUNCED: "neutral",
  PAYABLE: "blue",
  RECEIVED: "green",
  RECONCILED: "violet",
};

const LEDGER_COLUMNS: Column<IncomeLedgerRow>[] = [
  {
    key: "ticker",
    header: "Ticker",
    render: (row) => (
      <span className="flex items-center gap-2">
        <span className="font-mono font-medium">{row.ticker}</span>
        {row.focused && (
          <Chip tone="amber" testId="income-focus-chip">
            do alerta
          </Chip>
        )}
      </span>
    ),
  },
  { key: "type", header: "Tipo", render: (row) => row.typeLabel },
  {
    key: "date",
    header: "Data",
    render: (row) => (
      <span className="flex items-center gap-2">
        <span className="font-mono text-[11.5px] text-ink-dim">{formatIsoDate(row.date)}</span>
        {row.outsideWindow && (
          <Chip tone="amber" testId={`outside-window-${row.key}`}>
            fora do recorte
          </Chip>
        )}
      </span>
    ),
  },
  {
    key: "status",
    header: "Estado",
    render: (row) => <Chip tone={STATUS_TONES[row.status]}>{row.statusLabel}</Chip>,
  },
  {
    key: "retained",
    header: "Retenção",
    align: "right",
    render: (row) =>
      row.retained > 0 ? (
        <span className="font-mono text-tax">−{formatCurrency(row.retained)}</span>
      ) : (
        <span className="text-ink-faint">—</span>
      ),
  },
  {
    key: "net",
    header: "Líquido",
    align: "right",
    render: (row) => (
      <span className="font-mono font-medium text-yield">{formatCurrency(row.net)}</span>
    ),
  },
];

// Análise · Proventos (issue #393): a window read over the shared period rail. The KPIs and the
// monthly chart are window-level; the ledger reads "a receber" first (forward-looking, wherever it
// falls) and then the settled rows of the window. The page's ativo/estado filter narrows only the
// ledger, through the additive GET /income parameters, never the KPIs or the rail.
export function Income() {
  const income = useIncome();
  const closed = useClosedMonths();
  const filters = usePageFilters(INCOME_FILTER_FIELDS);
  const [searchParams] = useSearchParams();
  const focusId = parseFocus(searchParams.get("focus"));

  const all = income.data ?? [];
  const periods = [
    ...new Set([
      ...all.map((movement) => monthKey(movement.date)),
      ...(closed.data ?? []).map((month) => month.period),
    ]),
  ].sort();
  const focusMovement =
    focusId === undefined ? undefined : all.find((movement) => movement.id === focusId);
  // A deep link carries the target row's id; landing in "the right window" is a preferred anchor on
  // that row's month, used until the owner moves the rail.
  const focusMonth = focusMovement === undefined ? undefined : monthKey(focusMovement.date);
  const rail = useAnalysisWindow(periods, closed.data?.[0]?.period, focusMonth);
  const range = rail.range;

  // A deep link lands on a row that may sit below the fold once the ledger renders. This is a
  // one-time DOM landing (not derived state), so the target row is scrolled into view the first
  // time its element exists; later data refreshes must not pull the owner's scroll position back.
  const scrolledFocus = useRef<number | null>(null);
  useEffect(() => {
    if (focusId === undefined || all.length === 0 || scrolledFocus.current === focusId) return;
    const element = document.getElementById(`income-row-${focusId}`);
    if (element === null) return;
    scrolledFocus.current = focusId;
    element.scrollIntoView?.({ block: "center" });
  }, [focusId, all.length]);

  const dayRange = range === undefined ? undefined : windowDayRange(range);

  const ticker = filters.values.ticker?.trim();
  const narrowing = hasActiveFilter(filters.values);
  const filtered = useIncome(
    {
      from: dayRange?.from,
      to: dayRange?.to,
      ticker: ticker === undefined || ticker.length === 0 ? undefined : ticker,
      status: statusOf(filters.values.status),
    },
    narrowing && range !== undefined,
  );

  if (income.isLoading) {
    return (
      <section>
        <p className="text-[13.5px] text-ink-dim">Carregando…</p>
      </section>
    );
  }

  if (income.isError) {
    return (
      <section>
        <p className="text-[13.5px] text-tax">{income.error.message}</p>
      </section>
    );
  }

  if (range === undefined || rail.anchor === undefined) {
    return (
      <section>
        <Panel title="Sem proventos">
          <p className="text-[13px] text-ink-dim">
            Nenhum provento importado. O livro nasce do arquivo &quot;Proventos a Receber&quot; da
            B3.
          </p>
        </Panel>
        <div className="mt-4">
          <IncomeImportPanel />
        </div>
      </section>
    );
  }

  const view = incomeWindowView(all, range);
  const receivedRows = incomeRows(
    (narrowing ? (filtered.data ?? []) : view.received).filter(
      (movement) => !isOpenIncome(movement.status),
    ),
  );
  const openRows = filterIncome(incomeRows(view.open), filters.values);
  const ledger = incomeLedgerRows(openRows, receivedRows, range, focusId);
  const total = view.open.length + view.received.length;
  const next = view.next;

  return (
    <section>
      <PeriodRail
        preset={rail.preset}
        anchor={rail.anchor}
        range={range}
        bounds={rail.bounds}
        onPreset={rail.setPreset}
        onShift={rail.shift}
      />

      <div className="mb-4 grid grid-cols-2 gap-3 lg:grid-cols-4">
        <Kpi
          testId="income-received-kpi"
          label="Recebidos no período"
          value={formatCurrency(view.receivedTotal)}
          hint={`${view.received.length} ${view.received.length === 1 ? "lançamento" : "lançamentos"}`}
        />
        <Kpi
          testId="income-to-receive-kpi"
          label="A receber"
          value={formatCurrency(view.toReceiveTotal)}
          hint={`${view.open.length} ${view.open.length === 1 ? "linha em aberto" : "linhas em aberto"}${
            view.upcomingOutsideRange ? " · fora do recorte" : ""
          }`}
        />
        <Kpi
          testId="income-next-kpi"
          label="Próximo"
          value={next?.ticker ?? "—"}
          hint={
            next === undefined
              ? "nada a receber"
              : `${formatCurrency(next.net_amount)} em ${formatIsoDate(next.date)}`
          }
        />
        <Kpi
          testId="income-monthly-kpi"
          label="Média mensal"
          value={formatCurrency(view.receivedTotal / Math.max(view.monthsInWindow, 1))}
          hint={`${view.monthsInWindow} ${view.monthsInWindow === 1 ? "mês" : "meses"} no recorte`}
        />
      </div>

      <div className="mb-4">
        <Panel
          title="Proventos líquidos por mês"
          action={
            <span className="font-mono text-[10.5px] text-faint">{windowRangeLabel(range)}</span>
          }
        >
          <IncomeChart months={view.byMonth} />
          <p className="pt-1 text-[11.5px] text-ink-faint">
            Rendimentos de FII, dividendos e JCP, líquidos de retenção, no mês da sua data de
            pagamento. As linhas ainda em aberto aparecem no livro mesmo quando vencem fora do
            recorte.
          </p>
        </Panel>
      </div>

      <div className="mb-2 flex flex-wrap items-baseline gap-3">
        <h2 className="font-cond text-[13px] font-bold tracking-[.04em] text-ink-dim uppercase">
          Livro do período
        </h2>
        <span className="text-[11.5px] text-ink-faint">
          a receber primeiro · recebidos no recorte
        </span>
      </div>

      {narrowing && filtered.isError && (
        <p className="mb-2 text-[12.5px] text-tax">{filtered.error.message}</p>
      )}

      {total === 0 ? (
        <Panel>
          <p className="text-[13px] text-ink-dim">Nenhum provento no período.</p>
        </Panel>
      ) : (
        <FilterBar
          fields={INCOME_FILTER_FIELDS}
          values={filters.values}
          onChange={filters.setFilter}
          onClear={filters.clear}
          shown={ledger.length}
          total={total}
        >
          <Table
            columns={LEDGER_COLUMNS}
            rows={ledger}
            rowKey={(row) => row.key}
            rowId={(row) => `income-row-${row.key}`}
            rowClassName={(row) => (row.focused ? "bg-attention-bg" : undefined)}
          />
        </FilterBar>
      )}

      <div className="mt-4">
        <IncomeImportPanel />
      </div>
    </section>
  );
}

// The B3 "Proventos a Receber" import keeps a direct entry here until the import assistant carries
// the income source; the ledger read above is unaffected by the write.
function IncomeImportPanel() {
  const importIncome = useImportIncome();

  function handleFile(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (file) importIncome.mutate(file);
    event.target.value = "";
  }

  return (
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
  );
}

function parseFocus(raw: string | null): number | undefined {
  if (raw === null) return undefined;
  const parsed = Number(raw);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : undefined;
}

function statusOf(value: string | undefined): IncomeStatus | undefined {
  return INCOME_STATUSES.find((status) => status === value);
}
