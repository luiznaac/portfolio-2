import { useState } from "react";
import { Link } from "react-router-dom";
import {
  useApplyLegacyAttribution,
  useCurrentAllocation,
  useLegacyAttributionPreview,
} from "../api/queries.ts";
import type {
  Allocation as AllocationModel,
  LegacyAttributionPreview,
  LegacyAttributionResult,
  LegacyAttributionSkip,
} from "../api/types.ts";
import { Button } from "../components/Button.tsx";
import { ClassifyTickerModal } from "../components/ClassifyTickerModal.tsx";
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
import { formatCurrency, formatNumber } from "../lib/format.ts";
import {
  noBucketSkips,
  outsideCustody,
  previewAddCount,
  previewTotalValue,
  shouldShowBanner,
  skipAction,
  skipReasonLabel,
} from "../lib/legacyAttribution.ts";
import { assetClassLabel, monthPeriodLabel } from "../lib/month.ts";

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
  const [classifying, setClassifying] = useState<string | null>(null);
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
    <>
      <div className="grid grid-cols-1 items-start gap-4 lg:grid-cols-[1fr_300px]">
        <div className="flex flex-col gap-4">
          <LegacyAttributionBanner allocation={allocation} />

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
                    onClassify={setClassifying}
                  />
                ))}
              </tbody>
            </table>
          </div>

          <Reconciliation allocation={allocation} />
        </div>

        <CapitalPanel allocation={allocation} />
      </div>

      {classifying && (
        <ClassifyTickerModal ticker={classifying} onClose={() => setClassifying(null)} />
      )}
    </>
  );
}

const INDENT = ["pl-3", "pl-6", "pl-9", "pl-12"];

function AllocationTableRow({
  row,
  expanded,
  maxDeficit,
  maxDrift,
  onToggle,
  onClassify,
}: {
  row: AllocationRow;
  expanded: boolean;
  maxDeficit: number;
  maxDrift: number;
  onToggle: (key: string) => void;
  onClassify: (ticker: string) => void;
}) {
  const ticker = row.ticker;

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
          {row.kind === "unclassified_ticker" && ticker !== undefined && (
            <button
              type="button"
              onClick={() => onClassify(ticker)}
              className="rounded-sm border border-line px-1.5 py-0.5 font-mono text-[10px] text-ink-dim hover:border-surface-3 hover:text-ink"
            >
              Classificar
            </button>
          )}
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

// The page-level legacy action: a global banner above the tree that previews every outside Position
// and applies the whole fan-out per class. It hides once no outside custody remains; the dialog
// stays mounted while open, so the apply result survives the allocation refetch that hides the banner.
function LegacyAttributionBanner({ allocation }: { allocation: AllocationModel }) {
  const [open, setOpen] = useState(false);
  const show = shouldShowBanner(allocation);
  const preview = useLegacyAttributionPreview(show);

  return (
    <>
      {show && (
        <Panel title="Posições fora da estratégia">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <p className="text-[13px] text-ink-dim">
              {formatCurrency(outsideCustody(allocation))} de custódia fora da estratégia. Atribua
              as posições ao legado de cada classe em um passo.
            </p>
            <Button variant="primary" onClick={() => setOpen(true)} disabled={preview.isLoading}>
              Atribuir posições fora da estratégia ao legado
            </Button>
          </div>
          {preview.isError && (
            <p className="mt-2 text-[12.5px] text-tax">{preview.error.message}</p>
          )}
        </Panel>
      )}

      {open && preview.data && (
        <LegacyAttributionDialog preview={preview.data} onClose={() => setOpen(false)} />
      )}
    </>
  );
}

// Preview-then-apply: the dry-run lists every add and skip; nothing is written until Confirmar.
function LegacyAttributionDialog({
  preview,
  onClose,
}: {
  preview: LegacyAttributionPreview;
  onClose: () => void;
}) {
  const apply = useApplyLegacyAttribution();
  const adds = previewAddCount(preview);

  return (
    <dialog
      open
      aria-modal="true"
      aria-label="Atribuir ao legado"
      className="fixed inset-0 z-50 m-0 flex h-full w-full max-w-none items-center justify-center border-0 bg-transparent p-0"
    >
      <button
        type="button"
        aria-label="Fechar pelo fundo"
        onClick={onClose}
        className="absolute inset-0 bg-ground/70"
      />
      <div className="relative max-h-[85vh] w-[560px] max-w-full space-y-4 overflow-y-auto rounded-md border border-line bg-surface p-4">
        <h3 className="font-cond text-[16px] font-bold">Atribuir ao legado</h3>

        {apply.data ? (
          <LegacyAttributionResultBody result={apply.data} />
        ) : (
          <>
            <p className="text-[12.5px] text-ink-dim">
              {adds} {adds === 1 ? "posição" : "posições"} ·{" "}
              {formatCurrency(previewTotalValue(preview))}. Nada muda até confirmar.
            </p>

            {preview.groups.map((group) => (
              <div key={group.asset_class} className="space-y-1.5">
                <p className="font-mono text-[11px] tracking-[.06em] text-ink-faint uppercase">
                  {assetClassLabel(group.asset_class)} → {group.strategy_name}
                </p>
                <table className="w-full text-[13px]">
                  <tbody>
                    {group.adds.map((add) => (
                      <tr key={add.ticker} className="border-t border-line-soft">
                        <td className="py-1.5 font-mono">{add.ticker}</td>
                        <td className="py-1.5 text-right font-mono">
                          {formatNumber(add.quantity)} un
                        </td>
                        <td className="py-1.5 text-right font-mono">{formatCurrency(add.value)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ))}

            {preview.skips.length > 0 && (
              <div className="space-y-1.5">
                <p className="font-mono text-[11px] tracking-[.06em] text-ink-faint uppercase">
                  Não serão atribuídas
                </p>
                <SkipList skips={preview.skips} />
              </div>
            )}
          </>
        )}

        {apply.error && <p className="text-[12.5px] text-tax">{apply.error.message}</p>}

        <div className="flex justify-end gap-2">
          {apply.data ? (
            <Button variant="primary" onClick={onClose}>
              Fechar
            </Button>
          ) : (
            <>
              <Button variant="ghost" onClick={onClose}>
                Cancelar
              </Button>
              <Button
                variant="primary"
                onClick={() => apply.mutate()}
                disabled={apply.isPending || adds === 0}
              >
                {apply.isPending ? "Atribuindo…" : "Confirmar"}
              </Button>
            </>
          )}
        </div>
      </div>
    </dialog>
  );
}

function LegacyAttributionResultBody({ result }: { result: LegacyAttributionResult }) {
  const attributed = result.attributed.length;

  return (
    <div className="space-y-2 text-[13px]">
      <p>
        {attributed} {attributed === 1 ? "posição atribuída" : "posições atribuídas"} ao legado.
      </p>
      {attributed > 0 && (
        <ul className="space-y-1">
          {result.attributed.map((applied) => (
            <li
              key={applied.ticker}
              className="flex items-center justify-between gap-3 text-[12.5px]"
            >
              <span className="font-mono">{applied.ticker}</span>
              <span className="text-ink-dim">{applied.strategy_name}</span>
              <span className="font-mono">
                {formatNumber(applied.quantity)} un · {formatCurrency(applied.value)}
              </span>
            </li>
          ))}
        </ul>
      )}
      {result.skipped.length > 0 && (
        <div className="space-y-1.5">
          <p className="text-ink-dim">
            {result.skipped.length}{" "}
            {result.skipped.length === 1 ? "posição não atribuída" : "posições não atribuídas"}.
          </p>
          <SkipList skips={result.skipped} />
        </div>
      )}
    </div>
  );
}

function SkipList({ skips }: { skips: LegacyAttributionSkip[] }) {
  return (
    <ul className="space-y-1">
      {skips
        .filter((skip) => skip.reason !== "NO_BUCKET")
        .map((skip) => (
          <li key={skip.ticker} className="flex items-center justify-between gap-3 text-[12.5px]">
            <span className="font-mono">{skip.ticker}</span>
            <span className="text-ink-dim">{skipReasonLabel(skip.reason)}</span>
            <SkipLink skip={skip} />
          </li>
        ))}
      {noBucketSkips(skips).map((group) => (
        <li
          key={`no-bucket-${group.assetClass ?? "unknown"}`}
          className="flex items-center justify-between gap-3 text-[12.5px]"
        >
          <span className="font-mono">
            {group.assetClass ? assetClassLabel(group.assetClass) : "—"}
          </span>
          <span className="text-ink-dim">Sem estratégia manual · {group.tickers.join(", ")}</span>
          <Link to="/strategies" className="text-principal hover:underline">
            Criar estratégia
          </Link>
        </li>
      ))}
    </ul>
  );
}

function SkipLink({ skip }: { skip: LegacyAttributionSkip }) {
  const action = skipAction(skip);
  if (action) {
    return (
      <Link to={action.to} className="text-principal hover:underline">
        {action.label}
      </Link>
    );
  }
  return <span className="text-ink-faint">{skip.message ?? "—"}</span>;
}
