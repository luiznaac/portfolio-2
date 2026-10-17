import type { ReactNode } from "react";

export type ChipTone = "neutral" | "blue" | "green" | "amber" | "rose" | "violet";

// The single place the sign palette maps to utilities — callers name a tone, never a colour. Tones
// follow the reference's sign convention: blue = falta · comprar · Δ−, amber = excesso · vender,
// green = ganho · ok, rose = imposto · alerta, violet = FIIs.
const TONES: Record<ChipTone, string> = {
  neutral: "bg-surface-3 text-ink-dim",
  blue: "bg-principal-bg text-principal",
  green: "bg-yield-bg text-yield",
  amber: "bg-accent-bg text-accent",
  rose: "bg-tax-bg text-tax",
  violet: "bg-violet-bg text-violet",
};

export function Chip({ tone = "neutral", children }: { tone?: ChipTone; children: ReactNode }) {
  return (
    <span
      className={`inline-flex items-center gap-1 rounded-sm px-2 py-0.5 font-mono text-[10.5px] tracking-[.02em] whitespace-nowrap ${TONES[tone]}`}
    >
      {children}
    </span>
  );
}
