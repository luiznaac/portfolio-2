import { type ChangeEvent, useState } from "react";
import { Link, useLocation, useSearchParams } from "react-router-dom";
import {
  useActivateEdition,
  useCreateStrategy,
  useCurrentAllocation,
  useImportEdition,
  useStrategies,
} from "../api/queries.ts";
import type {
  AssetClass,
  Edition,
  EditionEntry,
  EditionSummary,
  Rating,
  Strategy,
  StrategyKind,
} from "../api/types.ts";
import { Button } from "../components/Button.tsx";
import { Chip } from "../components/Chip.tsx";
import { EditionDiffPanel } from "../components/EditionDiffPanel.tsx";
import { Field, Input } from "../components/Field.tsx";
import { Panel } from "../components/Panel.tsx";
import { type Column, Table } from "../components/Table.tsx";
import { formatPoints } from "../lib/allocation.ts";
import { cn } from "../lib/cn.ts";
import { formatCurrency } from "../lib/format.ts";
import { ASSET_CLASSES, assetClassLabel } from "../lib/month.ts";
import { ROUTES, strategyDetail } from "../lib/routes.ts";
import {
  type StrategyListRow,
  editionDiffSummary,
  editionSummaryChip,
  editionWeightSum,
  isReportDriven,
  isWeightSumValid,
  overrideEntry,
  ratingLabel,
  referenceMonthLabel,
  reportDrivenStrategies,
  strategyKindLabel,
  strategyRows,
  suggestStrategy,
} from "../lib/strategy.ts";

const FIELD = "rounded-sm border border-line bg-surface-2 px-2 py-1.5 text-[13px] text-ink";

const RATINGS: Rating[] = ["BUY", "NEUTRAL", "SELL"];

// A Strategy is created in one of the two listed-asset classes; Renda Fixa is the consolidation
// engine's domain and never holds a Strategy.
const STRATEGY_CLASSES: AssetClass[] = ASSET_CLASSES.filter(
  (assetClass) => assetClass !== "RENDA_FIXA",
);

// The Estratégias screen: the comparison list (issue #373) and the batch report import. The list is
// one bounded table over the whole catalog — no filter bar — reading GET /strategies (which carries
// `latest_edition`) and the allocation tree for value/drift; a row opens the strategy detail (#374).
// The import view stages each PDF through POST .../editions/import and confirms through
// POST .../editions.
export function Strategies() {
  const { pathname } = useLocation();
  return pathname.endsWith("/import") ? <StrategyImportView /> : <StrategyListView />;
}

function StrategyListView() {
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
        <Link to={ROUTES.strategiesImport}>
          <Button variant="default">Importar relatórios</Button>
        </Link>
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
      <div className="mb-4 flex items-center justify-end gap-3">
        <Link to={ROUTES.strategies}>
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
                <EditionDiffPanel diff={edition.diff_from_previous} />
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
