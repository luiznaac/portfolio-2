import { Link } from "react-router-dom";
import { useCurrentMonth, useOrders } from "../api/queries.ts";
import { pendingTransfers } from "../lib/fechamento.ts";
import { monthPeriodLabel, monthStatusLabel } from "../lib/month.ts";
import { Chip } from "./Chip.tsx";

// The topbar's month pill: the open month and its status, or the way to open one. The pending
// transfer count is the one decision the shell surfaces everywhere.
export function MonthPill() {
  const current = useCurrentMonth();
  const orders = useOrders();
  const month = current.data?.month;
  const pending = pendingTransfers(orders.data).length;

  if (current.isLoading) {
    return (
      <span className="rounded-md border border-line bg-surface-2 px-2.5 py-1 text-[12.5px] text-ink-faint">
        …
      </span>
    );
  }

  // An error is not "no month open": the pill says the read failed instead of inviting a new month.
  if (current.isError) {
    return (
      <span className="rounded-md border border-line bg-surface-2 px-2.5 py-1 text-[12.5px] text-tax">
        Mês indisponível
      </span>
    );
  }

  return (
    <Link
      data-testid="month-pill"
      to={month === undefined ? "/month" : "/"}
      className="flex items-center gap-2.5 rounded-md border border-line bg-surface-2 px-2.5 py-1 text-[12.5px] hover:border-surface-3"
    >
      <span className={month === undefined ? "text-ink-dim" : "text-ink"}>
        {month === undefined ? "Nenhum mês aberto" : monthPeriodLabel(month.period)}
      </span>
      {month !== undefined && (
        <span className="flex items-center gap-1.5 text-ink-dim">
          <span className="h-[7px] w-[7px] rounded-full bg-yield" />
          {monthStatusLabel(month.status)}
        </span>
      )}
      {pending > 0 && (
        <Chip tone="rose">
          {pending} pendente{pending === 1 ? "" : "s"}
        </Chip>
      )}
    </Link>
  );
}
