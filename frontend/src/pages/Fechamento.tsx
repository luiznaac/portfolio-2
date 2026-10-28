import { Link } from "react-router-dom";
import {
  useCloseMonth,
  useClosedMonths,
  useCurrentMonth,
  useMonthSnapshot,
  useOrders,
} from "../api/queries.ts";
import type { Month, Snapshot } from "../api/types.ts";
import { Button } from "../components/Button.tsx";
import { Chip } from "../components/Chip.tsx";
import { ClosedMonthSelect } from "../components/ClosedMonthSelect.tsx";
import { Kpi } from "../components/Kpi.tsx";
import { Panel } from "../components/Panel.tsx";
import { type Column, Table } from "../components/Table.tsx";
import { useClosedMonthSelection } from "../hooks/useClosedMonthSelection.ts";
import {
  type ClosedMonthSummary,
  closeButtonLabel,
  closedMonthSummary,
  pendingTransfers,
} from "../lib/fechamento.ts";
import { formatCurrency, formatNumber } from "../lib/format.ts";
import { monthPeriodLabel } from "../lib/month.ts";

// Fechamento (issue #176): the month's last ritual. The close is gated by the transfer inbox — a
// pending Attribution Transfer blocks it — and the frozen snapshot of every closed month is the
// read-only history the summary and its selector open on.
export function Fechamento() {
  const current = useCurrentMonth();
  const orders = useOrders();
  const closed = useClosedMonths();
  const close = useCloseMonth();
  const months = closed.data ?? [];
  const { monthId, select: selectMonth } = useClosedMonthSelection(months);
  const snapshot = useMonthSnapshot(monthId);
  const pending = pendingTransfers(orders.data);

  // The gate is only trustworthy once the inbox has loaded: disabling while it is in flight keeps a
  // click from racing the pending-transfer check.
  const gateReady = !orders.isLoading && !orders.isError;
  const hasOpenMonth = current.data !== null && current.data !== undefined;
  const canClose = hasOpenMonth && gateReady && pending.length === 0 && !close.isPending;

  function handleClose() {
    close.mutate(undefined, {
      onSuccess: (closedSnapshot) => selectMonth(closedSnapshot.month_id),
    });
  }

  return (
    <section>
      <div className="mb-4">
        <h1 className="font-cond text-[24px] font-bold tracking-[.005em]">Fechamento</h1>
        <p className="text-[13px] text-ink-dim">
          O último passo do mês: o plano é recalculado, as transferências precisam estar decididas e
          o estado do mês é congelado num retrato imutável.
        </p>
      </div>

      <div className="mb-4 grid items-start gap-3 lg:grid-cols-2">
        <Panel title="Antes de fechar">
          {current.isLoading || orders.isLoading ? (
            <p className="text-[13.5px] text-ink-dim">Carregando…</p>
          ) : current.isError || orders.isError ? (
            <p className="text-[13.5px] text-tax">
              {(current.error ?? orders.error)?.message ?? "Não foi possível ler o mês."}
            </p>
          ) : current.data === null || current.data === undefined ? (
            <p className="text-[13.5px] text-ink-dim">
              Nenhum mês aberto.{" "}
              <Link to="/month" className="text-principal underline">
                Abra um mês
              </Link>{" "}
              para fechá-lo.
            </p>
          ) : (
            <CloseGate
              month={current.data.month}
              pendingCount={pending.length}
              gateReady={gateReady}
              canClose={canClose}
              isClosing={close.isPending}
              error={close.error?.message ?? null}
              onClose={handleClose}
            />
          )}
        </Panel>

        <SummaryPanel
          months={months}
          monthId={monthId}
          snapshot={snapshot.data ?? null}
          isLoading={closed.isLoading || snapshot.isLoading}
          error={snapshot.isError ? snapshot.error.message : null}
          onSelect={selectMonth}
        />
      </div>

      <h2 className="mb-2 font-cond text-[13px] font-bold tracking-[.04em] text-ink-dim uppercase">
        Meses fechados
      </h2>
      {months.length === 0 ? (
        <Panel>
          <p className="text-[13px] text-ink-dim">
            Nenhum mês fechado ainda. Feche o mês aberto para começar a história.
          </p>
        </Panel>
      ) : (
        <Table
          columns={closedMonthColumns(monthId, selectMonth)}
          rows={months}
          rowKey={(month) => month.id}
        />
      )}
    </section>
  );
}

function CloseGate({
  month,
  pendingCount,
  gateReady,
  canClose,
  isClosing,
  error,
  onClose,
}: {
  month: Month;
  pendingCount: number;
  gateReady: boolean;
  canClose: boolean;
  isClosing: boolean;
  error: string | null;
  onClose: () => void;
}) {
  return (
    <div data-testid="close-gate">
      <div className="mb-3 flex flex-wrap items-baseline gap-2">
        <span className="font-cond text-[15px] font-semibold">
          {monthPeriodLabel(month.period)}
        </span>
        <Chip tone="blue">Aberto</Chip>
      </div>

      <dl className="mb-4 space-y-1.5 text-[12.5px]">
        <Row label="Capital" value={formatCurrency(month.capital)} />
        <Row label="Piso" value={formatCurrency(month.floor)} />
        <Row label="Aportes" value={formatCurrency(month.aportes)} />
        <div className="flex items-center justify-between gap-3">
          <dt className="text-ink-dim">Transferências</dt>
          <dd className="flex items-center gap-2">
            {!gateReady ? (
              <Chip>verificando…</Chip>
            ) : pendingCount === 0 ? (
              <Chip tone="green">Nenhuma pendente</Chip>
            ) : (
              <>
                <Chip tone="rose">
                  {pendingCount} {pendingCount === 1 ? "pendente" : "pendentes"}
                </Chip>
                <Link to="/orders" className="text-[11.5px] text-principal underline">
                  resolver
                </Link>
              </>
            )}
          </dd>
        </div>
      </dl>

      <Button
        variant="primary"
        className="w-full justify-center"
        data-testid="close-month"
        disabled={!canClose}
        onClick={onClose}
      >
        {isClosing ? "Fechando…" : closeButtonLabel(pendingCount)}
      </Button>

      <p className="mt-2 text-[11.5px] text-ink-faint">
        Fechar recalcula o plano de ordens e congela posições, proventos, impostos e o plano no
        retrato do mês.
      </p>

      {error && <p className="mt-2 text-[12.5px] text-tax">{error}</p>}
    </div>
  );
}

function SummaryPanel({
  months,
  monthId,
  snapshot,
  isLoading,
  error,
  onSelect,
}: {
  months: Month[];
  monthId: number | null;
  snapshot: Snapshot | null;
  isLoading: boolean;
  error: string | null;
  onSelect: (monthId: number) => void;
}) {
  const summary = snapshot ? closedMonthSummary(snapshot) : null;

  return (
    <Panel title="Mês fechado">
      <div className="mb-3">
        <ClosedMonthSelect months={months} monthId={monthId} onSelect={onSelect} />
      </div>

      {isLoading ? (
        <p className="text-[13.5px] text-ink-dim">Carregando…</p>
      ) : error ? (
        <p className="text-[13.5px] text-tax">{error}</p>
      ) : summary ? (
        <ClosedSummary summary={summary} />
      ) : (
        <p className="text-[13.5px] text-ink-dim">
          Nenhum mês fechado ainda. O resumo aparece aqui quando o primeiro mês for fechado.
        </p>
      )}
    </Panel>
  );
}

function ClosedSummary({ summary }: { summary: ClosedMonthSummary }) {
  return (
    <div data-testid="closed-summary">
      <div className="mb-3 flex flex-wrap items-center gap-2">
        <span className="font-cond text-[15px] font-semibold">
          {monthPeriodLabel(summary.period)}
        </span>
        <Chip tone="green">Fechado</Chip>
        <span className="ml-auto font-mono text-[11px] text-ink-faint">{summary.closedAt}</span>
      </div>

      <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
        <Kpi label="Capital" value={formatCurrency(summary.capital)} />
        <Kpi label="Piso" value={formatCurrency(summary.floor)} />
        <Kpi label="Caixa" value={formatCurrency(summary.caixa)} />
        <Kpi label="Proventos líquidos" value={formatCurrency(summary.incomeNet)} />
        <Kpi label="Ordens executadas" value={formatNumber(summary.executedOrders)} />
      </div>
    </div>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between gap-3">
      <dt className="text-ink-dim">{label}</dt>
      <dd className="font-mono text-ink">{value}</dd>
    </div>
  );
}

function closedMonthColumns(
  selectedMonthId: number | null,
  onSelect: (monthId: number) => void,
): Column<Month>[] {
  return [
    {
      key: "period",
      header: "Mês",
      render: (month) => (
        <span className="inline-flex items-center gap-2">
          <span className="font-mono text-[12.5px] text-ink">{monthPeriodLabel(month.period)}</span>
          {month.id === selectedMonthId && <Chip tone="green">selecionado</Chip>}
        </span>
      ),
    },
    {
      key: "capital",
      header: "Capital",
      align: "right",
      render: (month) => <span className="font-mono">{formatCurrency(month.capital)}</span>,
    },
    {
      key: "floor",
      header: "Piso",
      align: "right",
      render: (month) => <span className="font-mono">{formatCurrency(month.floor)}</span>,
    },
    {
      key: "aportes",
      header: "Aportes",
      align: "right",
      render: (month) => <span className="font-mono">{formatCurrency(month.aportes)}</span>,
    },
    {
      key: "select",
      header: "",
      align: "right",
      render: (month) => (
        <Button
          variant="ghost"
          className="px-2 py-0.5 text-[11.5px]"
          onClick={() => onSelect(month.id)}
        >
          Ver resumo
        </Button>
      ),
    },
  ];
}
