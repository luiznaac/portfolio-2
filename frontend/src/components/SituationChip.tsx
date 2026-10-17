import type { ImportRowSituation } from "../lib/importSituations.ts";
import { Chip, type ChipTone } from "./Chip.tsx";

// The per-row review hint renders as a chip (the reference's `.chip`), not a status badge.
const STYLES: Record<ImportRowSituation, { label: string; tone: ChipTone }> = {
  new: { label: "Novo", tone: "green" },
  duplicate: { label: "Duplicado", tone: "amber" },
  warning: { label: "Atenção", tone: "rose" },
};

export function SituationChip({ situation }: { situation: ImportRowSituation }) {
  const { label, tone } = STYLES[situation];
  return <Chip tone={tone}>{label}</Chip>;
}
