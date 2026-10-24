import { Link } from "react-router-dom";
import {
  useBonds,
  useClosedMonths,
  useCurrentAllocation,
  useCurrentMonth,
  useEditionCoverage,
  useIncome,
  useOrderPlan,
  useOrders,
  useStrategies,
  useTrades,
  useUpcomingIncome,
} from "../api/queries.ts";
import type { Income } from "../api/types.ts";
import { Agenda } from "../components/Agenda.tsx";
import { AllocationStrip } from "../components/AllocationStrip.tsx";
import { Kpi } from "../components/Kpi.tsx";
import { Panel } from "../components/Panel.tsx";
import { RitualStepper } from "../components/RitualStepper.tsx";
import { agendaItems } from "../lib/agenda.ts";
import { cn } from "../lib/cn.ts";
import {
  type AttentionItem,
  allocationStrip,
  attentionItems,
  cockpitKpis,
  editionCoverage,
  ritualSteps,
  upcomingIncome,
} from "../lib/cockpit.ts";
import { todayIso } from "../lib/dates.ts";
import { formatCurrency, formatIsoDate } from "../lib/format.ts";
import { incomeTypeLabel } from "../lib/income.ts";
import { monthPeriodLabel, monthStatusLabel } from "../lib/month.ts";

// The Cockpit (issue #26/#177): where the month is and what to do now. Every figure is derived on
// read from the same month-scoped queries the other screens use — no cockpit-specific endpoint —
// and the pure rules live in lib/cockpit.ts (steps, attention, strip) and lib/agenda.ts.
export function Cockpit() {
  const current = useCurrentMonth();
  const allocation = useCurrentAllocation();
  const orders = useOrders();
  const plan = useOrderPlan();
  const trades = useTrades();
  const strategies = useStrategies();
  const income = useIncome();
  const upcoming = useUpcomingIncome();
  const bonds = useBonds();
  const closed = useClosedMonths();
  const editions = useEditionCoverage(strategies.data ?? [], current.data?.month.period);

  const today = todayIso();
  const month = current.data?.month ?? null;
  const agenda = agendaItems(bonds.data ?? [], month, closed.data ?? [], today);
  const upcomingRows = upcomingIncome(upcoming.data ?? [], today);

  if (current.isLoading) {
    return <p className="text-[13.5px] text-ink-dim">Carregando…</p>;
  }

  if (current.isError) {
    return <p className="text-[13.5px] text-tax">{current.error.message}</p>;
  }

  if (month === null) {
    return (
      <section>
        <CockpitHead period={null} />
        <Panel title="Nenhum mês aberto">
          <p className="text-[13.5px] text-ink-dim">
            O ritual mensal começa quando um mês é aberto.{" "}
            <Link to="/month" className="text-principal underline">
              Abrir mês
            </Link>{" "}
            para definir Capital, reserva e alvos.
          </p>
        </Panel>

        <div className="mt-4 grid items-start gap-4 lg:grid-cols-2">
          <Panel title="Agenda">
            <Agenda items={agenda} />
          </Panel>
          <UpcomingPanel rows={upcomingRows} />
        </div>
      </section>
    );
  }

  const coverage = editionCoverage(
    strategies.data ?? [],
    editions.map((result) => result.data),
    month.period,
  );

  const kpis = cockpitKpis(month, allocation.data ?? null);
  const steps = ritualSteps({
    month,
    orders: orders.data ?? null,
    trades: trades.data ?? [],
    coverage,
    income: income.data ?? [],
    upcoming: upcoming.data ?? [],
  });
  const attention = attentionItems({
    allocation: allocation.data ?? null,
    plan: plan.data ?? null,
  });
  const segments = allocationStrip(allocation.data ?? null);
  const attentionLoading = allocation.isLoading || plan.isLoading;
  const attentionError = allocation.isError || plan.isError;

  return (
    <section>
      <CockpitHead period={month.period} />

      <div className="mb-4 grid grid-cols-2 gap-3 lg:grid-cols-4">
        <Kpi
          testId="cockpit-capital"
          label="Capital"
          value={formatCurrency(kpis.capital)}
          hint="base do mês"
        />
        <Kpi
          testId="cockpit-caixa"
          label="Caixa"
          value={kpis.caixa === undefined ? undefined : formatCurrency(kpis.caixa)}
          hint="residual (Capital − posições)"
          tone={kpis.caixa !== undefined && kpis.caixa < 0 ? "loss" : "default"}
        />
        <Kpi
          testId="cockpit-investable"
          label="Capital investível"
          value={formatCurrency(kpis.investable)}
          hint="Capital − reserva"
          tone={kpis.investable < 0 ? "loss" : "default"}
        />
        <Kpi
          testId="cockpit-status"
          label="Status"
          value={monthStatusLabel(kpis.status)}
          hint={kpis.status === "OPEN" ? "ritual em andamento" : "mês encerrado"}
        />
      </div>

      <div className="grid items-start gap-4 lg:grid-cols-[1.6fr_1fr]">
        <Panel title={`Fechamento de ${monthPeriodLabel(month.period)}`}>
          <RitualStepper steps={steps} />
        </Panel>

        <div className="flex flex-col gap-4">
          <Panel title="Precisa de atenção">
            <div data-testid="cockpit-attention">
              {attentionLoading ? (
                <p className="text-[13px] text-ink-dim">Carregando…</p>
              ) : attentionError ? (
                <p className="text-[13px] text-tax">Não foi possível ler a alocação e o plano.</p>
              ) : attention.length === 0 ? (
                <p className="text-[13px] text-yield">Nada precisa de atenção.</p>
              ) : (
                <div className="flex flex-col gap-2.5">
                  {attention.map((item) => (
                    <AttentionCard key={item.key} item={item} />
                  ))}
                </div>
              )}
            </div>
          </Panel>

          <Panel title="Alocação atual × alvo">
            <AllocationStrip segments={segments} />
          </Panel>

          <UpcomingPanel rows={upcomingRows} />
        </div>
      </div>

      <div className="mt-4">
        <Panel title="Agenda">
          <Agenda items={agenda} />
        </Panel>
      </div>
    </section>
  );
}

function CockpitHead({ period }: { period: string | null }) {
  return (
    <div className="mb-4">
      <h1 className="font-cond text-[24px] font-bold tracking-[.005em]">
        {period === null ? "Cockpit" : `Cockpit de ${monthPeriodLabel(period)}`}
      </h1>
      <p className="text-[13px] text-ink-dim">
        Onde o mês está e o que fazer agora. O ritual tem 7 passos; quatro deles são decisões suas.
      </p>
    </div>
  );
}

function AttentionCard({ item }: { item: AttentionItem }) {
  const tone =
    item.severity === "critical"
      ? "border-l-tax text-tax"
      : item.severity === "warning"
        ? "border-l-accent text-accent"
        : "border-l-principal text-principal";
  const mark = item.severity === "critical" ? "◆" : item.severity === "warning" ? "▲" : "●";

  return (
    <div
      className={cn(
        "grid grid-cols-[auto_1fr] gap-2.5 rounded-md border-l-[3px] bg-surface-2 px-3 py-2.5 text-[12.5px]",
        tone,
      )}
    >
      <span>{mark}</span>
      <div className="text-ink">
        {item.text}{" "}
        <Link to={item.to} className="font-medium text-principal underline">
          Ver
        </Link>
      </div>
    </div>
  );
}

function UpcomingPanel({ rows }: { rows: Income[] }) {
  return (
    <Panel title="Próximos proventos">
      <div data-testid="upcoming-proventos">
        {rows.length === 0 ? (
          <p className="text-[13px] text-ink-dim">Nenhum provento nos próximos 30 dias.</p>
        ) : (
          <table className="w-full border-collapse text-[12.5px]">
            <tbody>
              {rows.map((row) => (
                <tr key={row.id} className="border-t border-line-soft first:border-t-0">
                  <td className="py-1.5 font-mono font-medium">{row.ticker}</td>
                  <td className="py-1.5 text-ink-dim">{incomeTypeLabel(row.type)}</td>
                  <td className="py-1.5 font-mono text-[11.5px] text-ink-faint">
                    {formatIsoDate(row.date)}
                  </td>
                  <td className="py-1.5 text-right font-mono text-yield">
                    {formatCurrency(row.net_amount)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </Panel>
  );
}
