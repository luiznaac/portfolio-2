import { useState, type FormEvent } from "react";
import {
  useAllocationPlan,
  useCapitalSnapshots,
  useClassTargets,
  useCreateStrategy,
  useFixedIncomeSubClassTargets,
  useRecordCapitalSnapshot,
  useSetClassTarget,
  useSetFixedIncomeSubClassTarget,
  useStrategies,
} from "../api/queries.ts";
import type { AssetClass, FixedIncomeSubClass } from "../api/types.ts";
import {
  ASSET_CLASSES,
  FIXED_INCOME_SUBCLASSES,
  assetClassLabel,
  fixedIncomeSubClassLabel,
} from "../i18n/assetClass.ts";
import { DivergentBar } from "../components/DivergentBar.tsx";
import { Panel } from "../components/Panel.tsx";
import { formatBRL, formatRatio } from "../lib/money.ts";
import { formatDate } from "../lib/format.ts";

export function Carteira() {
  const plan = useAllocationPlan();

  return (
    <div className="space-y-6">
      <h1 className="text-xl font-semibold text-slate-100">Carteira</h1>

      <CapitalPanel />

      <Panel title="Drift por classe">
        {plan.isLoading && <p className="text-sm text-slate-500">Carregando…</p>}
        {plan.data && <AllocationTree classes={plan.data.classes} />}
      </Panel>

      <div className="grid gap-6 lg:grid-cols-2">
        <Panel title="Alvos por classe">
          <ClassTargetsPanel />
        </Panel>
        <Panel title="Sub-alvos de renda fixa">
          <SubClassTargetsPanel />
        </Panel>
      </div>

      <Panel title="Estratégias (carteiras XP)">
        <StrategiesPanel />
      </Panel>
    </div>
  );
}

function CapitalPanel() {
  const snapshots = useCapitalSnapshots();
  const mutation = useRecordCapitalSnapshot();
  const [date, setDate] = useState("");
  const [externalBalance, setExternalBalance] = useState("");
  const [plannedContribution, setPlannedContribution] = useState("");

  const last = snapshots.data?.[snapshots.data.length - 1];

  const submit = (e: FormEvent) => {
    e.preventDefault();
    mutation.mutate({
      date,
      external_balance: Number(externalBalance),
      planned_contribution: Number(plannedContribution),
    });
  };

  return (
    <Panel title="Capital do mês">
      <div className="space-y-4">
        {last && (
          <p className="text-sm text-slate-300">
            Capital em {formatDate(last.date)}:{" "}
            <span className="tabular-nums text-slate-100">
              {formatBRL(last.external_balance + last.planned_contribution)}
            </span>{" "}
            <span className="text-slate-500">
              (saldo externo {formatBRL(last.external_balance)} + aporte{" "}
              {formatBRL(last.planned_contribution)})
            </span>
          </p>
        )}
        <form onSubmit={submit} className="flex flex-wrap items-end gap-3">
          <label className="text-sm">
            <span className="mb-1 block text-xs text-slate-500">Data</span>
            <input
              type="date"
              required
              value={date}
              onChange={(e) => setDate(e.target.value)}
              className="rounded-md border border-white/10 bg-slate-900 px-3 py-2 text-slate-100 outline-none focus:border-accent-500"
            />
          </label>
          <label className="text-sm">
            <span className="mb-1 block text-xs text-slate-500">Saldo externo (R$)</span>
            <input
              type="number"
              step="0.01"
              required
              value={externalBalance}
              onChange={(e) => setExternalBalance(e.target.value)}
              className="w-36 rounded-md border border-white/10 bg-slate-900 px-3 py-2 text-slate-100 outline-none focus:border-accent-500"
            />
          </label>
          <label className="text-sm">
            <span className="mb-1 block text-xs text-slate-500">Aporte planejado (R$)</span>
            <input
              type="number"
              step="0.01"
              required
              value={plannedContribution}
              onChange={(e) => setPlannedContribution(e.target.value)}
              className="w-36 rounded-md border border-white/10 bg-slate-900 px-3 py-2 text-slate-100 outline-none focus:border-accent-500"
            />
          </label>
          <button
            type="submit"
            disabled={mutation.isPending}
            className="rounded-md bg-accent-500 px-3 py-2 text-sm font-medium text-slate-950 transition-colors hover:bg-accent-600 disabled:opacity-50"
          >
            {mutation.isPending ? "Salvando…" : "Registrar"}
          </button>
        </form>
      </div>
    </Panel>
  );
}

function AllocationTree({
  classes,
}: {
  classes: {
    asset_class: AssetClass;
    ideal: number;
    current: number;
    delta: number;
    sub_classes: { sub_class: FixedIncomeSubClass; ideal: number; current: number; delta: number }[];
  }[];
}) {
  const maxAbs = Math.max(1, ...classes.map((c) => Math.abs(c.delta)));
  const subMaxAbs = Math.max(
    1,
    ...classes.flatMap((c) => c.sub_classes.map((s) => Math.abs(s.delta))),
  );

  return (
    <div className="space-y-1">
      {classes.map((c) => (
        <div key={c.asset_class}>
          <DivergentBar label={assetClassLabel(c.asset_class)} delta={c.delta} maxAbs={maxAbs} />
          {c.sub_classes.length > 0 && (
            <div className="ml-4 border-l border-white/10 pl-3">
              {c.sub_classes.map((s) => (
                <DivergentBar
                  key={s.sub_class}
                  label={fixedIncomeSubClassLabel(s.sub_class)}
                  delta={s.delta}
                  maxAbs={subMaxAbs}
                />
              ))}
            </div>
          )}
        </div>
      ))}
    </div>
  );
}

function ClassTargetsPanel() {
  const targets = useClassTargets();
  const mutation = useSetClassTarget();
  const [assetClass, setAssetClass] = useState<AssetClass>("ACOES");
  const [weightPct, setWeightPct] = useState("");

  const current = new Map<AssetClass, number>();
  for (const t of targets.data ?? []) current.set(t.asset_class, t.weight);

  const submit = (e: FormEvent) => {
    e.preventDefault();
    mutation.mutate({
      asset_class: assetClass,
      weight: Number(weightPct) / 100,
      effective_from: new Date().toISOString().slice(0, 10),
    });
  };

  return (
    <div className="space-y-4">
      <ul className="divide-y divide-white/5 text-sm">
        {ASSET_CLASSES.map((c) => (
          <li key={c} className="flex items-center justify-between py-1.5">
            <span className="text-slate-300">{assetClassLabel(c)}</span>
            <span className="tabular-nums text-slate-200">
              {current.has(c) ? formatRatio(current.get(c)!) : "—"}
            </span>
          </li>
        ))}
      </ul>
      <form onSubmit={submit} className="flex flex-wrap items-end gap-3">
        <label className="text-sm">
          <span className="mb-1 block text-xs text-slate-500">Classe</span>
          <select
            value={assetClass}
            onChange={(e) => setAssetClass(e.target.value as AssetClass)}
            className="rounded-md border border-white/10 bg-slate-900 px-3 py-2 text-slate-100 outline-none focus:border-accent-500"
          >
            {ASSET_CLASSES.map((c) => (
              <option key={c} value={c}>
                {assetClassLabel(c)}
              </option>
            ))}
          </select>
        </label>
        <label className="text-sm">
          <span className="mb-1 block text-xs text-slate-500">Peso (%)</span>
          <input
            type="number"
            step="0.01"
            min="0"
            max="100"
            required
            value={weightPct}
            onChange={(e) => setWeightPct(e.target.value)}
            className="w-24 rounded-md border border-white/10 bg-slate-900 px-3 py-2 text-slate-100 outline-none focus:border-accent-500"
          />
        </label>
        <button
          type="submit"
          disabled={mutation.isPending}
          className="rounded-md bg-accent-500 px-3 py-2 text-sm font-medium text-slate-950 transition-colors hover:bg-accent-600 disabled:opacity-50"
        >
          {mutation.isPending ? "Salvando…" : "Definir"}
        </button>
      </form>
    </div>
  );
}

function SubClassTargetsPanel() {
  const targets = useFixedIncomeSubClassTargets();
  const mutation = useSetFixedIncomeSubClassTarget();
  const [subClass, setSubClass] = useState<FixedIncomeSubClass>("POS_FIXADO");
  const [weightPct, setWeightPct] = useState("");

  const current = new Map<FixedIncomeSubClass, number>();
  for (const t of targets.data ?? []) current.set(t.sub_class, t.weight);

  const submit = (e: FormEvent) => {
    e.preventDefault();
    mutation.mutate({
      sub_class: subClass,
      weight: Number(weightPct) / 100,
      effective_from: new Date().toISOString().slice(0, 10),
    });
  };

  return (
    <div className="space-y-4">
      <ul className="divide-y divide-white/5 text-sm">
        {FIXED_INCOME_SUBCLASSES.map((s) => (
          <li key={s} className="flex items-center justify-between py-1.5">
            <span className="text-slate-300">{fixedIncomeSubClassLabel(s)}</span>
            <span className="tabular-nums text-slate-200">
              {current.has(s) ? formatRatio(current.get(s)!) : "—"}
            </span>
          </li>
        ))}
      </ul>
      <form onSubmit={submit} className="flex flex-wrap items-end gap-3">
        <label className="text-sm">
          <span className="mb-1 block text-xs text-slate-500">Sub-classe</span>
          <select
            value={subClass}
            onChange={(e) => setSubClass(e.target.value as FixedIncomeSubClass)}
            className="rounded-md border border-white/10 bg-slate-900 px-3 py-2 text-slate-100 outline-none focus:border-accent-500"
          >
            {FIXED_INCOME_SUBCLASSES.map((s) => (
              <option key={s} value={s}>
                {fixedIncomeSubClassLabel(s)}
              </option>
            ))}
          </select>
        </label>
        <label className="text-sm">
          <span className="mb-1 block text-xs text-slate-500">Peso (%)</span>
          <input
            type="number"
            step="0.01"
            min="0"
            max="100"
            required
            value={weightPct}
            onChange={(e) => setWeightPct(e.target.value)}
            className="w-24 rounded-md border border-white/10 bg-slate-900 px-3 py-2 text-slate-100 outline-none focus:border-accent-500"
          />
        </label>
        <button
          type="submit"
          disabled={mutation.isPending}
          className="rounded-md bg-accent-500 px-3 py-2 text-sm font-medium text-slate-950 transition-colors hover:bg-accent-600 disabled:opacity-50"
        >
          {mutation.isPending ? "Salvando…" : "Definir"}
        </button>
      </form>
    </div>
  );
}

function StrategiesPanel() {
  const strategies = useStrategies();
  const mutation = useCreateStrategy();
  const [name, setName] = useState("");

  const submit = (e: FormEvent) => {
    e.preventDefault();
    mutation.mutate({ name }, { onSuccess: () => setName("") });
  };

  return (
    <div className="space-y-4">
      {strategies.data && strategies.data.length > 0 ? (
        <ul className="flex flex-wrap gap-2">
          {strategies.data.map((s) => (
            <li
              key={s.id}
              className="rounded bg-slate-800 px-2.5 py-1 text-sm text-slate-300"
            >
              {s.name}
            </li>
          ))}
        </ul>
      ) : (
        <p className="text-sm text-slate-500">Nenhuma estratégia cadastrada.</p>
      )}
      <form onSubmit={submit} className="flex items-end gap-3">
        <label className="text-sm">
          <span className="mb-1 block text-xs text-slate-500">Nome</span>
          <input
            required
            placeholder="ex.: Top, Dividendos…"
            value={name}
            onChange={(e) => setName(e.target.value)}
            className="w-56 rounded-md border border-white/10 bg-slate-900 px-3 py-2 text-slate-100 outline-none focus:border-accent-500"
          />
        </label>
        <button
          type="submit"
          disabled={mutation.isPending}
          className="rounded-md bg-accent-500 px-3 py-2 text-sm font-medium text-slate-950 transition-colors hover:bg-accent-600 disabled:opacity-50"
        >
          {mutation.isPending ? "Salvando…" : "Adicionar"}
        </button>
      </form>
    </div>
  );
}
