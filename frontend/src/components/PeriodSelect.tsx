import type { MonthPeriodRange } from "../api/types.ts";
import {
  boundPeriod,
  monthOptionsFor,
  periodMonth,
  periodYear,
  periodYearOptions,
} from "../lib/month.ts";

const FIELD =
  "rounded-sm border border-line bg-surface-2 px-2 py-1.5 font-mono text-[12.5px] text-ink";

// The month/year pair every period picker shares: the opener and Impostos both read the same server
// window, so the bounded years and the disabled boundary months cannot drift between them.
export function PeriodSelect({
  range,
  period,
  onChange,
}: {
  range: MonthPeriodRange;
  period: string;
  onChange: (period: string) => void;
}) {
  const year = periodYear(period);

  return (
    <div className="flex gap-2">
      <select
        aria-label="Mês"
        value={periodMonth(period)}
        onChange={(event) => onChange(boundPeriod(`${year}-${event.target.value}`, range))}
        className={FIELD}
      >
        {monthOptionsFor(year, range).map((month) => (
          <option key={month.value} value={month.value} disabled={month.disabled}>
            {month.label}
          </option>
        ))}
      </select>
      <select
        aria-label="Ano"
        value={year}
        onChange={(event) =>
          onChange(boundPeriod(`${event.target.value}-${periodMonth(period)}`, range))
        }
        className={FIELD}
      >
        {periodYearOptions(range).map((option) => (
          <option key={option} value={option}>
            {option}
          </option>
        ))}
      </select>
    </div>
  );
}
