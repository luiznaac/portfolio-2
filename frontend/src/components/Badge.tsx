import type { ReactNode } from "react";
import { Chip, type ChipTone } from "./Chip.tsx";

export type BadgeTone = "neutral" | "ok" | "warn" | "crit" | "info";

// The status counterpart of Chip: the same shell and sign palette, with a semantic status name.
// ok = return+ (ganho), warn = attention (atenção/surplus), crit = return− (bloqueio), info =
// action (informativo). Mapping to Chip's tones here keeps one place deciding the colour.
const TONES: Record<BadgeTone, ChipTone> = {
  neutral: "neutral",
  ok: "green",
  warn: "amber",
  crit: "rose",
  info: "blue",
};

export function Badge({ tone = "neutral", children }: { tone?: BadgeTone; children: ReactNode }) {
  return <Chip tone={TONES[tone]}>{children}</Chip>;
}
