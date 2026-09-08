const brl = new Intl.NumberFormat("pt-BR", {
  style: "currency",
  currency: "BRL",
});

const brlCompact = new Intl.NumberFormat("pt-BR", {
  style: "currency",
  currency: "BRL",
  notation: "compact",
  maximumFractionDigits: 1,
});

const pct = new Intl.NumberFormat("pt-BR", {
  style: "percent",
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

/** R$ 1.234,56 — for display only, never for arithmetic. */
export function formatBRL(n: number): string {
  return brl.format(n);
}

/** R$ 1,2 mil — for tight spaces (tiles, axis labels). */
export function formatBRLCompact(n: number): string {
  return brlCompact.format(n);
}

/** 0.1234 -> "12,34%". Pass a ratio, not a percentage. */
export function formatRatio(n: number): string {
  return pct.format(n);
}
