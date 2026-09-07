import type { Position } from "../api/types.ts";

/** Sum many per-product position series into one portfolio-wide series by date. */
export function mergePositions(series: Position[][]): Position[] {
  const byDate = new Map<string, Position>();
  for (const list of series) {
    for (const p of list) {
      const acc = byDate.get(p.date);
      if (acc) {
        acc.principal += p.principal;
        acc.yield += p.yield;
        acc.taxes += p.taxes;
      } else {
        byDate.set(p.date, { ...p });
      }
    }
  }
  return [...byDate.values()].sort((a, b) => a.date.localeCompare(b.date));
}

export function lastPosition(list: Position[]): Position | undefined {
  return list.length ? list[list.length - 1] : undefined;
}

export const emptyTotals = { principal: 0, yield: 0, taxes: 0 };

export function sumTotals(positions: (Position | undefined)[]) {
  return positions.reduce(
    (acc, p) =>
      p
        ? {
            principal: acc.principal + p.principal,
            yield: acc.yield + p.yield,
            taxes: acc.taxes + p.taxes,
          }
        : acc,
    { ...emptyTotals },
  );
}
