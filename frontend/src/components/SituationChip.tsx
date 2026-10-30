import type { ImportRowSituation } from "../api/types.ts";
import { Chip, type ChipTone } from "./Chip.tsx";

// The per-row review verdict renders as a chip (the reference's `.chip`), not a status badge.
const STYLES: Record<ImportRowSituation, { label: string; tone: ChipTone }> = {
  new: { label: "Novo", tone: "green" },
  duplicate_in_file: { label: "Repetida no arquivo", tone: "amber" },
  duplicate_in_ledger: { label: "Já no livro", tone: "amber" },
  error: { label: "Atenção", tone: "rose" },
};

export function SituationChip({ situation }: { situation: ImportRowSituation }) {
  const { label, tone } = STYLES[situation];
  return <Chip tone={tone}>{label}</Chip>;
}
