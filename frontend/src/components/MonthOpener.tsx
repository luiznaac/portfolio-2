import { type FormEvent, useState } from "react";
import { useOpenMonth } from "../api/queries.ts";
import type { MonthPeriodRange } from "../api/types.ts";
import {
  type MonthFormState,
  emptyMonthForm,
  monthPeriodLabel,
  toMonthCreation,
} from "../lib/month.ts";
import { Button } from "./Button.tsx";
import { NumberField } from "./NumberField.tsx";
import { Panel } from "./Panel.tsx";
import { PeriodSelect } from "./PeriodSelect.tsx";

// Opening a month closes any month still open (the backend owns that invariant), so the copy warns
// before the button. The period selects are bounded by the server's window, never a ±2-year guess.
// Mês atual and Fechamentos both host this opener when no month is open.
export function MonthOpener({
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
