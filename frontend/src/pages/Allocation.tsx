import { useState } from "react";
import { Link } from "react-router-dom";
import {
  useApplyLegacyAttribution,
  useAttribution,
  useCurrentAllocation,
  useLegacyAttributionPreview,
  useOrderPlan,
  useStrategies,
  useStrategyEditions,
  useTransferAttribution,
} from "../api/queries.ts";
import type {
  Allocation as AllocationModel,
  AssetClass,
  Edition,
  LegacyAttributionPreview,
  LegacyAttributionResult,
  LegacyAttributionSkip,
} from "../api/types.ts";
import { Button } from "../components/Button.tsx";
import { DataQualityPanel } from "../components/DataQualityPanel.tsx";
import { Field, Input } from "../components/Field.tsx";
import { Panel } from "../components/Panel.tsx";
import {
  type AllocationRow,
  actualIdealBar,
  allocationRows,
  distanceText,
  formatPoints,
  formatTarget,
  unattributedShare,
} from "../lib/allocation.ts";
import {
  defaultTransferQuantity,
  isValidTransferQuantity,
  transferDestinations,
} from "../lib/attribution.ts";
import { cn } from "../lib/cn.ts";
import { formatCurrency, formatIsoDate, formatNumber } from "../lib/format.ts";
import {
  attributionValueLabel,
  noBucketSkips,
  outsideCustody,
  previewAddCount,
  previewTotalValue,
  previewUnpricedCount,
  shouldShowBanner,
  skipAction,
  skipReasonLabel,
} from "../lib/legacyAttribution.ts";
import { assetClassLabel, monthPeriodLabel } from "../lib/month.ts";
import { ROUTES, positionDetail, strategyDetail } from "../lib/routes.ts";
import { deficitTone, driftTone, toneClass } from "../lib/tones.ts";

const HEADER =
  "sticky top-0 z-[1] h-[var(--height-row)] bg-panel-2 px-3 font-mono text-label font-semibold tracking-[.1em] text-faint uppercase whitespace-nowrap";
const CELL = "h-[var(--height-row)] border-t border-line-soft px-3 align-middle";

// The Alocação tree: the open month's Capital → class → strategy/sub-class → ticker chain, each rung
// with its Target, Ideal, Actual, worded distance, Drift and an Actual × Ideal bar. A class is
// additive — its report Strategies, its manual bucket and its outside row sum to the class Actual —
// and the unclassified line follows at the root in no class rung. A manual bucket has no target, so
// its target columns read "—" and it draws no bar.
export function Allocation() {
  const allocation = useCurrentAllocation();

  return (
    <section>
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

// The manual transfer's open state: the bucket ticker leaf it starts from and the class whose manual
// Strategy gives up the units. The modal mounts only while open, so its reads start on demand.
interface TransferTarget {
  ticker: string;
  assetClass: AssetClass;
}

// The expansion keys are the row keys from lib/allocation, so the tree and its toggles agree.
function AllocationTree({ allocation }: { allocation: AllocationModel }) {
  const [expanded, setExpanded] = useState<ReadonlySet<string>>(() => new Set());
  const [transfer, setTransfer] = useState<TransferTarget | null>(null);
  const rows = allocationRows(allocation, expanded);

  function openTransfer(row: AllocationRow) {
    if (row.kind !== "manual_ticker" || row.assetClass === undefined) return;
    const manual = allocation.classes.find((cls) => cls.asset_class === row.assetClass)?.manual;
    if (manual) {
      setTransfer({ ticker: row.label, assetClass: row.assetClass });
    }
  }

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
      {/* The tree owns the full page width: seven columns (including the Actual × Ideal bar) do not
          fit beside a 300 px rail on a 1280 px screen, and a clipped bar is a missing read. The
          supporting panels sit below, two-up. */}
      <div className="flex flex-col gap-4">
        <LegacyAttributionBanner allocation={allocation} />

        <div
          data-testid="allocation-tree"
          className="overflow-x-auto rounded-panel border border-line"
        >
          <table className="w-full border-collapse text-num whitespace-nowrap">
            <thead>
              <tr>
                <th className={cn(HEADER, "text-left")}>Nível</th>
                <th className={cn(HEADER, "text-right")}>Alvo</th>
                <th className={cn(HEADER, "text-right")}>Ideal</th>
                <th className={cn(HEADER, "text-right")}>Atual</th>
                <th className={cn(HEADER, "text-right")}>Déficit / distância</th>
                <th className={cn(HEADER, "text-right")}>Δ p.p.</th>
                <th className={cn(HEADER, "text-right")}>Atual × Ideal</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <AllocationTableRow
                  key={row.key}
                  row={row}
                  expanded={expanded.has(row.key)}
                  onToggle={toggle}
                  onTransfer={openTransfer}
                />
              ))}
            </tbody>
          </table>
        </div>

        <div className="grid grid-cols-1 items-start gap-4 lg:grid-cols-2">
          <DataQualityPanel allocation={allocation} />
          <CapitalPanel allocation={allocation} />
        </div>

        <Reconciliation allocation={allocation} />
      </div>

      {transfer && (
        <TransferModal
          ticker={transfer.ticker}
          assetClass={transfer.assetClass}
          onClose={() => setTransfer(null)}
        />
      )}
    </>
  );
}

const INDENT = ["pl-3", "pl-6", "pl-9", "pl-12"];

function AllocationTableRow({
  row,
  expanded,
  onToggle,
  onTransfer,
}: {
  row: AllocationRow;
  expanded: boolean;
  onToggle: (key: string) => void;
  onTransfer: (row: AllocationRow) => void;
}) {
  const ticker = row.ticker;
  // Only a Strategy rung (report-driven or manual) has a detail to link; a bucket without a catalog
  // id stays plain text rather than linking to a guessed route.
  const detail =
    (row.kind === "strategy" || row.kind === "manual") && row.strategyId !== undefined
      ? strategyDetail(row.strategyId)
      : undefined;

  return (
    <tr className="bg-panel">
      <td className={cn(CELL, INDENT[row.depth])}>
        <span className="inline-flex items-center gap-1.5">
          {row.expandable ? (
            <button
              type="button"
              aria-expanded={expanded}
              aria-label={`${expanded ? "Recolher" : "Expandir"} ${row.label}`}
              onClick={() => onToggle(row.key)}
              className="font-mono text-[10px] text-faint hover:text-text"
            >
              {expanded ? "▾" : "▸"}
            </button>
          ) : (
            <span className="inline-block w-[8px]" aria-hidden="true" />
          )}
          {detail !== undefined ? (
            <Link
              to={detail}
              className={cn(
                "font-mono hover:text-action hover:underline",
                row.kind === "manual" && "text-muted",
              )}
            >
              {row.label}
            </Link>
          ) : (
            <span
              className={cn(
                row.kind === "capital" ? "font-cond font-bold" : "font-mono",
                row.kind === "class" && "font-cond font-semibold",
              )}
            >
              {row.label}
            </span>
          )}
          {row.kind === "strategy" && (
            <span className="font-mono text-label text-faint">· relatório</span>
          )}
          {row.kind === "manual" && (
            <span className="font-mono text-label text-faint">· manual, sem alvo</span>
          )}
          {row.partial && (
            <span
              data-testid="allocation-partial"
              className="rounded-xs border border-surplus px-1.5 py-0.5 font-mono text-[9.5px] tracking-[.04em] text-surplus uppercase"
            >
              parcial
            </span>
          )}
          {row.stale && row.quoteDate !== undefined && (
            <span
              data-testid="allocation-stale"
              className="font-mono text-[10px] text-attention"
              title="Cotação desatualizada"
            >
              desatualizada · {formatIsoDate(row.quoteDate)}
            </span>
          )}
          {row.kind === "manual_ticker" && (
            <button
              type="button"
              aria-label={`Transferir ${row.label}`}
              onClick={() => onTransfer(row)}
              className="font-mono text-[10px] text-action hover:underline"
            >
              Transferir
            </button>
          )}
          {row.kind === "unclassified_ticker" && ticker !== undefined && (
            <Link
              to={positionDetail(ticker)}
              className="rounded-xs border border-line px-1.5 py-0.5 font-mono text-[10px] text-muted hover:border-panel-3 hover:text-text"
            >
              Classificar
            </Link>
          )}
        </span>
      </td>
      <td className={cn(CELL, "text-right text-muted")}>{formatTarget(row.target)}</td>
      <td className={cn(CELL, "text-right font-mono")}>
        {row.ideal === undefined ? "—" : formatCurrency(row.ideal)}
      </td>
      <td className={cn(CELL, "text-right font-mono")}>
        {row.actual === undefined ? (
          <span className="text-muted">—</span>
        ) : (
          formatCurrency(row.actual)
        )}
      </td>
      <td className={cn(CELL, "text-right")}>
        {row.deficit === undefined ? (
          <span className="font-mono text-muted">—</span>
        ) : (
          <span className={cn("font-mono", toneClass(deficitTone(row.deficit)))}>
            {distanceText(row.deficit)}
          </span>
        )}
      </td>
      <td className={cn(CELL, "text-right")}>
        {row.drift === undefined ? (
          <span className="font-mono text-muted">—</span>
        ) : (
          <span className={cn("font-mono", toneClass(driftTone(row.drift)))}>
            {formatPoints(row.drift)}
          </span>
        )}
      </td>
      <td className={cn(CELL, "text-right")}>
        <ActualIdealBar actual={row.actual} ideal={row.ideal} />
      </td>
    </tr>
  );
}

const FIELD = "rounded-sm border border-line bg-surface-2 px-2 py-1.5 text-[13px] text-ink";

// The manual Attribution Transfer out of the bucket: the destination list is the report-driven
// Strategies of the same class whose current Edition weighs the ticker, and the quantity defaults to
// the receiver's whole-unit shortfall, capped by the bucket slice. It applies immediately — no
// inbox row, no open month required by the endpoint.
function TransferModal({
  ticker,
  assetClass,
  onClose,
}: {
  ticker: string;
  assetClass: AssetClass;
  onClose: () => void;
}) {
  const strategies = useStrategies();
  const plan = useOrderPlan();
  const attribution = useAttribution(ticker);
  const transfer = useTransferAttribution(ticker);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [quantityInput, setQuantityInput] = useState<string | null>(null);

  const catalog = strategies.data ?? [];
  const manual = catalog.find(
    (strategy) => strategy.kind === "MANUAL" && strategy.asset_class === assetClass,
  );
  const reportStrategies = catalog.filter(
    (strategy) => strategy.kind === "REPORT_DRIVEN" && strategy.asset_class === assetClass,
  );
  const editions = useStrategyEditions(reportStrategies);
  const editionsLoading = editions.some((query) => query.isLoading);
  const editionsById = new Map<number, Edition[]>();
  editions.forEach((query, index) => {
    if (query.data) editionsById.set(reportStrategies[index].id, query.data);
  });

  const destinations = transferDestinations(
    reportStrategies,
    editionsById,
    assetClass,
    ticker,
    plan.data,
  );
  const selected =
    destinations.find((destination) => destination.strategyId === selectedId) ?? destinations[0];
  const bucketSlice = attribution.data?.slices.find((slice) => slice.strategy_id === manual?.id);
  const bucketQuantity = bucketSlice?.quantity;
  const defaultQuantity =
    selected && bucketQuantity !== undefined
      ? defaultTransferQuantity(selected, bucketQuantity)
      : 0;
  const quantity = quantityInput === null ? defaultQuantity : Number(quantityInput);
  const canSubmit =
    manual !== undefined &&
    selected !== undefined &&
    bucketQuantity !== undefined &&
    isValidTransferQuantity(quantity, bucketQuantity) &&
    !transfer.isPending;

  async function submit() {
    if (manual === undefined || selected === undefined) return;
    try {
      await transfer.mutateAsync({
        from_strategy_id: manual.id,
        to_strategy_id: selected.strategyId,
        quantity,
      });
      onClose();
    } catch {
      // The mutation error is rendered inline; the modal stays open for a retry.
    }
  }

  return (
    <dialog
      open
      aria-modal="true"
      aria-label="Transferir do legado"
      className="fixed inset-0 z-50 m-0 flex h-full w-full max-w-none items-center justify-center border-0 bg-transparent p-0"
    >
      <button
        type="button"
        aria-label="Fechar"
        onClick={onClose}
        className="absolute inset-0 bg-ground/70"
      />
      <div className="relative w-[420px] max-w-full space-y-4 rounded-md border border-line bg-surface p-4">
        <h3 className="font-cond text-[16px] font-bold">Transferir do legado</h3>

        <Field label="Ativo">
          <Input value={ticker} readOnly />
        </Field>

        {strategies.isLoading || attribution.isLoading || editionsLoading || plan.isLoading ? (
          <p className="text-[13px] text-ink-dim">Carregando…</p>
        ) : strategies.isError || attribution.isError ? (
          <p className="text-[12.5px] text-tax">Não foi possível carregar a transferência.</p>
        ) : manual === undefined ? (
          <p className="text-[12.5px] text-tax">A estratégia manual não foi encontrada.</p>
        ) : destinations.length === 0 ? (
          <p data-testid="transfer-empty" className="text-[12.5px] text-ink-dim">
            Nenhuma estratégia com relatório da classe {assetClassLabel(assetClass)} tem uma edição
            ativa que atribua {ticker}.{" "}
            <Link to={ROUTES.strategies} className="text-principal hover:underline">
              Importe uma edição
            </Link>{" "}
            para transferir.
          </p>
        ) : (
          <>
            <Field label="Estratégia de destino">
              <select
                data-testid="transfer-destination"
                value={selected?.strategyId ?? ""}
                onChange={(event) => {
                  setSelectedId(Number(event.target.value));
                  setQuantityInput(null);
                }}
                className={FIELD}
              >
                {destinations.map((destination) => (
                  <option key={destination.strategyId} value={destination.strategyId}>
                    {destination.shortfall === undefined
                      ? destination.strategyName
                      : `${destination.strategyName} · falta ${formatNumber(destination.shortfall)} cotas`}
                  </option>
                ))}
              </select>
            </Field>

            <Field label="Quantidade">
              <Input
                data-testid="transfer-quantity"
                type="number"
                min="1"
                max={bucketQuantity}
                step="1"
                value={quantityInput ?? (defaultQuantity > 0 ? String(defaultQuantity) : "")}
                onChange={(event) => setQuantityInput(event.target.value)}
              />
            </Field>

            <p data-testid="transfer-hint" className="text-[11.5px] text-ink-faint">
              {formatNumber(bucketQuantity ?? 0)} cotas no legado. A custódia não muda: só a
              atribuição.
            </p>
          </>
        )}

        {transfer.error && <p className="text-[12.5px] text-tax">{transfer.error.message}</p>}

        <div className="flex justify-end gap-2">
          <Button variant="ghost" onClick={onClose}>
            Cancelar
          </Button>
          <Button variant="primary" onClick={submit} disabled={!canSubmit}>
            {transfer.isPending ? "Transferindo…" : `Transferir ${formatNumber(quantity)} cotas`}
          </Button>
        </div>
      </div>
    </dialog>
  );
}

// The console's Actual × Ideal bar: the Actual fills from the left, the Ideal is a hairline marker,
// and the track scales to the larger of the two — so a short rung reads blue and an excess amber
// without either leaving the track. The worded distance beside it carries the same meaning in words.
function ActualIdealBar({
  actual,
  ideal,
}: {
  actual: number | undefined;
  ideal: number | undefined;
}) {
  if (actual === undefined || ideal === undefined) return null;
  const bar = actualIdealBar(actual, ideal);
  if (bar === null) return null;

  return (
    <span
      data-testid="actual-ideal-bar"
      className="relative inline-block h-1 w-[74px] rounded-xs bg-line-soft align-middle"
    >
      <span
        className={cn(
          "absolute inset-y-0 left-0 rounded-xs",
          actual > ideal ? "bg-surplus" : actual < ideal ? "bg-deficit" : "bg-muted",
        )}
        style={{ width: `${bar.actual}%` }}
      />
      <span
        aria-hidden="true"
        className="absolute -top-0.5 -bottom-0.5 w-px bg-text opacity-65"
        style={{ left: `${bar.ideal}%` }}
      />
    </span>
  );
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
        <Kv
          label="Atual"
          value={
            allocation.partial
              ? `${formatCurrency(allocation.actual)} · parcial`
              : formatCurrency(allocation.actual)
          }
        />
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
  const unpriced = previewUnpricedCount(preview);

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
              {adds > 0 && unpriced === adds ? "—" : formatCurrency(previewTotalValue(preview))}
              {unpriced > 0 ? ` · ${unpriced} sem cotação` : ""}. Nada muda até confirmar.
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
                        <td className="py-1.5 text-right font-mono">
                          {attributionValueLabel(add.value)}
                        </td>
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
                {formatNumber(applied.quantity)} un · {attributionValueLabel(applied.value)}
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
          <Link to={ROUTES.strategies} className="text-principal hover:underline">
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
