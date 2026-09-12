import { formatBRLCompact } from "../lib/money.ts";

/**
 * One row of the allocation drift chart — a horizontal bar centered on zero, extending left
 * (falta: buy/contribute, --color-principal) or right (excesso: sell/redeem, --color-accent-500).
 * `maxAbs` is shared across sibling rows so the scale stays comparable, same as the plan's figure.
 */
export function DivergentBar({
  label,
  delta,
  maxAbs,
}: {
  label: string;
  delta: number;
  maxAbs: number;
}) {
  const pct = maxAbs === 0 ? 0 : (Math.abs(delta) / maxAbs) * 50;
  const isExcess = delta > 0;

  return (
    <div className="grid grid-cols-[minmax(0,9rem)_1fr_minmax(0,5.5rem)] items-center gap-3 py-1.5">
      <span className="truncate text-sm text-slate-300">{label}</span>
      <div className="relative h-4 rounded-sm bg-slate-800/60">
        <div className="absolute left-1/2 top-0 h-full w-px bg-slate-600" />
        <div
          className={`absolute top-0 h-full rounded-sm ${isExcess ? "bg-accent-500" : "bg-principal"}`}
          style={isExcess ? { left: "50%", width: `${pct}%` } : { right: "50%", width: `${pct}%` }}
        />
      </div>
      <span
        className={`text-right text-xs tabular-nums ${isExcess ? "text-accent-500" : "text-principal"}`}
      >
        {isExcess ? "+" : "−"}
        {formatBRLCompact(Math.abs(delta))}
      </span>
    </div>
  );
}
