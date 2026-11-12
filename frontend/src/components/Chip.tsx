import type { ReactNode } from "react";

export type ChipTone = "neutral" | "blue" | "green" | "amber" | "rose" | "violet";

// The single place the sign palette maps to utilities — callers name a tone, never a colour. Tones
// follow the console sign convention: blue = deficit · falta · comprar, amber = surplus · atenção,
// green = return+ · ganho/ok, rose = return− · crítico, violet = FIIs.
const TONES: Record<ChipTone, string> = {
  neutral: "bg-panel-3 text-muted",
  blue: "bg-action-bg text-action",
  green: "bg-return-up-bg text-return-up",
  amber: "bg-attention-bg text-attention",
  rose: "bg-return-down-bg text-return-down",
  violet: "bg-violet-bg text-violet",
};

export function Chip({
  tone = "neutral",
  testId,
  children,
}: {
  tone?: ChipTone;
  testId?: string;
  children: ReactNode;
}) {
  return (
    <span
      data-testid={testId}
      className={`inline-flex items-center gap-1 rounded-xs px-1.5 py-[3px] font-mono text-label font-semibold tracking-[.06em] uppercase whitespace-nowrap ${TONES[tone]}`}
    >
      {children}
    </span>
  );
}
