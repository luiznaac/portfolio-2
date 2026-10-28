import { cn } from "../lib/cn.ts";

export type KpiTone = "default" | "gain" | "loss" | "muted";

const TONES: Record<KpiTone, string> = {
  default: "text-ink",
  gain: "text-yield",
  loss: "text-principal",
  muted: "text-ink-dim",
};

// The reference's KPI tile — a label over one figure. `lg` is the detail-page stat; `sm` the compact
// grid tile the month-history screens use. Callers name a tone, never a colour.
export function Kpi({
  label,
  value,
  hint,
  tone = "default",
  size = "sm",
  testId,
}: {
  label: string;
  value?: string;
  hint?: string;
  tone?: KpiTone;
  size?: "sm" | "lg";
  testId?: string;
}) {
  return (
    <div
      className={cn(
        "rounded-lg border border-line bg-surface",
        size === "sm" ? "px-3 py-2.5" : "px-4 py-3.5",
      )}
    >
      <div className="font-mono text-[10px] tracking-[.09em] text-ink-faint uppercase">{label}</div>
      <div
        data-testid={testId}
        className={cn(
          "mt-1",
          size === "sm" ? "font-mono text-[17px]" : "font-cond text-[22px] font-bold",
          TONES[tone],
        )}
      >
        {value ?? "—"}
      </div>
      {hint !== undefined && <div className="mt-0.5 text-[11.5px] text-ink-faint">{hint}</div>}
    </div>
  );
}
