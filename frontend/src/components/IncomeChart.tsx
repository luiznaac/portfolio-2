import { formatCurrency } from "../lib/format.ts";
import type { IncomeMonthBar } from "../lib/income.ts";
import { monthPeriodLabel } from "../lib/month.ts";

// The Proventos window's one chart: the net income paid in each month of the shared window, as
// bars over a hairline baseline. A month that paid nothing keeps its slot so the gaps read as
// months, not as missing categories.
export function IncomeChart({ months }: { months: IncomeMonthBar[] }) {
  const width = 640;
  const height = 140;
  const pad = { top: 14, right: 12, bottom: 24, left: 52 };
  const innerWidth = width - pad.left - pad.right;
  const innerHeight = height - pad.top - pad.bottom;
  const max = Math.max(...months.map((month) => month.value), 1);
  const slot = innerWidth / Math.max(months.length, 1);
  const barWidth = Math.min(34, slot * 0.62);
  const baseline = pad.top + innerHeight;
  const y = (value: number) => pad.top + (1 - value / max) * innerHeight;

  return (
    <div data-testid="income-chart">
      <svg
        width="100%"
        height={height}
        viewBox={`0 0 ${width} ${height}`}
        role="img"
        aria-label="Proventos líquidos por mês no recorte"
      >
        <line
          x1={pad.left}
          x2={width - pad.right}
          y1={baseline}
          y2={baseline}
          className="stroke-line"
          strokeWidth={1}
        />
        <text x={pad.left - 6} y={pad.top + 3} textAnchor="end" className="fill-faint text-[9px]">
          {formatCurrency(max)}
        </text>
        <text x={pad.left - 6} y={baseline} textAnchor="end" className="fill-faint text-[9px]">
          {formatCurrency(0)}
        </text>
        {months.map((month, index) => {
          const center = pad.left + slot * index + slot / 2;
          const barHeight = (month.value / max) * innerHeight;
          return (
            <g key={month.period}>
              <rect
                data-testid={`income-bar-${month.period}`}
                x={center - barWidth / 2}
                y={y(month.value)}
                width={barWidth}
                height={Math.max(barHeight, 1)}
                rx={2}
                className="fill-action"
              >
                <title>
                  {monthPeriodLabel(month.period)} · {formatCurrency(month.value)}
                </title>
              </rect>
              <text
                x={center}
                y={height - 8}
                textAnchor="middle"
                className="fill-faint font-mono text-[9px]"
              >
                {monthPeriodLabel(month.period)}
              </text>
            </g>
          );
        })}
      </svg>
    </div>
  );
}
