import { useNavigate } from "react-router-dom";
import { cn } from "../lib/cn.ts";
import type { RitualStep, StepStatus } from "../lib/cockpit.ts";
import { Button } from "./Button.tsx";

const MARK_TONE: Record<StepStatus, string> = {
  complete: "border-yield bg-yield-bg text-yield",
  pending: "border-line text-ink-faint",
  attention: "border-accent bg-accent-bg text-accent",
};

const MARK: Record<StepStatus, (step: RitualStep) => string> = {
  complete: () => "✓",
  pending: (step) => String(step.number),
  attention: (step) => (step.blocking ? "!" : "●"),
};

// The ritual's seven rows: the mark, the live detail and the action that advances the step.
export function RitualStepper({ steps }: { steps: RitualStep[] }) {
  const navigate = useNavigate();

  return (
    <div className="divide-y divide-line-soft">
      {steps.map((step) => (
        <div
          key={step.key}
          data-testid={`ritual-step-${step.key}`}
          className="grid grid-cols-[34px_1fr_auto] items-start gap-x-3.5 py-3.5 first:pt-0 last:pb-0"
        >
          <span
            className={cn(
              "mt-0.5 grid h-[26px] w-[26px] place-items-center rounded-full border-[1.5px] font-mono text-[12px] font-semibold",
              MARK_TONE[step.status],
              step.blocking && step.status === "attention" && "border-tax bg-tax-bg text-tax",
            )}
          >
            {MARK[step.status](step)}
          </span>
          <div>
            <div className="font-cond text-[15px] font-semibold">{step.title}</div>
            <div className={cn("mt-0.5 text-[12.5px] text-ink-dim", step.blocking && "text-tax")}>
              {step.detail}
            </div>
          </div>
          <div className="flex flex-col items-end gap-1.5">
            {step.optional && (
              <span className="font-mono text-[10.5px] tracking-[.08em] text-ink-faint uppercase">
                opcional
              </span>
            )}
            <Button
              className="px-2.5 py-1 text-[11.5px]"
              onClick={() => navigate(step.to)}
              data-testid={`ritual-action-${step.key}`}
            >
              {step.actionLabel}
            </Button>
          </div>
        </div>
      ))}
    </div>
  );
}
