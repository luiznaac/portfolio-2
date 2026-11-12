import type { ImportStatus } from "../api/types.ts";
import { importStatusLabel } from "../lib/imports.ts";
import { Badge, type BadgeTone } from "./Badge.tsx";

const TONES: Record<ImportStatus, BadgeTone> = {
  STAGED: "neutral",
  REVIEWED: "info",
  APPLIED: "ok",
  FAILED: "crit",
};

export function ImportStatusChip({ status }: { status: ImportStatus }) {
  return <Badge tone={TONES[status]}>{importStatusLabel(status)}</Badge>;
}
