// The one mapping from a numeric read's meaning to a console token class — no React imports here,
// so the page, the derivation and the `Num` primitive all consume the same table. Callers name the
// role (`deficit`, `surplus`, `return-up`, …), never a colour; the sign stays in the text.
export type Tone =
  | "default"
  | "muted"
  | "deficit"
  | "surplus"
  | "attention"
  | "return-up"
  | "return-down";

const TONE_CLASSES: Record<Tone, string> = {
  default: "text-text",
  muted: "text-muted",
  deficit: "text-deficit",
  surplus: "text-surplus",
  attention: "text-attention",
  "return-up": "text-return-up",
  "return-down": "text-return-down",
};

export function toneClass(tone: Tone): string {
  return TONE_CLASSES[tone];
}

export type SignedTone = Extract<Tone, "muted" | "deficit" | "surplus">;

// A Drift's direction: above target is a Surplus (amber), below is a Deficit (blue).
export function driftTone(value: number): SignedTone {
  return value > 0 ? "surplus" : value < 0 ? "deficit" : "muted";
}

// A distance in R$: money still missing is a Deficit (blue), money above the Ideal a Surplus (amber).
export function deficitTone(value: number): SignedTone {
  return value > 0 ? "deficit" : value < 0 ? "surplus" : "muted";
}

// A return's direction: positive is green, negative is red, and a zero or absent read is neutral.
export function returnTone(value: number | undefined): Tone {
  if (value === undefined) return "muted";
  return value > 0 ? "return-up" : value < 0 ? "return-down" : "default";
}
