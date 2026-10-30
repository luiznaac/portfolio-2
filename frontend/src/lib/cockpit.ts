// Pure Cockpit derivations — no React imports here (see AGENTS.md). The page reads the queries and
// this module turns them into the four KPI figures, the seven-step ritual, the attention cards, the
// class strip and the 30-day proventos window, so each rule is unit-tested without a browser.

import type {
  Allocation,
  AssetClass,
  Income,
  Month,
  MonthStatus,
  OrderList,
  OrderPlan,
  Trade,
} from "../api/types.ts";
import { formatPoints } from "./allocation.ts";
import { dataQualityCounts, dataQualitySummary } from "./dataQuality.ts";
import { addDays, monthKey } from "./dates.ts";
import { pendingTransfers } from "./fechamento.ts";
import { formatCurrency } from "./format.ts";
import { netTotal } from "./income.ts";
import { outsideCustody, shouldShowBanner } from "./legacyAttribution.ts";
import { assetClassLabel, monthPeriodLabel } from "./month.ts";

function roundCents(value: number): number {
  return Math.round(value * 100) / 100;
}

export interface CockpitKpis {
  capital: number;
  // The Uninvested Balance: Capital minus the value of every position. Undefined until the
  // allocation read lands; that is "unknown", not zero.
  caixa?: number;
  // Capital minus the cash Floor — the amount every Ideal scales.
  investable: number;
  status: MonthStatus;
}

export function cockpitKpis(month: Month, allocation: Allocation | null): CockpitKpis {
  // A partial allocation excludes unpriced custody, so Capital − Actual would overstate cash: the
  // residual is unknown until every held ticker has a close.
  const caixa =
    allocation === null || allocation.partial
      ? undefined
      : roundCents(month.capital - allocation.actual);
  return {
    capital: month.capital,
    caixa,
    investable: roundCents(month.capital - month.floor),
    status: month.status,
  };
}

export type StepStatus = "complete" | "pending" | "attention";

export interface RitualStep {
  number: number;
  key: string;
  title: string;
  detail: string;
  status: StepStatus;
  optional?: boolean;
  // True when this step is the reason the month cannot close.
  blocking?: boolean;
  to: string;
  actionLabel: string;
}

export interface StrategyCoverage {
  name: string;
  hasEdition: boolean;
}

export interface RitualInput {
  month: Month;
  orders: OrderList | null;
  trades: Trade[];
  coverage: StrategyCoverage[];
  income: Income[];
  upcoming: Income[];
}

// A plan is persisted only once Recalcular wrote something: the /orders read answers with an empty
// shape for any open month, so the read's own emptiness is the signal Orders.tsx also uses. A
// perfectly balanced month is indistinguishable from an unrecalculated one (the DTO carries no
// plan marker), so its steps stay pending and Recalcular remains the action.
function hasPersistedPlan(orders: OrderList | null, month: Month): boolean {
  return (
    orders !== null &&
    orders.period === month.period &&
    (orders.orders.length > 0 || orders.transfers.length > 0 || orders.unplanned_trades.length > 0)
  );
}

// The seven steps of the monthly ritual, in the order they run. Each status is derived from what
// the book already holds: a step is complete when its artefact exists for the open month, attention
// when it is a decision waiting on the owner, pending when it has not happened yet.
export function ritualSteps(input: RitualInput): RitualStep[] {
  const { month, orders, trades, coverage, income, upcoming } = input;
  const pending = pendingTransfers(orders);
  const persisted = hasPersistedPlan(orders, month);
  const orderCount = persisted ? (orders?.orders.length ?? 0) : 0;
  const importedTrades = trades.filter(
    (trade) => trade.source === "B3_IMPORT" && monthKey(trade.date) === month.period,
  );
  const monthIncome = income.filter((row) => monthKey(row.date) === month.period);
  const reconciled = monthIncome.filter((row) => row.status === "RECONCILED").length;
  const editions = coverage.filter((strategy) => strategy.hasEdition).length;
  const forecast = netTotal(upcoming);

  const editionStep: RitualStep = {
    number: 1,
    key: "editions",
    title: "Registrar edições da XP",
    detail:
      coverage.length === 0
        ? "Nenhuma estratégia com relatório"
        : `${editions} de ${coverage.length} estratégias com edição de ${monthPeriodLabel(month.period)}`,
    // A legacy-only book has nothing to import, so the step is complete rather than waiting on a
    // report that will never exist.
    status: editions === coverage.length ? "complete" : "pending",
    to: "/strategies",
    actionLabel: "Importar relatórios",
  };

  const recalculateStep: RitualStep = {
    number: 2,
    key: "recalculate",
    title: "Recalcular o plano de ordens",
    detail: persisted
      ? `Plano de ${monthPeriodLabel(month.period)} persistido`
      : "Plano ainda não recalculado",
    status: persisted ? "complete" : "pending",
    to: "/orders",
    actionLabel: "Recalcular",
  };

  const transfersStep: RitualStep = {
    number: 3,
    key: "transfers",
    title: "Decidir as transferências",
    detail: !persisted
      ? "O plano ainda não foi recalculado"
      : pending.length === 0
        ? "Todas decididas"
        : `${pending.length} transferências pendentes`,
    status: !persisted ? "pending" : pending.length === 0 ? "complete" : "attention",
    to: "/orders",
    actionLabel: "Decidir",
  };

  const ordersStep: RitualStep = {
    number: 4,
    key: "orders",
    title: "Gerar a lista de ordens",
    detail: persisted ? `${orderCount} ordens na lista` : "A lista nasce do plano recalculado",
    status: persisted && pending.length === 0 ? "complete" : "pending",
    to: "/orders",
    actionLabel: "Ver ordens",
  };

  const importStep: RitualStep = {
    number: 5,
    key: "import",
    title: "Importar o extrato B3",
    detail:
      importedTrades.length === 0
        ? "Nenhum negócio do mês importado"
        : `${importedTrades.length} negócios importados`,
    status: importedTrades.length > 0 ? "complete" : "pending",
    to: "/imports",
    actionLabel: "Importar extrato",
  };

  const incomeStep: RitualStep = {
    number: 6,
    key: "income",
    title: "Conciliar proventos",
    detail:
      monthIncome.length === 0
        ? `Previsto ${formatCurrency(forecast)}`
        : `${reconciled} de ${monthIncome.length} conciliados`,
    status: monthIncome.length > 0 && reconciled === monthIncome.length ? "complete" : "pending",
    optional: true,
    to: "/income",
    actionLabel: "Conciliar",
  };

  const closeBlocked = pending.length > 0;
  const closeStep: RitualStep = {
    number: 7,
    key: "close",
    title: "Fechar o mês",
    detail: closeBlocked
      ? `Bloqueado: ${pending.length} ${pending.length === 1 ? "transferência pendente" : "transferências pendentes"}`
      : "Pronto para fechar",
    status: month.status === "CLOSED" ? "complete" : closeBlocked ? "attention" : "pending",
    blocking: closeBlocked,
    to: "/fechamento",
    actionLabel: "Fechar o mês",
  };

  return [
    editionStep,
    recalculateStep,
    transfersStep,
    ordersStep,
    importStep,
    incomeStep,
    closeStep,
  ];
}

export type AttentionSeverity = "critical" | "warning" | "info";

export interface AttentionItem {
  key: string;
  severity: AttentionSeverity;
  text: string;
  to: string;
  // The link's verb, so a planning act ("Atribuir") and a data fix ("Classificar") do not share one.
  actionLabel?: string;
}

export interface AttentionInput {
  allocation: Allocation | null;
  plan: OrderPlan | null;
}

// Drift is in percentage points; a full point away from target is worth surfacing.
const DRIFT_THRESHOLD = 1;

// The "Precisa de atenção" cards: the concrete things that would make the plan wrong or blocked,
// ordered by how badly they hurt (critical → warning → info).
export function attentionItems(input: AttentionInput): AttentionItem[] {
  const items: AttentionItem[] = [];
  const { allocation, plan } = input;

  // Every data-quality problem collapses into one card pointing at the "Dados da carteira" panel:
  // unpriced, unclassified and negative custody share one fix surface, so the Cockpit does not
  // raise one interruption per problem. The bucket never appears: its units are attributed.
  const quality = dataQualityCounts(allocation);
  if (quality.total > 0) {
    items.push({
      key: "data-quality",
      severity: "critical",
      text: `Dados da carteira: ${dataQualitySummary(quality)}`,
      to: "/allocation",
      actionLabel: "Revisar",
    });
  }

  const classes = allocation?.classes ?? [];
  const worst = classes.reduce<Allocation["classes"][number] | null>(
    (current, rung) =>
      current === null || Math.abs(rung.drift) > Math.abs(current.drift) ? rung : current,
    null,
  );
  if (worst !== null && Math.abs(worst.drift) > DRIFT_THRESHOLD) {
    items.push({
      key: "drift",
      severity: "warning",
      text: `Maior desvio: ${assetClassLabel(worst.asset_class)} ${formatPoints(worst.drift)}`,
      to: "/allocation",
    });
  }

  const funding = plan?.funding;
  if (funding !== undefined && funding.unfunded_amount > 0) {
    items.push({
      key: "unfunded",
      severity: "warning",
      text: `${formatCurrency(funding.unfunded_amount)} do plano segue sem funding`,
      to: "/orders",
    });
  }

  if (funding !== undefined && funding.neutro_deficit > 0) {
    items.push({
      key: "neutro",
      severity: "warning",
      text: `NEUTRO sem funding: ${formatCurrency(funding.neutro_deficit)}`,
      to: "/orders",
    });
  }

  // Real outside custody is a planning act, not a data fix: the owner decides whether it joins the
  // legacy book, so it stays an info card pointing at the bulk action on Alocação.
  const hasOutsideCustody = allocation !== null && shouldShowBanner(allocation);
  const outsideValue = allocation === null ? 0 : outsideCustody(allocation);
  if (hasOutsideCustody) {
    items.push({
      key: "outside",
      severity: "info",
      text:
        outsideValue > 0
          ? `Cotas fora da estratégia: ${formatCurrency(outsideValue)}`
          : "Cotas fora da estratégia — sem cotação",
      to: "/allocation",
      actionLabel: "Atribuir",
    });
  }

  return items.slice(0, 6);
}

export interface EditionRef {
  reference_month: string;
}

// The stepper's first input: which strategies already carry an edition for the open month. The page
// reads one editions list per strategy in parallel; this pairs them back by index.
export function editionCoverage(
  strategies: { name: string }[],
  editions: readonly (readonly EditionRef[] | undefined)[],
  period: string,
): StrategyCoverage[] {
  return strategies.map((strategy, index) => ({
    name: strategy.name,
    hasEdition: (editions[index] ?? []).some((edition) => edition.reference_month === period),
  }));
}

export interface AllocationStripSegment {
  assetClass: AssetClass;
  label: string;
  actualPercent: number;
  // Where the class's cumulative target falls on the full strip (0–100), when the policy declares one.
  idealPercent?: number;
}

// The compact class-level bar: each class's Actual share of Capital, with a marker at its cumulative
// Ideal. A book worth more than Capital is capped at 100% so the bar cannot overflow its frame.
export function allocationStrip(allocation: Allocation | null): AllocationStripSegment[] {
  if (allocation === null) return [];

  const capital = allocation.capital;
  let consumed = 0;
  let cumulativeTarget = 0;

  return allocation.classes.map((rung) => {
    const raw = capital > 0 ? (rung.actual / capital) * 100 : 0;
    const actualPercent = roundCents(Math.max(0, Math.min(raw, 100 - consumed)));
    consumed = roundCents(consumed + actualPercent);

    let idealPercent: number | undefined;
    if (rung.target !== undefined) {
      cumulativeTarget = roundCents(cumulativeTarget + rung.target);
      idealPercent = Math.max(0, Math.min(cumulativeTarget, 100));
    }

    return {
      assetClass: rung.asset_class,
      label: assetClassLabel(rung.asset_class),
      actualPercent,
      idealPercent,
    };
  });
}

// The "Próximos proventos" window: payments inside the next 30 days, soonest first.
export function upcomingIncome(incomes: Income[], today: string, days = 30): Income[] {
  const limit = addDays(today, days);
  return incomes
    .filter((income) => income.date >= today && income.date <= limit)
    .sort((a, b) => a.date.localeCompare(b.date));
}
