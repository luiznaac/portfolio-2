import { Link } from "react-router-dom";
import {
  useCloseMonth,
  useClosedMonths,
  useCorporateActions,
  useCurrentMonth,
  useGaps,
  useIncome,
  useMonthPeriodRange,
  useMonthSnapshot,
  useOrders,
  usePortfolioStatus,
} from "../api/queries.ts";
import type { Month, OrdersSnapshot, Snapshot, TransferProposal } from "../api/types.ts";
import { Button } from "../components/Button.tsx";
import { Chip } from "../components/Chip.tsx";
import { CLOSE_NON_BLOCKING_NOTE, CloseGateReason } from "../components/CloseGateReason.tsx";
import { ClosedMonthSelect } from "../components/ClosedMonthSelect.tsx";
import { ClosedMonthStats } from "../components/ClosedMonthStats.tsx";
import { FrozenOrders } from "../components/FrozenOrders.tsx";
import { MonthOpener } from "../components/MonthOpener.tsx";
import { Panel } from "../components/Panel.tsx";
import { type Column, Table } from "../components/Table.tsx";
import { useClosedMonthSelection } from "../hooks/useClosedMonthSelection.ts";
import { monthKey, todayIso } from "../lib/dates.ts";
import {
  type CloseGateView,
  type CloseNonBlockingItem,
  type ClosedMonthSummary,
  closeGateView,
  closeNonBlockingItems,
  closedMonthSummary,
  pendingTransfers,
} from "../lib/fechamento.ts";
import { formatCurrency } from "../lib/format.ts";
import { isBackdatedPeriod, monthPeriodLabel } from "../lib/month.ts";
import { ROUTES, portfolioHistoryDetail } from "../lib/routes.ts";

// Plano / Mês · Fechamentos (issue #391): the close screen. The gate refreshes the plan before it
// decides, so only an undecided Attribution Transfer can refuse the close; every data-quality state
// the snapshot freezes is listed beside it as "não trava". The resulting immutable snapshot and the
// recent closes stay on the same screen.
export function Fechamento() {
  const current = useCurrentMonth();
  const orders = useOrders();
  const closed = useClosedMonths();
  const close = useCloseMonth();
  const gaps = useGaps();
  const corporateActions = useCorporateActions();
  const incomes = useIncome();
  const status = usePortfolioStatus();
  const periodRange = useMonthPeriodRange();

  const months = closed.data ?? [];
  const { monthId, select: selectMonth } = useClosedMonthSelection(months);
  const snapshot = useMonthSnapshot(monthId);

  const pending = pendingTransfers(orders.data);
  // The gate is only trustworthy once the inbox has loaded: disabling while it is in flight keeps a
  // click from racing the pending-transfer check.
  const gateReady = !orders.isLoading && !orders.isError;
  const hasOpenMonth = current.data !== null && current.data !== undefined;
  const backdated =
    current.data !== null &&
    current.data !== undefined &&
    isBackdatedPeriod(current.data.month.period, monthKey(todayIso()));
  const gate = closeGateView({
    hasOpenMonth,
    backdated,
    gateReady,
    pendingCount: pending.length,
  });
  const canClose = gate.canClose && !close.isPending;

  const nonBlocking = closeNonBlockingItems({
    period: current.data?.month.period ?? "",
    orders: orders.data,
    gaps: gaps.data ?? [],
    corporateActions: corporateActions.data ?? [],
    staleQuotes: status.data?.data.stale_quotes ?? [],
    incomes: incomes.data ?? [],
  });

  function handleClose() {
    close.mutate(undefined, {
      onSuccess: (closedSnapshot) => selectMonth(closedSnapshot.month_id),
    });
  }

  return (
    <section data-testid="close-page">
      <div className="mb-4 grid items-start gap-3 lg:grid-cols-2">
        <Panel title="Antes de fechar">
          {current.isLoading || orders.isLoading ? (
            <p className="text-[13.5px] text-ink-dim">Carregando…</p>
          ) : current.isError || orders.isError ? (
            <p className="text-[13.5px] text-tax">
              {(current.error ?? orders.error)?.message ?? "Não foi possível ler o mês."}
            </p>
          ) : current.data === null || current.data === undefined ? (
            periodRange.isError ? (
              <p className="text-[13.5px] text-tax">{periodRange.error.message}</p>
            ) : periodRange.data ? (
              <MonthOpener range={periodRange.data} onOpened={() => {}} />
            ) : (
              <p className="text-[13.5px] text-ink-dim">Carregando…</p>
            )
          ) : (
            <CloseGate
              month={current.data.month}
              gate={gate}
              pending={pending}
              canClose={canClose}
              isClosing={close.isPending}
              error={close.error?.message ?? null}
              onClose={handleClose}
            />
          )}
        </Panel>

        {hasOpenMonth && (
          <Panel title="Não trava o fechamento" action={<Chip tone="neutral">não bloqueia</Chip>}>
            <NonBlockingList items={nonBlocking} />
          </Panel>
        )}
      </div>

      <div className="grid items-start gap-3 lg:grid-cols-2">
        <SummaryPanel
          months={months}
          monthId={monthId}
          snapshot={snapshot.data ?? null}
          isLoading={closed.isLoading || snapshot.isLoading}
          error={snapshot.isError ? snapshot.error.message : null}
          onSelect={selectMonth}
        />
        <RecentCloses months={months.slice(0, 3)} monthId={monthId} onSelect={selectMonth} />
      </div>
    </section>
  );
}

function CloseGate({
  month,
  gate,
  pending,
  canClose,
  isClosing,
  error,
  onClose,
}: {
  month: Month;
  gate: CloseGateView;
  pending: TransferProposal[];
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
        {gate.state === "backdated" && <Chip tone="amber">histórico — sem fechamento</Chip>}
      </div>

      <dl className="mb-3 space-y-1.5 text-[12.5px]">
        <Row label="Capital" value={formatCurrency(month.capital)} />
        <Row label="Piso" value={formatCurrency(month.floor)} />
        <Row label="Aportes" value={formatCurrency(month.aportes)} />
        <div className="flex items-center justify-between gap-3">
          <dt className="text-ink-dim">Transferências</dt>
          <dd className="flex items-center gap-2">
            {gate.state === "checking" ? (
              <Chip>verificando…</Chip>
            ) : pending.length === 0 ? (
              <Chip tone="green">Nenhuma pendente</Chip>
            ) : (
              <>
                <Chip tone="rose">
                  {pending.length} {pending.length === 1 ? "pendente" : "pendentes"}
                </Chip>
                <Link to={ROUTES.planTransfers} className="text-[11.5px] text-principal underline">
                  resolver
                </Link>
              </>
            )}
          </dd>
        </div>
      </dl>

      <CloseGateReason gate={gate} />

      <Button
        variant="primary"
        className="w-full justify-center"
        data-testid="close-month"
        disabled={!canClose}
        onClick={onClose}
      >
        {isClosing
          ? "Fechando…"
          : gate.state === "backdated"
            ? "Só o mês atual pode ser fechado"
            : "Fechar mês"}
      </Button>

      <p className="mt-2 text-[11.5px] text-ink-faint">{CLOSE_NON_BLOCKING_NOTE}</p>

      {error && <p className="mt-2 text-[12.5px] text-tax">{error}</p>}
    </div>
  );
}

function NonBlockingList({ items }: { items: CloseNonBlockingItem[] }) {
  return (
    <div>
      <p className="mb-2 text-[12px] leading-[1.5] text-ink-dim">
        O snapshot segue com estes estados como estão; nenhum deles recusa o fechamento.
      </p>
      <ul className="divide-y divide-line-soft">
        {items.map((item) => (
          <li
            key={item.key}
            data-testid={`non-blocking-${item.key}`}
            className="flex flex-wrap items-center gap-2 py-2 text-[12px]"
          >
            <span className="font-cond font-semibold text-ink">{item.label}</span>
            <span className="font-mono text-ink-dim">{item.detail}</span>
            <span className="flex-1" />
            <Chip tone="neutral">{item.badge}</Chip>
            <Link to={item.to} className="text-[11.5px] text-principal underline">
              ver →
            </Link>
          </li>
        ))}
      </ul>
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
    <Panel title="Resultado do fechamento">
      <div className="mb-3">
        <ClosedMonthSelect months={months} monthId={monthId} onSelect={onSelect} />
      </div>

      {isLoading ? (
        <p className="text-[13.5px] text-ink-dim">Carregando…</p>
      ) : error ? (
        <p className="text-[13.5px] text-tax">{error}</p>
      ) : summary && snapshot ? (
        <ClosedSummary summary={summary} orders={snapshot.state.orders} />
      ) : (
        <p className="text-[13.5px] text-ink-dim">
          Nenhum mês fechado ainda. O resultado aparece aqui quando o primeiro mês for fechado.
        </p>
      )}
    </Panel>
  );
}

function ClosedSummary({
  summary,
  orders,
}: {
  summary: ClosedMonthSummary;
  orders: OrdersSnapshot;
}) {
  return (
    <div data-testid="closed-summary">
      <div className="mb-3 flex flex-wrap items-center gap-2">
        <span className="font-cond text-[15px] font-semibold">
          {monthPeriodLabel(summary.period)}
        </span>
        <Chip tone="green">Fechado</Chip>
        <span className="font-mono text-[11px] text-ink-faint">{summary.closedAt}</span>
        <Link
          to={portfolioHistoryDetail(summary.period)}
          className="ml-auto text-[11.5px] text-principal underline"
        >
          Abrir mês congelado →
        </Link>
      </div>

      <ClosedMonthStats summary={summary} />

      <div className="mt-3">
        <FrozenOrders orders={orders} testId="frozen-orders" title="Plano congelado" />
      </div>
    </div>
  );
}

function RecentCloses({
  months,
  monthId,
  onSelect,
}: {
  months: Month[];
  monthId: number | null;
  onSelect: (monthId: number) => void;
}) {
  return (
    <Panel
      title="Fechamentos guardados"
      action={
        <Link to={ROUTES.portfolioHistory} className="text-[11.5px] text-principal underline">
          Ver todos →
        </Link>
      }
    >
      <div data-testid="recent-closes">
        {months.length === 0 ? (
          <p className="text-[13px] text-ink-dim">
            Nenhum mês fechado ainda. Feche o mês aberto para começar a história.
          </p>
        ) : (
          <Table
            columns={closedMonthColumns(monthId, onSelect)}
            rows={months}
            rowKey={(month) => month.id}
          />
        )}
      </div>
    </Panel>
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
