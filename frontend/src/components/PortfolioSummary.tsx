import { formatBRL } from "../lib/money.ts";

interface Totals {
  principal: number;
  yield: number;
  taxes: number;
}

/** Three headline tiles: net worth, accrued yield, taxes owed on redemption. */
export function PortfolioSummary({ totals }: { totals: Totals }) {
  const net = totals.principal + totals.yield - totals.taxes;

  return (
    <div className="grid gap-4 sm:grid-cols-3">
      <Tile label="Patrimônio líquido" value={formatBRL(net)} accent="text-slate-100" big />
      <Tile label="Rendimento acumulado" value={formatBRL(totals.yield)} accent="text-yield" />
      <Tile label="Impostos no resgate" value={`−${formatBRL(totals.taxes)}`} accent="text-tax" />
    </div>
  );
}

function Tile({
  label,
  value,
  accent,
  big,
}: {
  label: string;
  value: string;
  accent: string;
  big?: boolean;
}) {
  return (
    <div className="rounded-xl border border-white/10 bg-slate-900/50 p-4">
      <div className="text-xs uppercase tracking-wide text-slate-500">{label}</div>
      <div className={`mt-1 tabular-nums font-semibold ${accent} ${big ? "text-2xl" : "text-xl"}`}>
        {value}
      </div>
    </div>
  );
}
