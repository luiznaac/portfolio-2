// Labels for the corporate-action creation kinds. There's no wire-level CorporateActionType
// discriminator to translate (see CorporateAction in types.ts) — this covers the frontend-only
// "kind" tag used to pick which POST .../corporate-actions/{...} route to call.
export type CorporateActionKind = "split" | "reverse-split" | "bonus" | "ticker-change";

const LABELS_PT: Record<CorporateActionKind, string> = {
  split: "Desdobramento",
  "reverse-split": "Grupamento",
  bonus: "Bonificação",
  "ticker-change": "Troca de ticker",
};

export const CORPORATE_ACTION_KINDS: CorporateActionKind[] = [
  "split",
  "reverse-split",
  "bonus",
  "ticker-change",
];

export function corporateActionKindLabel(value: CorporateActionKind): string {
  return LABELS_PT[value];
}
