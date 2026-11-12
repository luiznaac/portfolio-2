import { cn } from "../lib/cn.ts";

export type KpiTone = "default" | "gain" | "loss" | "muted";

const TONES: Record<KpiTone, string> = {
  default: "text-text",
  gain: "text-return-up",
  loss: "text-deficit",
  muted: "text-muted",
};

// The console KPI tile — a 9,5 px uppercase mono label over one figure. `lg` is the detail-page
// stat; `sm` the compact grid tile the month-history screens use. Callers name a tone, never a
// colour.
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
        "rounded-panel border border-line bg-panel",
        size === "sm" ? "px-3 py-2.5" : "px-4 py-3.5",
      )}
    >
      <div className="font-mono text-label font-semibold tracking-[.12em] text-faint uppercase">
        {label}
      </div>
      <div
        data-testid={testId}
        className={cn(
          "mt-1 tabular-nums",
          size === "sm" ? "font-mono text-title" : "font-cond text-[22px] font-bold",
          TONES[tone],
        )}
      >
        {value ?? "—"}
      </div>
      {hint !== undefined && <div className="mt-0.5 text-num text-faint">{hint}</div>}
    </div>
  );
}
