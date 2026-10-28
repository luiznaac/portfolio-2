// Pure Fechamento helpers — no React imports here (see AGENTS.md). The close gate, the selected
// closed month and the frozen summary the read renders live here so their rules are unit-tested
// rather than buried in the page.

import type { OrderList, Snapshot, TransferProposal } from "../api/types.ts";
import { formatLocalDateTime } from "./format.ts";

// The close gate and the frozen closed-month summary live here; the closed-month selection helper is
// shared with the performance page and lives in lib/month.ts.

// The gate the close button reads: only undecided proposals block it. A month with no persisted
// plan yet (no orders read) has nothing pending — the close refreshes the plan itself.
export function pendingTransfers(orders: OrderList | null | undefined): TransferProposal[] {
  return (orders?.transfers ?? []).filter((transfer) => transfer.status === "PENDING");
}

export function closeButtonLabel(pendingCount: number): string {
  if (pendingCount === 0) return "Fechar mês";
  const noun = pendingCount === 1 ? "transferência pendente" : "transferências pendentes";
  return `Bloqueado — ${pendingCount} ${noun}`;
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
