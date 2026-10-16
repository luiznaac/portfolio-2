import type { ImportStatus } from "../api/types.ts";

const STYLES: Record<ImportStatus, { label: string; className: string }> = {
  STAGED: { label: "Aguardando revisão", className: "bg-slate-500/15 text-slate-300" },
  REVIEWED: { label: "Revisada", className: "bg-sky-500/15 text-sky-400" },
  APPLIED: { label: "Aplicada", className: "bg-emerald-500/15 text-emerald-400" },
  FAILED: { label: "Falhou", className: "bg-rose-500/15 text-rose-400" },
};

export function ImportStatusChip({ status }: { status: ImportStatus }) {
  const { label, className } = STYLES[status];
  return (
    <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${className}`}>{label}</span>
  );
}
