import { useState } from "react";
import { Link } from "react-router-dom";
import { useCreateStrategy, useCurrentAllocation, useStrategies } from "../api/queries.ts";
import type { AssetClass, EditionSummary, Strategy, StrategyKind } from "../api/types.ts";
import { Button } from "../components/Button.tsx";
import { Chip } from "../components/Chip.tsx";
import { Field, Input } from "../components/Field.tsx";
import { Panel } from "../components/Panel.tsx";
import { type Column, Table } from "../components/Table.tsx";
import { formatPoints } from "../lib/allocation.ts";
import { cn } from "../lib/cn.ts";
import { formatCurrency } from "../lib/format.ts";
import { ASSET_CLASSES, assetClassLabel } from "../lib/month.ts";
import { strategyDetail } from "../lib/routes.ts";
import {
  type StrategyListRow,
  editionSummaryChip,
  isReportDriven,
  strategyKindLabel,
  strategyRows,
} from "../lib/strategy.ts";

const FIELD = "rounded-sm border border-line bg-surface-2 px-2 py-1.5 text-[13px] text-ink";

// A Strategy is created in one of the two listed-asset classes; Renda Fixa is the consolidation
// engine's domain and never holds a Strategy.
const STRATEGY_CLASSES: AssetClass[] = ASSET_CLASSES.filter(
  (assetClass) => assetClass !== "RENDA_FIXA",
);

// The Estratégias screen: the comparison list (issue #373). One bounded table over the whole catalog
// — no filter bar — reading GET /strategies (which carries `latest_edition`) and the allocation tree
// for value/drift; a row opens the strategy detail (#374). A report is uploaded from the detail's
// Edições tab through the one import assistant, so there is no batch import surface here.
export function Strategies() {
  const strategies = useStrategies();
  const allocation = useCurrentAllocation();
  const [creating, setCreating] = useState(false);

  const list = strategies.data ?? [];
  const rows = strategyRows(list, allocation.data);

  const columns: Column<StrategyListRow>[] = [
    {
      key: "strategy",
      header: "Estratégia",
      render: (row) => (
        <Link to={strategyDetail(row.strategy.id)} className="text-ink hover:text-accent">
          {row.strategy.name}
        </Link>
      ),
    },
    {
      key: "class",
      header: "Classe",
      render: (row) => assetClassLabel(row.strategy.asset_class),
    },
    {
      key: "kind",
      header: "Tipo",
      render: (row) => strategyKindLabel(row.strategy.kind),
    },
    {
      key: "edition",
      header: "Edição",
      render: (row) => <EditionCell latest={row.strategy.latest_edition} />,
    },
    {
      key: "value",
      header: "Valor",
      align: "right",
      render: (row) =>
        row.value === undefined ? (
          <Unknown />
        ) : (
          <span className="font-mono">{formatCurrency(row.value)}</span>
        ),
    },
    {
      key: "drift",
      header: "Drift",
      align: "right",
      render: (row) =>
        row.drift === undefined ? (
          <Unknown />
        ) : (
          <span className="font-mono">{formatPoints(row.drift)}</span>
        ),
    },
  ];

  return (
    <section>
      <div className="mb-4 flex items-center justify-end gap-2">
        <Button variant="default" onClick={() => setCreating(true)}>
          Nova estratégia
        </Button>
      </div>

      {strategies.isLoading ? (
        <p className="text-[13.5px] text-ink-dim">Carregando…</p>
      ) : strategies.isError ? (
        <p className="text-[13.5px] text-tax">{strategies.error.message}</p>
      ) : rows.length === 0 ? (
        <Panel>
          <p className="text-[13px] text-ink-dim">Nenhuma estratégia cadastrada.</p>
        </Panel>
      ) : (
        <Table columns={columns} rows={rows} rowKey={(row) => row.strategy.id} />
      )}

      {creating && (
        <CreateStrategyModal
          strategies={list}
          onClose={() => setCreating(false)}
          onCreated={() => setCreating(false)}
        />
      )}
    </section>
  );
}

// The latest edition cell: the month with a ✓ when active, a "revisão" badge while staged, and an
// em dash when the strategy has never received a report.
function EditionCell({ latest }: { latest?: EditionSummary }) {
  if (!latest) return <Unknown />;
  const chip = editionSummaryChip(latest);
  return <Chip tone={chip.tone}>{chip.label}</Chip>;
}

// An unknown number — a strategy the month chain does not carry, or a bucket with no target. It is
// always an em dash, never R$ 0,00.
function Unknown() {
  return <span className="text-ink-dim">—</span>;
}

function CreateStrategyModal({
  strategies,
  onClose,
  onCreated,
}: {
  strategies: Strategy[];
  onClose: () => void;
  onCreated: (strategy: Strategy) => void;
}) {
  const createStrategy = useCreateStrategy();
  const [name, setName] = useState("");
  const [assetClass, setAssetClass] = useState<AssetClass>("ACOES");
  const [kind, setKind] = useState<StrategyKind>("REPORT_DRIVEN");

  const bucket = strategies.find(
    (strategy) => !isReportDriven(strategy) && strategy.asset_class === assetClass,
  );
  // A class holds at most one bucket, so a class that already has one falls back to "Com relatório"
  // during render instead of leaving a contradictory mode in state.
  const effectiveKind = bucket !== undefined && kind === "MANUAL" ? "REPORT_DRIVEN" : kind;

  async function submit() {
    try {
      const created = await createStrategy.mutateAsync({
        name: name.trim(),
        asset_class: assetClass,
        kind: effectiveKind,
      });
      onCreated(created);
    } catch {
      // The mutation error is rendered inline; the modal stays open for a retry.
    }
  }

  return (
    <dialog
      open
      aria-modal="true"
      aria-label="Nova estratégia"
      className="fixed inset-0 z-50 m-0 flex h-full w-full max-w-none items-center justify-center border-0 bg-transparent p-0"
    >
      <button
        type="button"
        aria-label="Fechar"
        onClick={onClose}
        className="absolute inset-0 bg-ground/70"
      />
      <div className="relative w-[420px] max-w-full space-y-4 rounded-md border border-line bg-surface p-4">
        <h3 className="font-cond text-[16px] font-bold">Nova estratégia</h3>

        <Field label="Nome">
          <Input value={name} onChange={(event) => setName(event.target.value)} />
        </Field>

        <Field label="Classe">
          <select
            value={assetClass}
            onChange={(event) => setAssetClass(event.target.value as AssetClass)}
            className={FIELD}
          >
            {STRATEGY_CLASSES.map((option) => (
              <option key={option} value={option}>
                {assetClassLabel(option)}
              </option>
            ))}
          </select>
        </Field>

        <div className="space-y-1">
          <span className="text-[11.5px] text-ink-dim">Modo</span>
          <fieldset className="grid grid-cols-2 gap-1 rounded-md border border-line bg-surface-2 p-1">
            <legend className="sr-only">Modo</legend>
            <label
              className={cn(
                "flex cursor-pointer items-center justify-center rounded-sm px-2 py-1.5 text-[12.5px]",
                effectiveKind === "REPORT_DRIVEN"
                  ? "bg-surface text-ink"
                  : "text-ink-dim hover:text-ink",
              )}
            >
              <input
                type="radio"
                name="strategy-kind"
                value="REPORT_DRIVEN"
                checked={effectiveKind === "REPORT_DRIVEN"}
                onChange={() => setKind("REPORT_DRIVEN")}
                className="sr-only"
              />
              Com relatório
            </label>
            <label
              className={cn(
                "flex items-center justify-center rounded-sm px-2 py-1.5 text-[12.5px]",
                bucket === undefined ? "cursor-pointer" : "cursor-not-allowed opacity-45",
                effectiveKind === "MANUAL" ? "bg-surface text-ink" : "text-ink-dim hover:text-ink",
              )}
            >
              <input
                type="radio"
                name="strategy-kind"
                value="MANUAL"
                checked={effectiveKind === "MANUAL"}
                disabled={bucket !== undefined}
                onChange={() => setKind("MANUAL")}
                className="sr-only"
              />
              Manual (legado)
            </label>
          </fieldset>
          {bucket && (
            <p className="text-[11.5px] text-ink-faint">
              {assetClassLabel(assetClass)} já tem uma estratégia manual.
            </p>
          )}
        </div>

        {createStrategy.error && (
          <p className="text-[12.5px] text-tax">{createStrategy.error.message}</p>
        )}

        <div className="flex justify-end gap-2">
          <Button variant="ghost" onClick={onClose}>
            Cancelar
          </Button>
          <Button
            variant="primary"
            onClick={submit}
            disabled={createStrategy.isPending || name.trim() === ""}
          >
            {createStrategy.isPending ? "Criando…" : "Criar estratégia"}
          </Button>
        </div>
      </div>
    </dialog>
  );
}
