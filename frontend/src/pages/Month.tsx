import { type FormEvent, type ReactNode, useState } from "react";
import {
  useCurrentMonth,
  useMonthPeriodRange,
  useOpenMonth,
  useReplaceTargets,
  useStrategies,
  useUpdateCurrentMonth,
} from "../api/queries.ts";
import type {
  AssetClass,
  CurrentMonth,
  Month as MonthModel,
  MonthPeriodRange,
  Targets,
} from "../api/types.ts";
import { Button } from "../components/Button.tsx";
import { Chip } from "../components/Chip.tsx";
import { Panel } from "../components/Panel.tsx";
import { PeriodSelect } from "../components/PeriodSelect.tsx";
import { monthKey, todayIso } from "../lib/dates.ts";
import { subClassLabel } from "../lib/fixedIncome.ts";
import { formatCurrency, formatNumber } from "../lib/format.ts";
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

const FIELD = "rounded-sm border border-line bg-surface-2 px-2 py-1.5 text-[13px] text-ink";

// The month-scoped kernel screen: without an open month it offers the opener; with one it shows the
// Capital/Floor editor and the target chain entry form. The month-scoped screens added later
// (Alocação, Ordens) hang off the same open month.
export function Month() {
  const current = useCurrentMonth();
  const range = useMonthPeriodRange();
  const [switching, setSwitching] = useState(false);
  const currentPeriod = monthKey(todayIso());

  return (
    <section>
      <h1 className="mb-4 font-cond text-[24px] font-bold tracking-[.005em]">Mês</h1>

      {current.isLoading || (switching && range.isLoading) ? (
        <p className="text-[13.5px] text-ink-dim">Carregando…</p>
      ) : current.isError ? (
        <p className="text-[13.5px] text-tax">{current.error.message}</p>
      ) : current.data && !switching ? (
        <MonthDetails
          key={current.data.month.id}
          current={current.data}
          currentPeriod={currentPeriod}
          onSwitch={() => setSwitching(true)}
        />
      ) : range.isError ? (
        <p className="text-[13.5px] text-tax">{range.error.message}</p>
      ) : range.data ? (
        <MonthOpener
          range={range.data}
          onOpened={() => setSwitching(false)}
          onCancel={current.data ? () => setSwitching(false) : undefined}
        />
      ) : null}
    </section>
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

function MonthDetails({
  current,
  currentPeriod,
  onSwitch,
}: {
  current: CurrentMonth;
  currentPeriod: string;
  onSwitch: () => void;
}) {
  const backdated = isBackdatedPeriod(current.month.period, currentPeriod);

  return (
    <div data-testid="month-details">
      <div className="mb-4 flex flex-wrap items-center gap-3">
        <Chip tone={current.month.status === "OPEN" ? "green" : "neutral"}>
          {monthStatusLabel(current.month.status)}
        </Chip>
        {backdated && <Chip tone="amber">histórico — sem fechamento</Chip>}
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

      <div className="mb-4">
        <CapitalForm month={current.month} />
      </div>

      <TargetsForm targets={current.targets} />
    </div>
  );
}

function CapitalForm({ month }: { month: MonthModel }) {
  const [form, setForm] = useState<MonthFormState>(() => monthFormFrom(month));
  const update = useUpdateCurrentMonth();
  const payload = toMonthUpdate(form);

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!payload) return;
    update.mutate(payload);
  }

  return (
    <Panel title="Capital do mês">
      <form onSubmit={handleSubmit} className="space-y-4">
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
  );
}

// The target chain entry form: the fixed class and RF sub-class rungs are always shown; strategies
// and their tickers are rows the user adds. Submitting replaces the whole chain.
function TargetsForm({ targets }: { targets: Targets }) {
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
    replace.mutate(toTargets(form));
  }

  return (
    <Panel title="Alvos">
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

        <Button
          type="submit"
          variant="primary"
          disabled={replace.isPending || overplanned.length > 0}
        >
          {replace.isPending ? "Salvando…" : "Salvar alvos"}
        </Button>
      </form>
    </Panel>
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
