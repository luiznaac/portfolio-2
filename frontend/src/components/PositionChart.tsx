import { useMemo } from "react";
import type { Position } from "../api/types.ts";
import { formatBRLCompact } from "../lib/money.ts";
import { formatDate } from "../lib/format.ts";

/**
 * Net portfolio value over time — `principal + yield - taxes` per date, drawn as
 * a hand-rolled SVG area (same technique as shougong's ItemsLearnedChart: a
 * `viewBox="0 0 100 100"` with `preserveAspectRatio="none"` and a non-scaling
 * stroke). `positions` must already be sorted by date and merged per date.
 */
export function PositionChart({ positions }: { positions: Position[] }) {
  const chart = useMemo(() => {
    if (positions.length === 0) return null;

    const net = positions.map((p) => p.principal + p.yield - p.taxes);
    const maxY = Math.max(...net, 1);
    const minY = Math.min(...net, 0);
    const span = maxY - minY || 1;
    const n = positions.length;

    const x = (i: number) => (n === 1 ? 0 : (i / (n - 1)) * 100);
    const y = (v: number) => 100 - ((v - minY) / span) * 100;

    const line = net.map((v, i) => `${x(i)},${y(v)}`).join(" ");
    return {
      line,
      area: `0,100 ${line} 100,100`,
      last: net[net.length - 1],
      firstLabel: formatDate(positions[0].date),
      lastLabel: formatDate(positions[n - 1].date),
      maxLabel: formatBRLCompact(maxY),
    };
  }, [positions]);

  if (!chart)
    return <p className="text-sm text-slate-500">Sem posições consolidadas ainda.</p>;

  return (
    <div>
      <svg
        viewBox="0 0 100 100"
        preserveAspectRatio="none"
        className="h-40 w-full"
      >
        <polygon points={chart.area} fill="var(--color-yield)" fillOpacity="0.12" />
        <polyline
          points={chart.line}
          fill="none"
          stroke="var(--color-yield)"
          strokeWidth="1.5"
          vectorEffect="non-scaling-stroke"
        />
      </svg>
      <div className="mt-1 flex justify-between text-[10px] text-slate-500">
        <span>{chart.firstLabel}</span>
        <span>pico {chart.maxLabel}</span>
        <span>{chart.lastLabel}</span>
      </div>
    </div>
  );
}
