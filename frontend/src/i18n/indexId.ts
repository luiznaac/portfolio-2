// The backend's IndexId is an acronym (CDI/SELIC/IPCA) — kept as-is in the UI,
// but this is the single place to attach a longer label or a colour token.
import type { IndexId } from "../api/types.ts";

const DESCRIPTIONS_PT: Record<IndexId, string> = {
  CDI: "Certificado de Depósito Interbancário",
  SELIC: "Taxa básica de juros",
  IPCA: "Índice de preços ao consumidor",
};

export const INDEX_IDS: IndexId[] = ["CDI", "SELIC", "IPCA"];

export function indexDescription(id: IndexId): string {
  return DESCRIPTIONS_PT[id];
}

export function indexColorVar(id: IndexId): string {
  return `var(--color-index-${id.toLowerCase()})`;
}
