import { type ChangeEvent, useState } from "react";
import { Link, useLocation, useSearchParams } from "react-router-dom";
import {
  useActivateEdition,
  useCreateStrategy,
  useCurrentMonth,
  useEditions,
  useImportEdition,
  useStrategies,
  useStrategyEditions,
} from "../api/queries.ts";
import type {
  AssetClass,
  Edition,
  EditionDiff,
  EditionEntry,
  Rating,
  Strategy,
  StrategyKind,
} from "../api/types.ts";
import { Button } from "../components/Button.tsx";
import { Chip } from "../components/Chip.tsx";
import { Field, Input } from "../components/Field.tsx";
import { Panel } from "../components/Panel.tsx";
import { type Column, Table } from "../components/Table.tsx";
import { cn } from "../lib/cn.ts";
import { formatCurrency } from "../lib/format.ts";
import { ASSET_CLASSES, assetClassLabel } from "../lib/month.ts";
import {
  editionDiffSummary,
  editionStatusChip,
  editionWeightSum,
  groupStrategiesByClass,
  isEditionForMonth,
  isReportDriven,
  isWeightSumValid,
  overrideEntry,
  ratingLabel,
  referenceMonthLabel,
  reportDrivenStrategies,
  strategyCoverage,
  strategyCoverageLabel,
  suggestStrategy,
} from "../lib/strategy.ts";

const FIELD = "rounded-sm border border-line bg-surface-2 px-2 py-1.5 text-[13px] text-ink";

const RATINGS: Rating[] = ["BUY", "NEUTRAL", "SELL"];

// A Strategy is created in one of the two listed-asset classes; Renda Fixa is the consolidation
// engine's domain and never holds a Strategy.
const STRATEGY_CLASSES: AssetClass[] = ASSET_CLASSES.filter(
  (assetClass) => assetClass !== "RENDA_FIXA",
);

// The Estratégias screen (issue #162): the XP strategy catalog, each strategy's editions with the
// month-to-month diff, and the batch report import with a per-edition preview drawer. The catalog
// view is a read of GET /strategies + GET /strategies/{id}/editions; the import view stages each PDF
// through POST .../editions/import and confirms through POST .../editions.
export function Strategies() {
  const { pathname } = useLocation();
  return pathname.endsWith("/import") ? <StrategyImportView /> : <StrategyCatalogView />;
}

function StrategyCatalogView() {
  const strategies = useStrategies();
  const currentMonth = useCurrentMonth().data?.month.period;
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [creating, setCreating] = useState(false);

  const list = strategies.data ?? [];
  const reportStrategies = reportDrivenStrategies(list);
  const groups = groupStrategiesByClass(list);
  // The default selection is the first row the nav renders, so the bucket never opens the detail
  // pane just because its name sorts first in the server's list.
  const selected =
    list.find((strategy) => strategy.id === selectedId) ?? groups[0]?.strategies[0] ?? null;
  const editions = useEditions(isReportDriven(selected) ? selected.id : null);
  const catalogEditions = useStrategyEditions(reportStrategies);
  const editionsById = new Map(
    reportStrategies.map((strategy, index) => [strategy.id, catalogEditions[index]?.data ?? []]),
  );

  return (
    <section>
      <div className="mb-4 flex items-start justify-between gap-3">
        <div>
          <h1 className="font-cond text-[24px] font-bold tracking-[.005em]">Estratégias</h1>
          <p className="text-[13px] text-ink-dim">
            As carteiras da XP, edição por edição. O diff mês a mês e os alvos por ticker.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="default" onClick={() => setCreating(true)}>
            Nova estratégia
          </Button>
          <Link to="/strategies/import">
            <Button variant="default">Importar relatórios</Button>
          </Link>
        </div>
      </div>

      {strategies.isLoading ? (
        <p className="text-[13.5px] text-ink-dim">Carregando…</p>
      ) : strategies.isError ? (
        <p className="text-[13.5px] text-tax">{strategies.error.message}</p>
      ) : (
        <div className="flex gap-5">
          <nav className="w-[250px] shrink-0 space-y-4">
            {groups.map((group) => (
              <div key={group.assetClass}>
                <h5 className="flex items-baseline justify-between gap-2 px-2 pb-1 font-mono text-[10px] font-medium tracking-[.12em] text-ink-faint uppercase">
                  <span>{assetClassLabel(group.assetClass)}</span>
                  <span className="tracking-[.02em] normal-case">
                    {strategyCoverageLabel(
                      strategyCoverage(group.strategies, editionsById, currentMonth),
                    )}
                  </span>
                </h5>
                <ul className="space-y-1">
                  {group.strategies.map((strategy) => (
                    <li key={strategy.id}>
                      <button
                        type="button"
                        onClick={() => setSelectedId(strategy.id)}
                        className={cn(
                          "flex w-full items-center justify-between gap-2 rounded-md border px-3 py-2 text-left",
                          strategy.id === selected?.id
                            ? "border-accent bg-surface-2"
                            : "border-line bg-surface hover:bg-surface-2",
                        )}
                      >
                        <span className="text-[13.5px] text-ink">{strategy.name}</span>
                        <StrategyStatusChip
                          strategy={strategy}
                          editions={editionsById.get(strategy.id)}
                        />
                      </button>
                    </li>
                  ))}
                </ul>
              </div>
            ))}
            {groups.length === 0 && (
              <p className="text-[13px] text-ink-dim">Nenhuma estratégia cadastrada.</p>
            )}
          </nav>

          <div className="min-w-0 flex-1">
            {selected ? (
              <StrategyDetail
                strategy={selected}
                editions={editions.data ?? []}
                loading={editions.isLoading}
              />
            ) : (
              <Panel>
                <p className="text-[13px] text-ink-dim">
                  Cadastre as estratégias da XP para ver as edições.
                </p>
              </Panel>
            )}
          </div>
        </div>
      )}

      {creating && (
        <CreateStrategyModal
          strategies={list}
          onClose={() => setCreating(false)}
          onCreated={(created) => {
            setSelectedId(created.id);
            setCreating(false);
          }}
        />
      )}
    </section>
  );
}

function StrategyStatusChip({
  strategy,
  editions,
}: {
  strategy: Strategy;
  editions?: Edition[];
}) {
  if (!isReportDriven(strategy)) return <Chip tone="violet">Legado</Chip>;
  const status = editionStatusChip(editions);
  return <Chip tone={status.tone}>{status.label}</Chip>;
}

function StrategyDetail({
  strategy,
  editions,
  loading,
}: {
  strategy: Strategy;
  editions: Edition[];
  loading: boolean;
}) {
  const currentMonth = useCurrentMonth().data?.month.period;
  const latest = editions[0] ?? null;

  const columns: Column<EditionEntry>[] = [
    {
      key: "ticker",
      header: "Ticker",
      render: (entry) => <span className="font-mono font-medium">{entry.ticker}</span>,
    },
    {
      key: "weight",
      header: "Peso",
      align: "right",
      render: (entry) => `${entry.weight}%`,
    },
    {
      key: "rating",
      header: "Rating",
      render: (entry) => ratingLabel(entry.rating),
    },
    {
      key: "target",
      header: "Preço-alvo",
      align: "right",
      render: (entry) => (entry.target_price == null ? "—" : formatCurrency(entry.target_price)),
    },
    {
      key: "do_not_trade",
      header: "Não operar",
      render: (entry) =>
        entry.do_not_trade ? (
          <Chip tone="rose">Não operar</Chip>
        ) : (
          <span className="text-ink-faint">—</span>
        ),
    },
  ];

  return (
    <>
      <div className="mb-4 flex items-start justify-between gap-3">
        <div>
          <h2 className="font-cond text-[19px] font-bold">{strategy.name}</h2>
          <p className="flex items-center gap-1.5 text-[12.5px] text-ink-dim">
            <span>{assetClassLabel(strategy.asset_class)}</span>
            <StrategyStatusChip strategy={strategy} editions={editions} />
          </p>
        </div>
        {isReportDriven(strategy) && (
          <Link to={`/strategies/import?strategy=${strategy.id}`}>
            <Button variant="default">Importar relatório</Button>
          </Link>
        )}
      </div>

      {!isReportDriven(strategy) && (
        <Panel>
          <p className="text-[13px] text-ink-dim">
            Estratégia manual (legado): guarda as posições desta classe sem edição e sem alvos.
            Atribua ou transfira posições na Alocação.
          </p>
        </Panel>
      )}

      {isReportDriven(strategy) && (
        <>
          <div className="mb-4 flex flex-wrap gap-1.5">
            {editions.map((edition) => (
              <Chip key={edition.id} tone={edition.status === "ACTIVE" ? "green" : "amber"}>
                {referenceMonthLabel(edition.reference_month)}
                {edition.status === "ACTIVE" ? " · ativa" : " · em revisão"}
                {currentMonth && isEditionForMonth(edition, currentMonth) ? " · mês atual" : ""}
              </Chip>
            ))}
          </div>

          {latest?.diff_from_previous && (
            <div className="mb-4">
              <DiffPanel diff={latest.diff_from_previous} />
            </div>
          )}

          {loading ? (
            <p className="text-[13.5px] text-ink-dim">Carregando edições…</p>
          ) : latest ? (
            <Table columns={columns} rows={latest.entries} rowKey={(entry) => entry.ticker} />
          ) : (
            <Panel>
              <p className="text-[13px] text-ink-dim">
                Sem edição importada. Importe o relatório do mês para ver os alvos.
              </p>
            </Panel>
          )}
        </>
      )}
    </>
  );
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

function DiffPanel({ diff }: { diff: EditionDiff }) {
  const hasMoves = diff.entered.length + diff.exited.length + diff.changed.length > 0;

  return (
    <Panel title="Mudanças vs. edição anterior">
      <div className="flex flex-wrap gap-1.5">
        {diff.entered.map((entry) => (
          <Chip key={`entered-${entry.ticker}`} tone="green">
            + {entry.ticker} {entry.weight}%
          </Chip>
        ))}
        {diff.exited.map((entry) => (
          <Chip key={`exited-${entry.ticker}`} tone="rose">
            − {entry.ticker}
          </Chip>
        ))}
        {diff.changed.map((change) => (
          <Chip key={`changed-${change.ticker}`} tone="neutral">
            {change.ticker} {change.weight_before}%→{change.weight_after}%
            {change.rating_before === change.rating_after
              ? ""
              : ` · ${ratingLabel(change.rating_before)}→${ratingLabel(change.rating_after)}`}
          </Chip>
        ))}
        {!hasMoves && <span className="text-[12.5px] text-ink-dim">Sem mudanças.</span>}
      </div>
    </Panel>
  );
}

type ImportStatus = "pending" | "importing" | "staged" | "failed";

interface ImportRow {
  id: string;
  file: File;
  strategyId: number;
  status: ImportStatus;
  parser?: string;
  edition?: Edition;
  error?: string;
}

function StrategyImportView() {
  const strategies = useStrategies();
  const importEdition = useImportEdition();
  const [params] = useSearchParams();
  const [rows, setRows] = useState<ImportRow[]>([]);
  const [reviewingId, setReviewingId] = useState<string | null>(null);

  // Only a report-driven Strategy can receive an edition, so the bucket is never offered here.
  const reportStrategies = reportDrivenStrategies(strategies.data ?? []);
  const preselectedParam = Number(params.get("strategy")) || null;
  const preselected = reportStrategies.some((strategy) => strategy.id === preselectedParam)
    ? preselectedParam
    : null;
  const reviewingRow = rows.find((row) => row.id === reviewingId);
  const reviewing = reviewingRow?.edition ?? null;

  // Every row update replaces one row immutably; the id is the row's identity, so the patch can never
  // land on a different row than the one the handler was bound to.
  function updateRow(id: string, patch: Partial<ImportRow>) {
    setRows((current) => current.map((row) => (row.id === id ? { ...row, ...patch } : row)));
  }

  function addFiles(event: ChangeEvent<HTMLInputElement>) {
    const files = Array.from(event.target.files ?? []);
    const additions: ImportRow[] = files.map((file) => ({
      id: crypto.randomUUID(),
      file,
      strategyId: preselected ?? suggestStrategy(reportStrategies, file.name)?.id ?? 0,
      status: "pending",
    }));
    setRows((current) => [...current, ...additions]);
    event.target.value = "";
  }

  async function importRow(row: ImportRow) {
    if (!row.strategyId) return;
    updateRow(row.id, { status: "importing", error: undefined });
    try {
      const imported = await importEdition.mutateAsync({
        strategyId: row.strategyId,
        file: row.file,
      });
      updateRow(row.id, {
        status: "staged",
        edition: imported.edition,
        parser: imported.parser,
      });
    } catch (error) {
      updateRow(row.id, {
        status: "failed",
        error: error instanceof Error ? error.message : "Falha ao importar",
      });
    }
  }

  async function importPending() {
    for (const row of rows) {
      if (row.status === "pending" || row.status === "failed") {
        await importRow(row);
      }
    }
  }

  const columns: Column<ImportRow>[] = [
    {
      key: "file",
      header: "Arquivo",
      render: (row) => (
        <span className="font-mono text-[11.5px] text-ink-dim">{row.file.name}</span>
      ),
    },
    {
      key: "strategy",
      header: "Estratégia sugerida",
      render: (row) =>
        reportStrategies.length === 0 ? (
          <span className="text-[12.5px] text-ink-dim">Nenhuma estratégia com relatório</span>
        ) : (
          <select
            value={row.strategyId}
            onChange={(event) => updateRow(row.id, { strategyId: Number(event.target.value) })}
            className={FIELD}
          >
            {reportStrategies.map((strategy) => (
              <option key={strategy.id} value={strategy.id}>
                {strategy.name}
              </option>
            ))}
          </select>
        ),
    },
    {
      key: "parser",
      header: "Parser",
      render: (row) => (
        <span className="font-mono text-[11.5px] text-ink-dim">{row.parser ?? "—"}</span>
      ),
    },
    {
      key: "month",
      header: "Mês",
      render: (row) => (row.edition ? referenceMonthLabel(row.edition.reference_month) : "—"),
    },
    {
      key: "targets",
      header: "Alvos",
      align: "right",
      render: (row) => (row.edition ? row.edition.entries.length : "—"),
    },
    {
      key: "sum",
      header: "Soma",
      align: "right",
      render: (row) =>
        row.edition ? (
          <Chip tone={isWeightSumValid(row.edition.entries) ? "green" : "rose"}>
            {editionWeightSum(row.edition.entries)}%
          </Chip>
        ) : (
          "—"
        ),
    },
    {
      key: "diff",
      header: "Diff",
      render: (row) => editionDiffSummary(row.edition?.diff_from_previous),
    },
    {
      key: "action",
      header: "",
      render: (row) => {
        if (row.status === "importing") return <span className="text-ink-dim">Importando…</span>;
        if (row.status === "failed") {
          return (
            <div className="flex flex-col items-start gap-1">
              <span className="text-[12px] text-tax">{row.error}</span>
              <Button variant="ghost" onClick={() => importRow(row)}>
                Tentar de novo
              </Button>
            </div>
          );
        }
        if (row.status === "staged") {
          return (
            <Button variant="default" onClick={() => setReviewingId(row.id)}>
              Revisar
            </Button>
          );
        }
        return (
          <Button variant="primary" onClick={() => importRow(row)} disabled={!row.strategyId}>
            Importar
          </Button>
        );
      },
    },
  ];

  return (
    <section>
      <div className="mb-4 flex items-start justify-between gap-3">
        <div>
          <h1 className="font-cond text-[24px] font-bold tracking-[.005em]">
            Importar relatórios da XP
          </h1>
          <p className="text-[13px] text-ink-dim">
            Solte os PDFs de cada estratégia. O parser sugere a estratégia e o mês; a edição fica em
            prévia até a confirmação.
          </p>
        </div>
        <Link to="/strategies">
          <Button variant="ghost">Voltar</Button>
        </Link>
      </div>

      <Panel>
        <input
          type="file"
          accept=".pdf"
          multiple
          onChange={addFiles}
          className="block w-full text-[13px] text-ink-dim file:mr-3 file:rounded-sm file:border-0 file:bg-surface-2 file:px-3 file:py-1.5 file:text-ink"
        />
      </Panel>

      {rows.length > 0 && (
        <div className="mt-4 space-y-3">
          <Table columns={columns} rows={rows} rowKey={(row) => row.id} />
          <Button
            variant="primary"
            onClick={importPending}
            disabled={importEdition.isPending || reportStrategies.length === 0}
          >
            {importEdition.isPending ? "Importando…" : "Importar pendentes"}
          </Button>
        </div>
      )}

      {reviewing && (
        <PreviewDrawer
          key={reviewing.id}
          edition={reviewing}
          parser={reviewingRow?.parser}
          onClose={() => setReviewingId(null)}
        />
      )}
    </section>
  );
}

function PreviewDrawer({
  edition,
  parser,
  onClose,
}: {
  edition: Edition;
  parser?: string;
  onClose: () => void;
}) {
  const [entries, setEntries] = useState<EditionEntry[]>(() => edition.entries);
  const [submitAttempted, setSubmitAttempted] = useState(false);
  const activate = useActivateEdition();
  const sum = editionWeightSum(entries);
  const sumValid = isWeightSumValid(entries);
  // The stored diff describes the imported set; once an override lands it no longer does, and the
  // server recomputes it at activation — so the drawer stops showing the stale delta and says so.
  const edited = entries.some((entry, index) => entry !== edition.entries[index]);

  function submit() {
    setSubmitAttempted(true);
    if (!sumValid) return;
    activate.mutate(
      {
        strategyId: edition.strategy_id,
        activation: { reference_month: edition.reference_month, entries },
      },
      { onSuccess: onClose },
    );
  }

  return (
    <dialog
      open
      aria-modal="true"
      aria-label="Revisar edição"
      className="fixed inset-0 z-50 m-0 flex h-full w-full max-w-none items-stretch justify-end border-0 bg-transparent p-0"
    >
      <button
        type="button"
        aria-label="Fechar"
        onClick={onClose}
        className="absolute inset-0 bg-ground/70"
      />
      <div className="relative flex h-full w-[620px] max-w-full flex-col border-l border-line bg-surface">
        <div className="flex items-center justify-between border-b border-line px-4 py-3">
          <h3 className="font-cond text-[16px] font-bold">Revisar edição</h3>
          <Button variant="ghost" onClick={onClose}>
            Fechar
          </Button>
        </div>

        <div className="flex-1 overflow-y-auto p-4">
          <div className="mb-3 flex flex-wrap items-center gap-1.5">
            <Chip tone="neutral">{referenceMonthLabel(edition.reference_month)}</Chip>
            {parser && <Chip tone="neutral">{parser}</Chip>}
            <Chip tone={sumValid ? "green" : "rose"}>soma {sum}%</Chip>
            {edition.diff_from_previous && !edited && (
              <Chip tone="neutral">{editionDiffSummary(edition.diff_from_previous)}</Chip>
            )}
          </div>

          {edition.diff_from_previous &&
            (edited ? (
              <div className="mb-4">
                <Panel>
                  <p className="text-[12.5px] text-ink-dim">
                    Você editou os alvos; o diff será recalculado ao confirmar a edição.
                  </p>
                </Panel>
              </div>
            ) : (
              <div className="mb-4">
                <DiffPanel diff={edition.diff_from_previous} />
              </div>
            ))}

          <div className="space-y-2">
            {entries.map((entry, index) => (
              <div
                key={entry.ticker}
                className="grid grid-cols-[1fr_88px_112px_104px_auto] items-center gap-2"
              >
                <span className="font-mono text-[13px] font-medium">{entry.ticker}</span>
                <input
                  type="number"
                  step="0.01"
                  aria-label={`Peso de ${entry.ticker}`}
                  value={entry.weight}
                  onChange={(event) =>
                    setEntries((current) =>
                      overrideEntry(current, index, {
                        weight: Number(event.target.value),
                      }),
                    )
                  }
                  className={FIELD}
                />
                <select
                  aria-label={`Rating de ${entry.ticker}`}
                  value={entry.rating ?? ""}
                  onChange={(event) =>
                    setEntries((current) =>
                      overrideEntry(current, index, {
                        rating: (event.target.value || undefined) as Rating | undefined,
                      }),
                    )
                  }
                  className={FIELD}
                >
                  <option value="">—</option>
                  {RATINGS.map((rating) => (
                    <option key={rating} value={rating}>
                      {ratingLabel(rating)}
                    </option>
                  ))}
                </select>
                <input
                  type="number"
                  step="0.01"
                  aria-label={`Preço-alvo de ${entry.ticker}`}
                  value={entry.target_price ?? ""}
                  onChange={(event) =>
                    setEntries((current) =>
                      overrideEntry(current, index, {
                        target_price:
                          event.target.value === "" ? undefined : Number(event.target.value),
                      }),
                    )
                  }
                  className={FIELD}
                />
                <label className="flex items-center gap-1 text-[11.5px] text-ink-dim">
                  <input
                    type="checkbox"
                    checked={entry.do_not_trade}
                    onChange={(event) =>
                      setEntries((current) =>
                        overrideEntry(current, index, {
                          do_not_trade: event.target.checked,
                        }),
                      )
                    }
                  />
                  Não operar
                </label>
              </div>
            ))}
          </div>

          {!sumValid && submitAttempted && (
            <p className="mt-3 text-[12.5px] text-tax">A soma dos pesos deve ser 100%.</p>
          )}
          {activate.error && (
            <p className="mt-3 text-[12.5px] text-tax">{activate.error.message}</p>
          )}
        </div>

        <div className="flex justify-end gap-2 border-t border-line px-4 py-3">
          <Button variant="ghost" onClick={onClose}>
            Cancelar
          </Button>
          <Button variant="primary" onClick={submit} disabled={activate.isPending}>
            {activate.isPending ? "Confirmando…" : "Confirmar edição"}
          </Button>
        </div>
      </div>
    </dialog>
  );
}
