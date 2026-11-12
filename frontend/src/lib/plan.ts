// Pure Plano / Mês derivations — no React imports here (see AGENTS.md). The six-step month is a task
// list, not a dashboard: this module decides which steps are done, which step the tool opens on and
// what each concluded step's one-line summary says. The rules mirror the backend's
// PortfolioStatusCalculator so the page's `n de 6` means the same thing as the landing's; the wire
// carries no per-step detail, so the derivation lives here and is unit-tested.

import type { AssetClass, Month, OrderList, Targets } from "../api/types.ts";
import { pendingTransfers } from "./fechamento.ts";
import { formatCurrency } from "./format.ts";
import { orderExecutionSummary } from "./orders.ts";

export type PlanStepKey = "capital" | "targets" | "plan" | "execution" | "transfer" | "close";

export const PLAN_STEPS_TOTAL = 6;

const STEP_LABELS: Record<PlanStepKey, string> = {
  capital: "Capital do mês",
  targets: "Cadeia de alvos",
  plan: "Plano calculado",
  execution: "Ordens executadas",
  transfer: "Transferência decidida",
  close: "Mês fechado",
};

export interface PlanStep {
  key: PlanStepKey;
  label: string;
  done: boolean;
  // True when the step is the reason the month cannot close (a pending transfer).
  blocked: boolean;
  // The concluded step's one-line summary, or the pending step's state.
  meta: string;
}

export interface PlanStepInput {
  month: Month;
  targets: Targets;
  orders: OrderList | null;
  // A capital or targets edit happened after the last refresh: the stored plan is a snapshot, so it
  // is stale until POST /orders/plan/refresh rewrites it.
  planStale?: boolean;
}

function hasTargets(targets: Targets): boolean {
  return (
    targets.classes.length > 0 ||
    targets.strategies.length > 0 ||
    targets.sub_classes.length > 0 ||
    targets.tickers.length > 0
  );
}

function targetsMeta(targets: Targets): string {
  const parts = [
    plural(targets.classes.length, "classe", "classes"),
    plural(targets.strategies.length, "estratégia", "estratégias"),
    plural(targets.sub_classes.length, "subclasse", "subclasses"),
  ];
  if (targets.tickers.length > 0) parts.push(plural(targets.tickers.length, "ticker", "tickers"));
  return parts.join(" · ");
}

function plural(count: number, singular: string, plural: string): string {
  return `${count} ${count === 1 ? singular : plural}`;
}

// A persisted suggestion or transfer is the only evidence the refresh ran: the read carries no plan
// marker. An unplanned trade is a trade the plan did not ask for, so it never completes the step.
export function hasPersistedPlan(orders: OrderList | null): boolean {
  return orders !== null && (orders.orders.length > 0 || orders.transfers.length > 0);
}

// The six steps run in order; each is done only when its artefact exists for the open month.
export function planSteps({
  month,
  targets,
  orders,
  planStale = false,
}: PlanStepInput): PlanStep[] {
  const capital = month.capital > 0;
  const targeted = hasTargets(targets);
  const plan = hasPersistedPlan(orders);
  const pending = pendingTransfers(orders);
  const orderCount = orders?.orders.length ?? 0;
  // The same execution derivation the step-4 body renders, so the rail's `n de 6` and the body's
  // buckets can never disagree on what is executed, partial or untouched.
  const execution = orderExecutionSummary(orders?.orders ?? []);
  const executionDone = plan && execution.executed.length === orderCount;
  const transferDone = plan && pending.length === 0;
  const closed = month.status === "CLOSED";

  return [
    {
      key: "capital",
      label: STEP_LABELS.capital,
      done: capital,
      blocked: false,
      meta: capital ? formatCurrency(month.capital) : "capital ainda não definido",
    },
    {
      key: "targets",
      label: STEP_LABELS.targets,
      done: targeted,
      blocked: false,
      meta: targeted ? targetsMeta(targets) : "nenhum alvo definido",
    },
    {
      key: "plan",
      label: STEP_LABELS.plan,
      done: plan,
      blocked: false,
      meta: !plan
        ? "plano ainda não calculado"
        : planStale
          ? "plano desatualizado — recalcule"
          : `plano persistido · ${plural(orderCount, "ordem", "ordens")}`,
    },
    {
      key: "execution",
      label: STEP_LABELS.execution,
      done: executionDone,
      blocked: false,
      meta: !plan
        ? "o plano ainda não foi calculado"
        : orderCount === 0
          ? "sem ordens sugeridas"
          : `${execution.executed.length}/${orderCount} executadas${execution.partial.length > 0 ? ` · ${execution.partial.length} parcial` : ""}`,
    },
    {
      key: "transfer",
      label: STEP_LABELS.transfer,
      done: transferDone,
      blocked: pending.length > 0,
      meta: !plan
        ? "o plano ainda não foi calculado"
        : pending.length > 0
          ? `${pending.length} pendente · trava o fechamento`
          : (orders?.transfers.length ?? 0) === 0
            ? "nenhuma transferência"
            : "decidida",
    },
    {
      key: "close",
      label: STEP_LABELS.close,
      done: closed,
      blocked: false,
      meta: closed ? "snapshot gravado" : "aberto",
    },
  ];
}

// The tool opens on the first unfinished step; a fully concluded month rests on the last step so the
// page still has a body to render.
export function firstUnfinishedStep(steps: PlanStep[]): PlanStep {
  const first = steps.find((step) => !step.done);
  if (first !== undefined) return first;

  const last = steps.at(-1);
  if (last === undefined) throw new Error("plan has no steps");
  return last;
}

export function doneStepCount(steps: PlanStep[]): number {
  return steps.filter((step) => step.done).length;
}

export interface ChainPreviewRow {
  assetClass: AssetClass;
  target: number;
  ideal: number;
}

// The Capital step's preview: ideal = typed Capital × the chain's class target. It reads the typed
// value, not the stored one, so the owner sees the chain a save would produce before the refresh.
// A blank or invalid capital has no preview.
export function chainPreview(capital: string, targets: Targets): ChainPreviewRow[] {
  const trimmed = capital.trim();
  if (trimmed.length === 0) return [];
  const parsed = Number(trimmed);
  if (!Number.isFinite(parsed) || parsed < 0) return [];

  return targets.classes.map((row) => ({
    assetClass: row.asset_class,
    target: row.percentage,
    ideal: Math.round(parsed * (row.percentage / 100) * 100) / 100,
  }));
}
