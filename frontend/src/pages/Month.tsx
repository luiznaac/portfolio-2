import { type FormEvent, type ReactNode, useState } from "react";
import {
  useCurrentMonth,
  useMonthPeriodRange,
  useOpenMonth,
  useOrders,
  useRefreshOrders,
  useReplaceTargets,
  useStrategies,
  useUpdateCurrentMonth,
} from "../api/queries.ts";
import type {
  AssetClass,
  CurrentMonth,
  Month as MonthModel,
  MonthPeriodRange,
  OrderList,
  SuggestedOrder,
  Targets,
} from "../api/types.ts";
import { Button } from "../components/Button.tsx";
import { Chip } from "../components/Chip.tsx";
import { Panel } from "../components/Panel.tsx";
import { PeriodSelect } from "../components/PeriodSelect.tsx";
import { type Column, Table } from "../components/Table.tsx";
import { cn } from "../lib/cn.ts";
import { monthKey, todayIso } from "../lib/dates.ts";
import { pendingTransfers } from "../lib/fechamento.ts";
import { subClassLabel } from "../lib/fixedIncome.ts";
import { formatCurrency, formatNumber, formatPercent } from "../lib/format.ts";
import {
  ASSET_CLASSES,
  type MonthFormState,
  type TargetsFormState,
  addStrategyRow,
  addTickerRow,
  assetClassLabel,
  emptyMonthForm,
  isBackdatedPeriod,
  monthFormFrom,
  monthPeriodLabel,
  monthStatusLabel,
  overplannedStrategyClasses,
  remainingLegacyShares,
  removeStrategyRow,
  removeTickerRow,
  targetsFormFrom,
  toMonthCreation,
  toMonthUpdate,
  toTargets,
} from "../lib/month.ts";
import { orderSideLabel, orderStatusLabel } from "../lib/orders.ts";
import {
  type ChainPreviewRow,
  PLAN_STEPS_TOTAL,
  type PlanStep,
  type PlanStepKey,
  chainPreview,
  doneStepCount,
  firstUnfinishedStep,
  hasPersistedPlan,
  planSteps,
} from "../lib/plan.ts";

const FIELD = "rounded-sm border border-line bg-surface-2 px-2 py-1.5 text-[13px] text-ink";

// Plano / Mês · Mês atual (issue #388): the six-step month, opened on its first unfinished step. The
// rail is the only step list — the body renders the active step's form, and a concluded step
// collapses to its one-line summary with its meta. Steps 4–6 land with their own slices.
export function Month() {
  const current = useCurrentMonth();
  const orders = useOrders();
  const periodRange = useMonthPeriodRange();
  const [switching, setSwitching] = useState(false);
  const currentPeriod = monthKey(todayIso());

  const loading = current.isLoading || (current.data !== null && orders.isLoading);
  const error = current.error ?? (current.data !== null ? orders.error : null);

  return (
    <section>
      {loading || (switching && periodRange.isLoading) ? (
        <p className="text-[13.5px] text-ink-dim">Carregando…</p>
      ) : current.isError || (current.data !== null && orders.isError) ? (
        <p className="text-[13.5px] text-tax">{error?.message ?? "Não foi possível ler o mês."}</p>
      ) : current.data && !switching ? (
        <MonthPlan
          key={current.data.month.id}
          current={current.data}
          orders={orders.data ?? null}
          currentPeriod={currentPeriod}
          onSwitch={() => setSwitching(true)}
        />
      ) : periodRange.isError ? (
        <p className="text-[13.5px] text-tax">{periodRange.error.message}</p>
      ) : periodRange.data ? (
        <MonthOpener
          range={periodRange.data}
          onOpened={() => setSwitching(false)}
          onCancel={current.data ? () => setSwitching(false) : undefined}
        />
      ) : null}
    </section>
  );
}

// The month's frame: status line, the progress rail and the active step. `stale` is the one piece of
// page state the reads cannot derive — the wire records no plan marker, so a capital or targets
// write flags the stored plan stale until POST /orders/plan/refresh rewrites it.
function MonthPlan({
  current,
  orders,
  currentPeriod,
  onSwitch,
}: {
  current: CurrentMonth;
  orders: OrderList | null;
  currentPeriod: string;
  onSwitch: () => void;
}) {
  const [stale, setStale] = useState(false);
  const steps = planSteps({
    month: current.month,
    targets: current.targets,
    orders,
    planStale: stale,
  });
  const first = firstUnfinishedStep(steps);
  const [activeKey, setActiveKey] = useState<PlanStepKey>(first.key);
  const active = steps.find((step) => step.key === activeKey) ?? first;
  const backdated = isBackdatedPeriod(current.month.period, currentPeriod);

  return (
    <div data-testid="month-plan">
      <div className="mb-3 flex flex-wrap items-center gap-2">
        <Chip tone={current.month.status === "OPEN" ? "green" : "neutral"}>
          {monthStatusLabel(current.month.status)}
        </Chip>
        {backdated && <Chip tone="amber">histórico — sem fechamento</Chip>}
        {stale && <Chip tone="amber">plano desatualizado</Chip>}
        <span className="font-cond text-[15px] font-semibold">
          {monthPeriodLabel(current.month.period)}
        </span>
        <span className="text-[13px] text-ink-dim">
          Capital {formatCurrency(current.month.capital)} · Reserva{" "}
          {formatCurrency(current.month.floor)}
        </span>
        <Button variant="ghost" className="ml-auto" onClick={onSwitch}>
          Trocar mês
        </Button>
      </div>

      <div className="space-y-3">
        <PlanRail steps={steps} activeKey={active.key} onSelect={setActiveKey} />

        <div className="flex items-center gap-2">
          {active.key !== first.key && (
            <Button data-testid="plan-retomar" onClick={() => setActiveKey(first.key)}>
              Retomar — {first.label} →
            </Button>
          )}
          <span className="flex-1" />
          <span className="font-mono text-[10.5px] tracking-[.08em] text-ink-faint uppercase">
            etapa {steps.findIndex((step) => step.key === active.key) + 1} de {PLAN_STEPS_TOTAL}
          </span>
        </div>

        <div data-testid={`plan-step-${active.key}`}>
          {active.key === "capital" && (
            <CapitalStep
              month={current.month}
              targets={current.targets}
              onSaved={() => setStale(true)}
            />
          )}
          {active.key === "targets" && (
            <TargetsStep targets={current.targets} onSaved={() => setStale(true)} />
          )}
          {active.key === "plan" && (
            <PlanStepBody orders={orders} stale={stale} onRefreshed={() => setStale(false)} />
          )}
          {(active.key === "execution" || active.key === "transfer" || active.key === "close") && (
            <PendingStep step={active} />
          )}
        </div>
      </div>
    </div>
  );
}

// The rail: the segmented `n de 6` progress and the only step list on the page. A concluded step is
// one row — its label and its one-line meta — never a second copy of the steps in the body.
function PlanRail({
  steps,
  activeKey,
  onSelect,
}: {
  steps: PlanStep[];
  activeKey: PlanStepKey;
  onSelect: (key: PlanStepKey) => void;
}) {
  return (
    <Panel
      title="Etapas do mês"
      action={
        <span className="font-mono text-[11px] text-ink-dim">
          {doneStepCount(steps)} de {PLAN_STEPS_TOTAL}
        </span>
      }
    >
      <div className="mb-2 flex gap-1" data-testid="plan-progress">
        {steps.map((step) => (
          <span
            key={step.key}
            className={cn(
              "h-1 flex-1 rounded-xs",
              step.done ? "bg-return-up" : step.blocked ? "bg-attention" : "bg-line",
            )}
          />
        ))}
      </div>
      <ol className="divide-y divide-line-soft">
        {steps.map((step, index) => (
          <li key={step.key}>
            <button
              type="button"
              data-testid={`plan-step-rail-${step.key}`}
              aria-current={step.key === activeKey ? "step" : undefined}
              onClick={() => onSelect(step.key)}
              className={cn(
                "flex w-full items-center gap-2 rounded-xs px-1.5 py-1.5 text-left",
                step.key === activeKey ? "bg-panel-2" : "hover:bg-panel-2",
              )}
            >
              <span
                className={cn(
                  "grid h-[22px] w-[22px] place-items-center rounded-full border font-mono text-[11px] font-semibold",
                  step.done
                    ? "border-return-up bg-return-up-bg text-return-up"
                    : step.blocked
                      ? "border-attention bg-attention-bg text-attention"
                      : "border-line text-ink-faint",
                )}
              >
                {step.done ? "✓" : index + 1}
              </span>
              <span className="font-cond text-[13.5px] font-semibold">{step.label}</span>
              <span className="flex-1" />
              <span
                className={cn(
                  "font-mono text-[11px]",
                  step.blocked ? "text-attention" : "text-ink-faint",
                )}
              >
                {step.meta}
              </span>
            </button>
          </li>
        ))}
      </ol>
    </Panel>
  );
}

// Step 1 — Capital: the month's one typed number. The preview reads the typed capital, so the chain
// a save would produce is visible before the plan is recalculated.
function CapitalStep({
  month,
  targets,
  onSaved,
}: {
  month: MonthModel;
  targets: Targets;
  onSaved: () => void;
}) {
  const [form, setForm] = useState<MonthFormState>(() => monthFormFrom(month));
  const update = useUpdateCurrentMonth();
  const payload = toMonthUpdate(form);
  const preview = chainPreview(form.capital, targets);
  const investable = payload === null ? null : payload.capital - payload.floor;

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!payload) return;
    update.mutate(payload, { onSuccess: onSaved });
  }

  return (
    <div className="space-y-3">
      <Panel title="Capital do mês">
        <div className="mb-3 divide-y divide-line-soft">
          <MiniRow label="Capital" value={formatCurrency(month.capital)} />
          <MiniRow label="Piso" value={formatCurrency(month.floor)} />
          <MiniRow
            label="Investível — Capital − Piso"
            value={investable === null ? "—" : formatCurrency(investable)}
          />
        </div>
        <form onSubmit={handleSubmit} className="space-y-3">
          <div className="flex flex-wrap gap-3">
            <NumberField
              label="Capital (R$)"
              value={form.capital}
              onChange={(value) => setForm((current) => ({ ...current, capital: value }))}
            />
            <NumberField
              label="Reserva (piso, R$)"
              value={form.floor}
              onChange={(value) => setForm((current) => ({ ...current, floor: value }))}
            />
          </div>

          {update.error && <p className="text-[12.5px] text-tax">{update.error.message}</p>}
          {update.isSuccess && <p className="text-[12.5px] text-yield">Capital atualizado.</p>}

          <Button type="submit" variant="primary" disabled={!payload || update.isPending}>
            {update.isPending ? "Salvando…" : "Salvar capital"}
          </Button>
        </form>
      </Panel>

      {preview.length > 0 && <ChainPreview rows={preview} />}
    </div>
  );
}

const PREVIEW_COLUMNS: Column<ChainPreviewRow>[] = [
  {
    key: "class",
    header: "Classe",
    render: (row) => assetClassLabel(row.assetClass),
  },
  {
    key: "target",
    header: "Alvo",
    align: "right",
    render: (row) => formatPercent(row.target),
  },
  {
    key: "ideal",
    header: "Ideal previsto",
    align: "right",
    render: (row) => formatCurrency(row.ideal),
  },
];

// The chain preview: ideal = typed Capital × the class target, never the stored plan.
function ChainPreview({ rows }: { rows: ChainPreviewRow[] }) {
  return (
    <Panel title="Prévia da cadeia">
      <p className="mb-2 text-[12px] text-ink-dim">
        Ideal = capital × alvo. A prévia usa o capital digitado; o plano guardado só muda ao
        recalcular.
      </p>
      <div data-testid="chain-preview">
        <Table columns={PREVIEW_COLUMNS} rows={rows} rowKey={(row) => row.assetClass} />
      </div>
    </Panel>
  );
}

// Step 3 — Plano calculado: the stored plan as a snapshot. A refresh is the one write that rewrites
// it; the page never recomputes a plan on read.
function PlanStepBody({
  orders,
  stale,
  onRefreshed,
}: {
  orders: OrderList | null;
  stale: boolean;
  onRefreshed: () => void;
}) {
  const refresh = useRefreshOrders();
  const hasPlan = hasPersistedPlan(orders);
  const pending = pendingTransfers(orders);

  return (
    <Panel title="Plano calculado">
      {!hasPlan || orders === null ? (
        <p className="mb-2 text-[13px] text-ink-dim">
          Nenhum plano persistido para este mês. Recalcular lê o capital e a cadeia e grava a lista
          de ordens.
        </p>
      ) : (
        <div className="mb-3 space-y-2">
          <p className="text-[12.5px] text-ink-dim">
            {formatNumber(orders.orders.length)}{" "}
            {orders.orders.length === 1 ? "ordem sugerida" : "ordens sugeridas"} · {pending.length}{" "}
            {pending.length === 1 ? "transferência pendente" : "transferências pendentes"}
          </p>
          {orders.orders.length > 0 && (
            <Table columns={PLAN_COLUMNS} rows={orders.orders} rowKey={(order) => order.id} />
          )}
        </div>
      )}

      {stale && (
        <p className="mb-2 text-[12.5px] text-attention">
          O capital ou os alvos mudaram depois do último cálculo. O plano guardado é um retrato —
          recalcule para atualizá-lo.
        </p>
      )}
      {refresh.error && <p className="mb-2 text-[12.5px] text-tax">{refresh.error.message}</p>}

      <Button
        variant="primary"
        data-testid="plan-refresh"
        onClick={() => refresh.mutate(undefined, { onSuccess: onRefreshed })}
        disabled={refresh.isPending}
      >
        {refresh.isPending ? "Recalculando…" : "Recalcular plano"}
      </Button>
    </Panel>
  );
}

const PLAN_COLUMNS: Column<SuggestedOrder>[] = [
  {
    key: "ticker",
    header: "Ordem",
    render: (order) => <span className="font-mono">{order.ticker}</span>,
  },
  { key: "side", header: "Lado", render: (order) => orderSideLabel(order.side) },
  {
    key: "quantity",
    header: "Qtd",
    align: "right",
    render: (order) => formatNumber(order.quantity),
  },
  {
    key: "price",
    header: "Preço",
    align: "right",
    render: (order) =>
      order.suggested_price === undefined ? "—" : formatCurrency(order.suggested_price),
  },
  {
    key: "amount",
    header: "Valor",
    align: "right",
    render: (order) =>
      order.suggested_price === undefined
        ? "—"
        : formatCurrency(order.quantity * order.suggested_price),
  },
  { key: "status", header: "Estado", render: (order) => orderStatusLabel(order.status) },
];

// Steps 4–6 keep their rail row and their meta; the bodies land with the slices that own them.
function PendingStep({ step }: { step: PlanStep }) {
  return (
    <Panel title={step.label}>
      <p className="text-[13px] text-ink-dim">
        Esta etapa é construída na fatia que a detalha. O rail já acompanha o estado do mês.
      </p>
    </Panel>
  );
}

// Step 2 — Cadeia de alvos: the fixed class and RF sub-class rungs plus the strategy and ticker rows,
// all replaced through PUT /month/current/targets. Submitting replaces the whole chain.
function TargetsStep({ targets, onSaved }: { targets: Targets; onSaved: () => void }) {
  const [form, setForm] = useState<TargetsFormState>(() => targetsFormFrom(targets));
  const replace = useReplaceTargets();
  const strategies = useStrategies();
  // The bucket carries no Target row, so its share is derived from the classes that have one.
  const manualClasses = new Set(
    (strategies.data ?? [])
      .filter((strategy) => strategy.kind === "MANUAL")
      .map((strategy) => strategy.asset_class),
  );
  const remainders = remainingLegacyShares(form, manualClasses);
  const overplanned = overplannedStrategyClasses(form);

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    replace.mutate(toTargets(form), { onSuccess: onSaved });
  }

  return (
    <Panel title="Cadeia de alvos">
      <form onSubmit={handleSubmit} className="space-y-6">
        <Rung title="Por classe">
          {form.classes.map((row, index) => (
            <NumberField
              key={row.assetClass}
              label={assetClassLabel(row.assetClass)}
              value={row.percentage}
              onChange={(value) =>
                setForm((current) => ({
                  ...current,
                  classes: patchRow(current.classes, index, { percentage: value }),
                }))
              }
            />
          ))}
        </Rung>

        <Rung title="Sub-classes de renda fixa">
          {form.subClasses.map((row, index) => (
            <NumberField
              key={row.subClass}
              label={subClassLabel(row.subClass)}
              value={row.percentage}
              onChange={(value) =>
                setForm((current) => ({
                  ...current,
                  subClasses: patchRow(current.subClasses, index, { percentage: value }),
                }))
              }
            />
          ))}
        </Rung>

        <Rung title="Estratégias">
          {form.strategies.map((row, index) => (
            <div key={row.id} className="flex flex-wrap items-end gap-2">
              <label className="flex flex-col gap-1 text-[12.5px] text-ink-dim">
                Classe
                <select
                  value={row.assetClass}
                  onChange={(event) =>
                    setForm((current) => ({
                      ...current,
                      strategies: patchRow(current.strategies, index, {
                        assetClass: event.target.value as AssetClass,
                      }),
                    }))
                  }
                  className={FIELD}
                >
                  {ASSET_CLASSES.filter((assetClass) => assetClass !== "RENDA_FIXA").map(
                    (assetClass) => (
                      <option key={assetClass} value={assetClass}>
                        {assetClassLabel(assetClass)}
                      </option>
                    ),
                  )}
                </select>
              </label>
              <label className="flex flex-col gap-1 text-[12.5px] text-ink-dim">
                Estratégia
                <input
                  type="text"
                  placeholder="Top Dividendos"
                  value={row.strategy}
                  onChange={(event) =>
                    setForm((current) => ({
                      ...current,
                      strategies: patchRow(current.strategies, index, {
                        strategy: event.target.value,
                      }),
                    }))
                  }
                  className={FIELD}
                />
              </label>
              <NumberField
                label="Alvo (%)"
                value={row.percentage}
                onChange={(value) =>
                  setForm((current) => ({
                    ...current,
                    strategies: patchRow(current.strategies, index, { percentage: value }),
                  }))
                }
              />
              <Button
                variant="ghost"
                onClick={() => setForm((current) => removeStrategyRow(current, index))}
              >
                Remover
              </Button>
            </div>
          ))}
          <Button variant="default" onClick={() => setForm((current) => addStrategyRow(current))}>
            Adicionar estratégia
          </Button>
          {remainders.map(({ assetClass, percentage }) => (
            <p key={assetClass} className="w-full text-[12.5px] text-ink-dim">
              {assetClassLabel(assetClass)} · restante: {formatNumber(percentage)}% — posições
              legadas, sem alvo
            </p>
          ))}
          {overplanned.map((assetClass) => (
            <p key={assetClass} className="w-full text-[12.5px] text-tax">
              A soma dos alvos das estratégias de {assetClassLabel(assetClass)} passa de 100%.
            </p>
          ))}
        </Rung>

        <Rung title="Tickers por estratégia">
          {form.tickers.map((row, index) => (
            <div key={row.id} className="flex flex-wrap items-end gap-2">
              <label className="flex flex-col gap-1 text-[12.5px] text-ink-dim">
                Estratégia
                <input
                  type="text"
                  placeholder="Top Dividendos"
                  value={row.strategy}
                  onChange={(event) =>
                    setForm((current) => ({
                      ...current,
                      tickers: patchRow(current.tickers, index, { strategy: event.target.value }),
                    }))
                  }
                  className={FIELD}
                />
              </label>
              <label className="flex flex-col gap-1 text-[12.5px] text-ink-dim">
                Ticker
                <input
                  type="text"
                  placeholder="VALE3"
                  value={row.ticker}
                  onChange={(event) =>
                    setForm((current) => ({
                      ...current,
                      tickers: patchRow(current.tickers, index, { ticker: event.target.value }),
                    }))
                  }
                  className={FIELD}
                />
              </label>
              <NumberField
                label="Peso (%)"
                value={row.percentage}
                onChange={(value) =>
                  setForm((current) => ({
                    ...current,
                    tickers: patchRow(current.tickers, index, { percentage: value }),
                  }))
                }
              />
              <Button
                variant="ghost"
                onClick={() => setForm((current) => removeTickerRow(current, index))}
              >
                Remover
              </Button>
            </div>
          ))}
          <Button
            variant="default"
            onClick={() =>
              setForm((current) => addTickerRow(current, current.strategies[0]?.strategy ?? ""))
            }
          >
            Adicionar ticker
          </Button>
        </Rung>

        {replace.error && <p className="text-[12.5px] text-tax">{replace.error.message}</p>}
        {replace.isSuccess && <p className="text-[12.5px] text-yield">Alvos salvos.</p>}

        <div className="flex flex-wrap items-center gap-3">
          <Button
            type="submit"
            variant="primary"
            disabled={replace.isPending || overplanned.length > 0}
          >
            {replace.isPending ? "Salvando…" : "Salvar alvos"}
          </Button>
          <span className="text-[11.5px] text-ink-faint">
            Salvar alvos deixa o plano calculado desatualizado até um recálculo.
          </span>
        </div>
      </form>
    </Panel>
  );
}

// Opening a month closes any month still open (the backend owns that invariant), so the copy warns
// before the button. The period selects are bounded by the server's window, never a ±2-year guess.
function MonthOpener({
  range,
  onOpened,
  onCancel,
}: {
  range: MonthPeriodRange;
  onOpened: () => void;
  onCancel?: () => void;
}) {
  const [form, setForm] = useState<MonthFormState>(() => emptyMonthForm(range.default));
  const open = useOpenMonth();
  const creation = toMonthCreation(form);

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!creation) return;
    open.mutate(creation, { onSuccess: onOpened });
  }

  return (
    <Panel title="Abrir mês">
      <form onSubmit={handleSubmit} data-testid="month-opener" className="space-y-4">
        <div className="flex flex-wrap gap-3">
          {/* Two selects rather than a native month input: Firefox/Safari have no month picker. */}
          <div className="flex flex-col gap-1 text-[12.5px] text-ink-dim">
            <span>Período</span>
            <PeriodSelect
              range={range}
              period={form.period}
              onChange={(period) => setForm((current) => ({ ...current, period }))}
            />
            <span className="text-[11.5px] text-ink-faint">
              Histórico desde {monthPeriodLabel(range.min)}
            </span>
          </div>
          <NumberField
            label="Capital (R$)"
            value={form.capital}
            onChange={(value) => setForm((current) => ({ ...current, capital: value }))}
          />
          <NumberField
            label="Reserva (piso, R$)"
            value={form.floor}
            onChange={(value) => setForm((current) => ({ ...current, floor: value }))}
          />
        </div>

        {open.error && <p className="text-[12.5px] text-tax">{open.error.message}</p>}

        <div className="flex flex-wrap gap-2">
          <Button type="submit" variant="primary" disabled={!creation || open.isPending}>
            {open.isPending ? "Abrindo…" : "Abrir mês"}
          </Button>
          {onCancel && (
            <Button type="button" variant="ghost" onClick={onCancel}>
              Cancelar
            </Button>
          )}
        </div>
        <p className="text-[11.5px] text-ink-faint">Abrir um mês fecha o mês aberto atual.</p>
      </form>
    </Panel>
  );
}

// A labelled read in a step's mini summary.
function MiniRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center gap-2 py-1.5 text-[12.5px]">
      <span className="text-ink-dim">{label}</span>
      <span className="flex-1" />
      <span className="font-mono text-[12px] text-ink">{value}</span>
    </div>
  );
}

// A titled group of entry fields in the chain form.
function Rung({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="space-y-2">
      <h3 className="font-cond text-[12.5px] font-bold tracking-[.04em] text-ink-dim uppercase">
        {title}
      </h3>
      <div className="flex flex-wrap gap-3">{children}</div>
    </div>
  );
}

function NumberField({
  label,
  value,
  onChange,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
}) {
  return (
    <label className="flex flex-col gap-1 text-[12.5px] text-ink-dim">
      {label}
      <input
        type="number"
        step="0.01"
        placeholder="0,00"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className={FIELD}
      />
    </label>
  );
}

function patchRow<T>(rows: T[], index: number, patch: Partial<T>): T[] {
  return rows.map((row, position) => (position === index ? { ...row, ...patch } : row));
}
