import type { Position } from "../api/types.ts";
import { formatBRL } from "../lib/money.ts";
import { formatDate } from "../lib/format.ts";

/** Newest positions first; principal / yield / taxes / net columns. */
export function PositionsTable({ positions }: { positions: Position[] }) {
  if (positions.length === 0)
    return <p className="text-sm text-slate-500">Nada consolidado.</p>;

  const rows = [...positions].reverse().slice(0, 30);

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm tabular-nums">
        <thead>
          <tr className="text-left text-xs uppercase tracking-wide text-slate-500">
            <th className="py-2 pr-4 font-medium">Data</th>
            <th className="py-2 pr-4 text-right font-medium">Principal</th>
            <th className="py-2 pr-4 text-right font-medium">Rendimento</th>
            <th className="py-2 pr-4 text-right font-medium">Impostos</th>
            <th className="py-2 text-right font-medium">Líquido</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-white/5">
          {rows.map((p) => (
            <tr key={p.date}>
              <td className="py-2 pr-4 text-slate-400">{formatDate(p.date)}</td>
              <td className="py-2 pr-4 text-right text-principal">
                {formatBRL(p.principal)}
              </td>
              <td className="py-2 pr-4 text-right text-yield">
                {formatBRL(p.yield)}
              </td>
              <td className="py-2 pr-4 text-right text-tax">
                −{formatBRL(p.taxes)}
              </td>
              <td className="py-2 text-right font-medium text-slate-200">
                {formatBRL(p.principal + p.yield - p.taxes)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
