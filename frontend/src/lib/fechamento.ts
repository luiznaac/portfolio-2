// Pure Fechamento helpers — no React imports here (see AGENTS.md). The close gate, the selected
// closed month and the frozen summary the read renders live here so their rules are unit-tested
// rather than buried in the page.

import type {
  CorporateAction,
  Gap,
  Income,
  OrderList,
  Snapshot,
  StaleQuote,
  TransferProposal,
} from "../api/types.ts";
import { formatLocalDateTime, formatNumber } from "./format.ts";
import { openGaps } from "./gaps.ts";
import { orderExecutionSummary } from "./orders.ts";
import { ROUTES } from "./routes.ts";

// The close gate and the frozen closed-month summary live here; the closed-month selection helper is
// shared with the performance page and lives in lib/month.ts.

// The gate the close button reads: only undecided proposals block it. A month with no persisted
// plan yet (no orders read) has nothing pending — the close refreshes the plan itself.
export function pendingTransfers(orders: OrderList | null | undefined): TransferProposal[] {
  return (orders?.transfers ?? []).filter((transfer) => transfer.status === "PENDING");
}

// A decided proposal is a decision, not a pending one — the inbox history and the step-5 summary
// read the same partition the close does.
export function decidedTransfers(orders: OrderList | null | undefined): TransferProposal[] {
  return (orders?.transfers ?? []).filter((transfer) => transfer.status !== "PENDING");
}

// The one owner of the pending-transfer noun, so the close button and the landing's month line
// cannot describe the same queue with two different words.
export function pendingTransferNoun(count: number): string {
  return count === 1 ? "transferência pendente" : "transferências pendentes";
}

// The decided counterpart, shared by the inbox history and step 5's one-line summary.
export function decidedTransferNoun(count: number): string {
  return count === 1 ? "transferência decidida" : "transferências decididas";
}

// The close screen's one gate rule: only an undecided transfer refuses the close. The view carries
// the title and the stated reason the page renders, so the gate cannot drift between Fechamentos and
// step 6 on Mês atual.
export type CloseGateState = "no-month" | "backdated" | "checking" | "pending" | "ready";

export interface CloseGateInput {
  hasOpenMonth: boolean;
  backdated: boolean;
  gateReady: boolean;
  pendingCount: number;
}

export interface CloseGateView {
  state: CloseGateState;
  title: string;
  detail: string;
  // The current inbox says a refusal is likely. The close is still attemptable: its refresh is the
  // authority, and it can finalise a stale proposal the pre-close read still shows as pending.
  blocked: boolean;
  canClose: boolean;
}

export function closeGateView(input: CloseGateInput): CloseGateView {
  if (!input.hasOpenMonth) {
    return {
      state: "no-month",
      title: "Nenhum mês aberto",
      detail: "Abra um mês para fechá-lo.",
      blocked: true,
      canClose: false,
    };
  }
  if (input.backdated) {
    return {
      state: "backdated",
      title: "Só o mês atual pode ser fechado",
      detail:
        "Um mês histórico é apenas de planejamento: o fechamento congela o mês atual, nunca o passado importado.",
      blocked: true,
      canClose: false,
    };
  }
  if (!input.gateReady) {
    return {
      state: "checking",
      title: "Verificando transferências",
      detail: "A trava do fechamento só é confiável depois de ler a caixa de transferências.",
      blocked: true,
      canClose: false,
    };
  }
  if (input.pendingCount > 0) {
    return {
      state: "pending",
      title: "Fechamento bloqueado: transferência pendente",
      detail:
        "O fechamento recalcula o plano antes de decidir — é esse refresh que finaliza as transferências (autoaprovando as abaixo de R$ 500). Se sobrar uma pendente, o fechamento é recusado; recusar é uma decisão e libera o fechamento.",
      blocked: true,
      // The attempt must run: the refresh is what finalises the transfers before the gate decides.
      canClose: true,
    };
  }
  return {
    state: "ready",
    title: "Pronto para fechar",
    detail:
      "O refresh do fechamento finaliza as transferências (autoaprovando as abaixo de R$ 500) e não encontra nenhuma pendente. Fechar recalcula, grava o snapshot e trava o mês.",
    blocked: false,
    canClose: true,
  };
}

export type CloseNonBlockingKey =
  | "orders"
  | "gaps"
  | "corporate-actions"
  | "stale-quotes"
  | "receivable-income";

export interface CloseNonBlockingInput {
  period: string;
  orders: OrderList | null | undefined;
  gaps: Gap[];
  corporateActions: CorporateAction[];
  staleQuotes: StaleQuote[];
  incomes: Income[];
}

export interface CloseNonBlockingItem {
  key: CloseNonBlockingKey;
  label: string;
  count: number;
  detail: string;
  to: string;
  // Every row wears the same label: none of these states refuses the close.
  badge: "não trava";
}

// The data quality the close freezes as-is. The list is always complete — a zero row states that the
// category was checked and is empty — because the point is that none of these five can hold the close.
export function closeNonBlockingItems(input: CloseNonBlockingInput): CloseNonBlockingItem[] {
  const execution = orderExecutionSummary(input.orders?.orders ?? []);
  const orderCount = execution.partial.length + execution.unexecuted.length;
  const gaps = openGaps(input.gaps).length;
  const reviews = input.corporateActions.filter(
    (action) => action.status === "NEEDS_REVIEW",
  ).length;
  const staleQuotes = input.staleQuotes.length;
  const receivable = input.incomes.filter(
    (income) =>
      income.date.slice(0, 7) === input.period &&
      (income.status === "ANNOUNCED" || income.status === "PAYABLE"),
  ).length;

  return [
    {
      key: "orders",
      label: "Ordens não executadas ou parciais",
      count: orderCount,
      detail:
        orderCount === 0
          ? "nenhuma"
          : `${formatNumber(orderCount)} ${orderCount === 1 ? "ordem" : "ordens"}${
              execution.partial.length > 0
                ? ` · ${formatNumber(execution.partial.length)} parcial`
                : ""
            }`,
      to: ROUTES.planOrders,
      badge: "não trava",
    },
    {
      key: "gaps",
      label: "Lacunas abertas",
      count: gaps,
      detail:
        gaps === 0
          ? "nenhuma aberta"
          : `${formatNumber(gaps)} ${gaps === 1 ? "aberta" : "abertas"}`,
      to: ROUTES.gaps,
      badge: "não trava",
    },
    {
      key: "corporate-actions",
      label: "Ações corporativas em revisão",
      count: reviews,
      detail: reviews === 0 ? "nenhuma em revisão" : `${formatNumber(reviews)} em revisão`,
      to: ROUTES.corporateActions,
      badge: "não trava",
    },
    {
      key: "stale-quotes",
      label: "Cotações desatualizadas",
      count: staleQuotes,
      detail:
        staleQuotes === 0
          ? "nenhuma desatualizada"
          : `${formatNumber(staleQuotes)} ${staleQuotes === 1 ? "desatualizada" : "desatualizadas"}`,
      to: ROUTES.quotes,
      badge: "não trava",
    },
    {
      key: "receivable-income",
      label: "Proventos a receber",
      count: receivable,
      detail: receivable === 0 ? "nenhum a receber" : `${formatNumber(receivable)} a receber`,
      to: ROUTES.income,
      badge: "não trava",
    },
  ];
}

// The frozen plan the snapshot carries, read back as the same order list shape the step and the
// Orders page use. A closed month never recomputes its plan: this is the plan the close wrote.
export function snapshotOrderList(snapshot: Snapshot): OrderList {
  return {
    period: snapshot.state.period,
    orders: snapshot.state.orders.orders,
    transfers: snapshot.state.orders.transfers,
    unplanned_trades: snapshot.state.orders.unplanned_trades,
  };
}

export interface ClosedMonthSummary {
  monthId: number;
  period: string;
  capital: number;
  floor: number;
  caixa: number;
  incomeNet: number;
  executedOrders: number;
  closedAt: string;
}

// The read-only summary of a closed month, taken from its immutable snapshot. An order counts as
// executed once the lifecycle matched it to a trade (EXECUTED) or imported it (IMPORTED); a
// suggestion that is still pending is not part of what the month ended with.
export function closedMonthSummary(snapshot: Snapshot): ClosedMonthSummary {
  const { state } = snapshot;
  return {
    monthId: snapshot.month_id,
    period: state.period,
    capital: state.capital,
    floor: state.floor,
    caixa: state.caixa,
    incomeNet: state.income.net,
    executedOrders: state.orders.orders.filter((order) => order.status !== "SUGGESTED").length,
    closedAt: formatLocalDateTime(snapshot.created_at),
  };
}
