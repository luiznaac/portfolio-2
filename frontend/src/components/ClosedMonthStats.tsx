import type { ClosedMonthSummary } from "../lib/fechamento.ts";
import { formatCurrency, formatNumber } from "../lib/format.ts";
import { Kpi } from "./Kpi.tsx";

// The frozen month's stat row, shared by Fechamentos and the closed state on Mês atual. Every value
// comes from the snapshot's summary, never a live read.
export function ClosedMonthStats({ summary }: { summary: ClosedMonthSummary }) {
  return (
    <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
      <Kpi label="Capital" value={formatCurrency(summary.capital)} />
      <Kpi label="Piso" value={formatCurrency(summary.floor)} />
      <Kpi label="Caixa" value={formatCurrency(summary.caixa)} />
      <Kpi label="Proventos líquidos" value={formatCurrency(summary.incomeNet)} />
      <Kpi label="Ordens executadas" value={formatNumber(summary.executedOrders)} />
    </div>
  );
}
