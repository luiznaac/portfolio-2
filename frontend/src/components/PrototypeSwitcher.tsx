import { useEffect } from "react";
import { useSearchParams } from "react-router-dom";
import { cn } from "../lib/cn.ts";

// THROWAWAY prototype chrome. Cycles the `?variant=` search param and never ships to production.
export function PrototypeSwitcher({
  variants,
  names,
}: { variants: string[]; names?: Record<string, string> }) {
  const [params, setParams] = useSearchParams();
  const current = params.get("variant") ?? variants[0];
  const index = Math.max(0, variants.indexOf(current));
  const step = (delta: number) => {
    const next = variants[(index + delta + variants.length) % variants.length];
    const nextParams = new URLSearchParams(params);
    nextParams.set("variant", next);
    setParams(nextParams, { replace: true });
  };

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement | null;
      if (event.key !== "ArrowLeft" && event.key !== "ArrowRight") return;
      if (target && ["INPUT", "TEXTAREA", "SELECT"].includes(target.tagName)) return;
      if (target?.isContentEditable) return;
      event.preventDefault();
      step(event.key === "ArrowLeft" ? -1 : 1);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  });

  return (
    <div className="fixed bottom-5 left-1/2 z-50 flex -translate-x-1/2 items-center gap-3 rounded-full border border-line bg-surface-1 px-4 py-2 shadow-lg">
      <button
        type="button"
        onClick={() => step(-1)}
        className="rounded-full px-2 py-0.5 text-[13px] text-ink-dim hover:text-ink"
        aria-label="Variante anterior"
      >
        ←
      </button>
      <span className="font-mono text-[11.5px] text-ink">
        {current}
        {names?.[current] ? ` · ${names[current]}` : ""}
      </span>
      <button
        type="button"
        onClick={() => step(1)}
        className="rounded-full px-2 py-0.5 text-[13px] text-ink-dim hover:text-ink"
        aria-label="Próxima variante"
      >
        →
      </button>
      <span className={cn("h-1.5 w-1.5 rounded-full bg-accent")} aria-hidden="true" />
      <span className="text-[10px] uppercase tracking-wide text-ink-faint">protótipo</span>
    </div>
  );
}
