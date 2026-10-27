import { useState } from "react";
import { useCurrentAllocation } from "../api/queries.ts";
import type { Allocation as AllocationModel } from "../api/types.ts";
import { Panel } from "../components/Panel.tsx";
import {
  type AllocationRow,
  allocationRows,
  barWidth,
  formatPoints,
  formatTarget,
  maxAbsDeficit,
  maxAbsDrift,
  unattributedShare,
} from "../lib/allocation.ts";
import { cn } from "../lib/cn.ts";
import { formatCurrency } from "../lib/format.ts";
import { monthPeriodLabel } from "../lib/month.ts";

const HEADER =
  "sticky top-0 z-[1] bg-surface-2 px-3 py-2.5 font-mono text-[10px] font-medium tracking-[.09em] text-ink-faint uppercase whitespace-nowrap";
const CELL = "border-t border-line-soft px-3 py-2.5 align-middle";

// The Alocação tree: the open month's Capital → class → strategy/sub-class → ticker chain, each rung
// with its Ideal, Actual, Δ R$ (deficit) and Δ p.p. (drift) bar. A class is additive — its report
// Strategies, its manual bucket and its outside row sum to the class Actual — and the unclassified
// line sits at the root in no class rung.
export function Allocation() {
  const allocation = useCurrentAllocation();

  return (
    <section>
      <h1 className="mb-4 font-cond text-[24px] font-bold tracking-[.005em]">Alocação</h1>

      {allocation.isLoading ? (
        <p className="text-[13.5px] text-ink-dim">Carregando…</p>
      ) : allocation.isError ? (
        <p className="text-[13.5px] text-tax">{allocation.error.message}</p>
      ) : allocation.data ? (
        <AllocationTree key={allocation.data.period ?? "month-free"} allocation={allocation.data} />
      ) : null}
    </section>
  );
}

// The expansion keys are the row keys from lib/allocation, so the tree and its toggles agree.
function AllocationTree({ allocation }: { allocation: AllocationModel }) {
  const [expanded, setExpanded] = useState<ReadonlySet<string>>(() => new Set());
  const rows = allocationRows(allocation, expanded);
  const maxDeficit = maxAbsDeficit(rows);
  const maxDrift = maxAbsDrift(rows);

  function toggle(key: string) {
    setExpanded((current) => {
      const next = new Set(current);
      if (next.has(key)) {
        next.delete(key);
      } else {
        next.add(key);
      }
      return next;
    });
  }

  return (
    <div className="grid grid-cols-1 items-start gap-4 lg:grid-cols-[1fr_300px]">
      <div className="flex flex-col gap-4">
        <div
          data-testid="allocation-tree"
          className="overflow-x-auto rounded-lg border border-line"
        >
          <table className="w-full border-collapse text-[13px]">
            <thead>
              <tr>
                <th className={cn(HEADER, "text-left")}>Nível</th>
                <th className={cn(HEADER, "text-right")}>Alvo</th>
                <th className={cn(HEADER, "text-right")}>Ideal</th>
                <th className={cn(HEADER, "text-right")}>Atual</th>
                <th className={cn(HEADER, "text-center")}>Δ R$</th>
                <th className={cn(HEADER, "text-right")}>Δ p.p.</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <AllocationTableRow
                  key={row.key}
                  row={row}
                  expanded={expanded.has(row.key)}
                  maxDeficit={maxDeficit}
                  maxDrift={maxDrift}
                  onToggle={toggle}
                />
              ))}
            </tbody>
          </table>
        </div>

        <Reconciliation allocation={allocation} />
      </div>

      <CapitalPanel allocation={allocation} />
    </div>
  );
}

const INDENT = ["pl-3", "pl-6", "pl-9", "pl-12"];

function AllocationTableRow({
  row,
  expanded,
  maxDeficit,
  maxDrift,
  onToggle,
}: {
  row: AllocationRow;
  expanded: boolean;
  maxDeficit: number;
  maxDrift: number;
  onToggle: (key: string) => void;
}) {
  return (
    <tr className="bg-surface">
      <td className={cn(CELL, INDENT[row.depth])}>
        <span className="inline-flex items-center gap-1.5">
          {row.expandable ? (
            <button
              type="button"
              aria-expanded={expanded}
              onClick={() => onToggle(row.key)}
              className="font-mono text-[10px] text-ink-faint hover:text-ink"
            >
              {expanded ? "▾" : "▸"}
            </button>
          ) : (
            <span className="inline-block w-[8px]" aria-hidden="true" />
          )}
          <span
            className={cn(
              row.kind === "capital" ? "font-cond font-bold" : "font-mono",
              row.kind === "class" && "font-cond font-semibold",
            )}
          >
            {row.label}
          </span>
        </span>
      </td>
      <td className={cn(CELL, "text-right text-ink-dim")}>{formatTarget(row.target)}</td>
      <td className={cn(CELL, "text-right font-mono")}>
        {row.ideal === undefined ? "—" : formatCurrency(row.ideal)}
      </td>
      <td className={cn(CELL, "text-right font-mono")}>{formatCurrency(row.actual)}</td>
      <td className={cn(CELL, "text-center")}>
        {row.deficit === undefined ? (
          <span className="font-mono text-ink-dim">—</span>
        ) : (
          <div className="flex items-center justify-center gap-2">
            <DivergentBar value={-row.deficit} max={maxDeficit} />
            <span className={cn("min-w-[84px] text-right font-mono", deviationTone(-row.deficit))}>
              {formatDeviation(-row.deficit)}
            </span>
          </div>
        )}
      </td>
      <td className={cn(CELL, "text-right")}>
        {row.drift === undefined ? (
          <span className="font-mono text-ink-dim">—</span>
        ) : (
          <div className="flex items-center justify-end gap-2">
            <DivergentBar value={row.drift} max={maxDrift} />
            <span className={cn("min-w-[72px] text-right font-mono", deviationTone(row.drift))}>
              {formatPoints(row.drift)}
            </span>
          </div>
        )}
      </td>
    </tr>
  );
}

// A divergent bar fills from a centre axis. `value` is the deviation Actual − Ideal (the reference's
// Δ): above ideal fills right in the "excesso · vender" amber, below ideal left in the "falta ·
// comprar" blue — the same reading for the R$ column and the p.p. one.
function DivergentBar({ value, max }: { value: number; max: number }) {
  const width = barWidth(value, max);
  const excess = value >= 0;

  return (
    <div className="relative h-3.5 w-[120px]">
      <div className="absolute top-[-2px] bottom-[-2px] left-1/2 w-px bg-line" />
      {width > 0 && (
        <div
          className={cn(
            "absolute top-0 bottom-0",
            excess ? "left-1/2 bg-accent" : "right-1/2 bg-principal",
          )}
          style={{ width: `${width}%` }}
        />
      )}
    </div>
  );
}

function deviationTone(deviation: number): string {
  if (Math.abs(deviation) < 0.5) return "text-ink-dim";
  return deviation > 0 ? "text-accent" : "text-principal";
}

// The reference's Δ R$ renders the signed deviation; near-zero reads as a dash.
function formatDeviation(deviation: number): string {
  if (Math.abs(deviation) < 0.5) return "—";
  return `${deviation > 0 ? "+" : "−"}${formatCurrency(Math.abs(deviation))}`;
}

// The reconciliation bar's hatched segment is the listed value no strategy claims.
function Reconciliation({ allocation }: { allocation: AllocationModel }) {
  const share = unattributedShare(allocation);
  const unattributed = Math.round(share * 100);
  const attributed = 100 - unattributed;

  return (
    <Panel title="Conciliação">
      <div className="flex h-[26px] overflow-hidden rounded-md border border-line">
        <div
          className="flex h-full items-center justify-center bg-surface-3 font-mono text-[10px] font-semibold text-ink-dim"
          style={{ width: `${attributed}%` }}
        >
          {attributed > 12 ? `${attributed}% atribuído` : ""}
        </div>
        {unattributed > 0 && (
          <div
            className="flex h-full items-center justify-center bg-tax-bg font-mono text-[10px] font-semibold text-tax"
            style={{
              width: `${unattributed}%`,
              backgroundImage:
                "repeating-linear-gradient(45deg, var(--color-tax) 0 6px, transparent 6px 12px)",
            }}
          >
            {unattributed > 12 ? `${unattributed}%` : ""}
          </div>
        )}
      </div>
      <p className="mt-2 text-[11.5px] text-ink-faint">
        {formatCurrency(allocation.unattributed)} de custódia fora de qualquer estratégia.
      </p>
    </Panel>
  );
}

function CapitalPanel({ allocation }: { allocation: AllocationModel }) {
  // No month means no Capital, Floor or drift to show: the panel stays empty rather than reporting
  // zeroes for a plan that does not exist.
  if (allocation.period === undefined) {
    return <Panel title="Capital do mês" />;
  }

  return (
    <Panel title="Capital do mês">
      <dl className="space-y-2 text-[13px]">
        <Kv label="Período" value={monthPeriodLabel(allocation.period)} />
        <Kv label="Capital" value={formatCurrency(allocation.capital)} />
        <Kv label="Reserva (piso)" value={formatCurrency(allocation.floor)} />
        <Kv label="Atual" value={formatCurrency(allocation.actual)} />
        <Kv label="Drift total" value={formatPoints(allocation.drift)} />
      </dl>
    </Panel>
  );
}

function Kv({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-3 border-b border-line-soft pb-2 last:border-0">
      <dt className="text-ink-faint">{label}</dt>
      <dd className="font-mono">{value}</dd>
    </div>
  );
}
