import { cn } from "../lib/cn.ts";

// The reference's wizard nav (frontend/design/front-prototype.html `.wizard-nav`): a bordered strip
// of numbered steps, done steps in the yield colour, the current one in the amber action colour.
export function WizardStepper({ steps, current }: { steps: readonly string[]; current: number }) {
  return (
    <ol className="mb-5 flex overflow-hidden rounded-lg border border-line">
      {steps.map((label, index) => {
        const done = index < current;
        const active = index === current;
        return (
          <li
            key={label}
            className={cn(
              "flex flex-1 items-center gap-2 border-r border-line-soft px-3.5 py-2.5 text-[12px] last:border-r-0",
              active ? "bg-surface-2 text-ink" : done ? "text-yield" : "text-ink-faint",
            )}
          >
            <span
              className={cn(
                "grid h-[19px] w-[19px] flex-none place-items-center rounded-full border-[1.5px] font-mono text-[10px]",
                active
                  ? "border-accent text-accent"
                  : done
                    ? "border-yield bg-yield-bg text-yield"
                    : "border-line",
              )}
            >
              {done ? "✓" : index + 1}
            </span>
            {label}
          </li>
        );
      })}
    </ol>
  );
}
