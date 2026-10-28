import type { ReactNode } from "react";

export type BadgeTone = "neutral" | "ok" | "warn" | "crit" | "info";

// The status counterpart of Chip: mono, uppercase, smaller — the one place its tones map to
// utilities. ok = green (ganho), warn = amber (atenção/excesso), crit = rose (bloqueio/imposto),
// info = blue (informativo).
const TONES: Record<BadgeTone, string> = {
  neutral: "bg-surface-3 text-ink-dim",
  ok: "bg-yield-bg text-yield",
  warn: "bg-accent-bg text-accent",
  crit: "bg-tax-bg text-tax",
  info: "bg-principal-bg text-principal",
};

export function Badge({ tone = "neutral", children }: { tone?: BadgeTone; children: ReactNode }) {
  return (
    <span
      className={`inline-flex items-center rounded-xs px-1.5 py-0.5 font-mono text-[10px] font-medium tracking-[.06em] uppercase whitespace-nowrap ${TONES[tone]}`}
    >
      {children}
    </span>
  );
}
