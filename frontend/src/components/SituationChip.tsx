import type { ImportRowSituation } from "../lib/importSituations.ts";

const STYLES: Record<ImportRowSituation, { label: string; className: string }> = {
  new: { label: "Novo", className: "bg-emerald-500/15 text-emerald-400" },
  duplicate: { label: "Duplicado", className: "bg-amber-500/15 text-amber-400" },
  warning: { label: "Atenção", className: "bg-rose-500/15 text-rose-400" },
};

export function SituationChip({ situation }: { situation: ImportRowSituation }) {
  const { label, className } = STYLES[situation];
  return (
    <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${className}`}>{label}</span>
  );
}
