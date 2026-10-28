import type { Month } from "../api/types.ts";
import { monthPeriodLabel } from "../lib/month.ts";

// The closed-month selector the Fechamento summary and the Performance detail share. The id is the
// only state the caller owns; this control is fully controlled.
export function ClosedMonthSelect({
  months,
  monthId,
  onSelect,
}: {
  months: Month[];
  monthId: number | null;
  onSelect: (monthId: number) => void;
}) {
  if (months.length === 0) return null;

  return (
    <label className="flex items-center justify-between gap-3 text-[11.5px] text-ink-dim">
      Mês
      <select
        value={monthId ?? ""}
        onChange={(event) => onSelect(Number(event.target.value))}
        className="rounded-sm border border-line bg-surface-2 px-2 py-1.5 font-mono text-[12.5px] text-ink"
      >
        {months.map((month) => (
          <option key={month.id} value={month.id}>
            {monthPeriodLabel(month.period)}
          </option>
        ))}
      </select>
    </label>
  );
}
