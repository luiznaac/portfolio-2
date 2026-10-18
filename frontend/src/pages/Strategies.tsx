import { type ChangeEvent, useState } from "react";
import { Link, useLocation, useSearchParams } from "react-router-dom";
import {
  useActivateEdition,
  useCurrentMonth,
  useEditions,
  useImportEdition,
  useStrategies,
} from "../api/queries.ts";
import type { Edition, EditionDiff, EditionEntry, Rating, Strategy } from "../api/types.ts";
import { Button } from "../components/Button.tsx";
import { Chip } from "../components/Chip.tsx";
import { Panel } from "../components/Panel.tsx";
import { type Column, Table } from "../components/Table.tsx";
import { cn } from "../lib/cn.ts";
import { formatCurrency } from "../lib/format.ts";
import { assetClassLabel } from "../lib/month.ts";
import {
  editionDiffSummary,
  editionWeightSum,
  groupStrategiesByClass,
  isEditionForMonth,
  isWeightSumValid,
  overrideEntry,
  ratingLabel,
  referenceMonthLabel,
  suggestStrategy,
} from "../lib/strategy.ts";

const FIELD = "rounded-sm border border-line bg-surface-2 px-2 py-1.5 text-[13px] text-ink";

const RATINGS: Rating[] = ["BUY", "NEUTRAL", "SELL"];

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
  const [selectedId, setSelectedId] = useState<number | null>(null);

  const list = strategies.data ?? [];
  const selected = list.find((strategy) => strategy.id === selectedId) ?? list[0] ?? null;
  const editions = useEditions(selected?.id ?? null);
  const groups = groupStrategiesByClass(list);

  return (
    <section>
      <div className="mb-4 flex items-start justify-between gap-3">
        <div>
          <h1 className="font-cond text-[24px] font-bold tracking-[.005em]">Estratégias</h1>
          <p className="text-[13px] text-ink-dim">
            As carteiras da XP, edição por edição. O diff mês a mês e os alvos por ticker.
          </p>
        </div>
        <Link to="/strategies/import">
          <Button variant="default">Importar relatórios</Button>
        </Link>
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
                <h5 className="px-2 pb-1 font-mono text-[10px] font-medium tracking-[.12em] text-ink-faint uppercase">
                  {assetClassLabel(group.assetClass)}
                </h5>
                <ul className="space-y-1">
                  {group.strategies.map((strategy) => (
                    <li key={strategy.id}>
                      <button
                        type="button"
                        onClick={() => setSelectedId(strategy.id)}
                        className={cn(
                          "w-full rounded-md border px-3 py-2 text-left",
                          strategy.id === selected?.id
                            ? "border-accent bg-surface-2"
                            : "border-line bg-surface hover:bg-surface-2",
                        )}
                      >
                        <span className="block text-[13.5px] text-ink">{strategy.name}</span>
                        <span className="font-mono text-[11px] text-ink-faint">
                          peso {strategy.weight}% do bloco
                        </span>
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
    </section>
  );
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
    { key: "weight", header: "Peso", align: "right", render: (entry) => `${entry.weight}%` },
    { key: "rating", header: "Rating", render: (entry) => ratingLabel(entry.rating) },
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
          <p className="text-[12.5px] text-ink-dim">
            {assetClassLabel(strategy.asset_class)} · peso{" "}
            <b className="font-mono">{strategy.weight}%</b> do bloco
          </p>
        </div>
        <Link to={`/strategies/import?strategy=${strategy.id}`}>
          <Button variant="default">Importar relatório</Button>
        </Link>
      </div>

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

  const list = strategies.data ?? [];
  const preselected = Number(params.get("strategy")) || null;
  const reviewing = rows.find((row) => row.id === reviewingId)?.edition ?? null;

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
      strategyId: preselected ?? suggestStrategy(list, file.name)?.id ?? 0,
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
      updateRow(row.id, { status: "staged", edition: imported.edition, parser: imported.parser });
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
      render: (row) => (
        <select
          value={row.strategyId}
          onChange={(event) => updateRow(row.id, { strategyId: Number(event.target.value) })}
          className={FIELD}
        >
          {list.map((strategy) => (
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
          <Button variant="primary" onClick={importPending} disabled={importEdition.isPending}>
            {importEdition.isPending ? "Importando…" : "Importar pendentes"}
          </Button>
        </div>
      )}

      {reviewing && (
        <PreviewDrawer
          key={reviewing.id}
          edition={reviewing}
          onClose={() => setReviewingId(null)}
        />
      )}
    </section>
  );
}

function PreviewDrawer({ edition, onClose }: { edition: Edition; onClose: () => void }) {
  const [entries, setEntries] = useState<EditionEntry[]>(() => edition.entries);
  const [validation, setValidation] = useState<string | null>(null);
  const activate = useActivateEdition();
  const sum = editionWeightSum(entries);

  function submit() {
    if (!isWeightSumValid(entries)) {
      setValidation("A soma dos pesos deve ser 100%.");
      return;
    }
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
            <Chip tone={isWeightSumValid(entries) ? "green" : "rose"}>soma {sum}%</Chip>
            {edition.diff_from_previous && (
              <Chip tone="neutral">{editionDiffSummary(edition.diff_from_previous)}</Chip>
            )}
          </div>

          {edition.diff_from_previous && (
            <div className="mb-4">
              <DiffPanel diff={edition.diff_from_previous} />
            </div>
          )}

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
                      overrideEntry(current, index, { weight: Number(event.target.value) }),
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
                        overrideEntry(current, index, { do_not_trade: event.target.checked }),
                      )
                    }
                  />
                  Não operar
                </label>
              </div>
            ))}
          </div>

          {validation && <p className="mt-3 text-[12.5px] text-tax">{validation}</p>}
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
