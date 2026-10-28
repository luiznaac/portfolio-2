import type { ImportStatus } from "../api/types.ts";
import { Badge, type BadgeTone } from "./Badge.tsx";

const STYLES: Record<ImportStatus, { label: string; tone: BadgeTone }> = {
  STAGED: { label: "Aguardando revisão", tone: "neutral" },
  REVIEWED: { label: "Revisada", tone: "info" },
  APPLIED: { label: "Aplicada", tone: "ok" },
  FAILED: { label: "Falhou", tone: "crit" },
};

export function ImportStatusChip({ status }: { status: ImportStatus }) {
  const { label, tone } = STYLES[status];
  return <Badge tone={tone}>{label}</Badge>;
}
