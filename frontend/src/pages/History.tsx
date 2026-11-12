import { useState } from "react";
import {
  useBackfill,
  useCurrentAllocation,
  useGaps,
  usePriceGap,
  useReconcileCustody,
  useSeedOpenings,
  useTickerQuotesList,
  useTrades,
} from "../api/queries.ts";
import type {
  BackfillReport,
  Gap,
  HeldTicker,
  OpeningSeedCreation,
  PositionReconciliation,
  Trade,
} from "../api/types.ts";
import { Button } from "../components/Button.tsx";
import { Chip } from "../components/Chip.tsx";
import { Panel } from "../components/Panel.tsx";
import { formatCurrency, formatIsoDate, formatNumber } from "../lib/format.ts";
import {
  type AdoptionRow,
  adoptionDefaultDate,
  adoptionRows,
  isMissingBuy,
  isValidAdoptionRow,
  latestClose,
  openGaps,
} from "../lib/history.ts";

// The Histórico screen (issue #307): adoption, replay, gaps and custody in one place. The backend
// commands existed first; this page gives them an obvious home and makes a mid-history import
// repairable. `POST /positions/reproject` deliberately has no control here.
export function History() {
  const allocation = useCurrentAllocation();
  const trades = useTrades();

  if (allocation.isLoading || trades.isLoading) {
    return <p className="text-[13.5px] text-ink-dim">Carregando…</p>;
  }

  return (
    <HistoryScreen initialNegative={allocation.data?.negative ?? []} trades={trades.data ?? []} />
  );
}

function HistoryScreen({
  initialNegative,
  trades,
}: {
  initialNegative: HeldTicker[];
  trades: Trade[];
}) {
  const [adoption, setAdoption] = useState<"pending" | "saved" | "skipped">("pending");
  const defaultDate = adoptionDefaultDate(trades);
  const adoptionKey = `${initialNegative.map((row) => `${row.ticker}:${row.quantity}`).join(",")}|${defaultDate}`;

  return (
    <section className="space-y-4">
      <AdoptionStep
        key={adoptionKey}
        initialNegative={initialNegative}
        defaultDate={defaultDate}
        saved={adoption === "saved"}
        onSaved={() => setAdoption("saved")}
        onSkip={() => setAdoption("skipped")}
      />

      <BackfillStep enabled={adoption !== "pending"} />

      <LacunasStep />

      <CustodyStep />
    </section>
  );
}

interface AdoptionDraft {
  key: string;
  ticker: string;
  fixedTicker: boolean;
  quantity: string;
  dateInput: string | null;
  priceInput: string | null;
}

function draftsFrom(negative: HeldTicker[]): AdoptionDraft[] {
  return adoptionRows(negative).map((row) => ({
    key: row.ticker,
    ticker: row.ticker,
    fixedTicker: true,
    quantity: String(row.quantity),
    dateInput: null,
    priceInput: null,
  }));
}

function AdoptionStep({
  initialNegative,
  defaultDate,
  saved,
  onSaved,
  onSkip,
}: {
  initialNegative: HeldTicker[];
  defaultDate: string;
  saved: boolean;
  onSaved: () => void;
  onSkip: () => void;
}) {
  const seedOpenings = useSeedOpenings();
  const [drafts, setDrafts] = useState<AdoptionDraft[]>(() => draftsFrom(initialNegative));
  const [nextBlank, setNextBlank] = useState(0);

  const tickers = [...new Set(drafts.map((draft) => draft.ticker.trim().toUpperCase()))].filter(
    (ticker) => ticker.length > 0,
  );
  const quoteQueries = useTickerQuotesList(tickers);
  const closeByTicker = new Map(
    tickers.map((ticker, index) => [ticker, latestClose(quoteQueries[index]?.data ?? [])]),
  );

  const resolved = drafts.map((draft) => resolveDraft(draft, defaultDate, closeByTicker));
  const canSave =
    resolved.length > 0 && resolved.every(isValidAdoptionRow) && !seedOpenings.isPending;

  function update(key: string, change: Partial<AdoptionDraft>) {
    setDrafts((current) =>
      current.map((draft) => (draft.key === key ? { ...draft, ...change } : draft)),
    );
  }

  function addTicker() {
    const key = `new-${nextBlank}`;
    setNextBlank((current) => current + 1);
    setDrafts((current) => [
      ...current,
      {
        key,
        ticker: "",
        fixedTicker: false,
        quantity: "",
        dateInput: null,
        priceInput: null,
      },
    ]);
  }

  function save() {
    const seeds: OpeningSeedCreation[] = resolved.map((row) => ({
      ticker: row.ticker,
      quantity: row.quantity,
      unit_price: row.unitPrice,
      date: row.date,
    }));
    seedOpenings.mutate(seeds, { onSuccess: onSaved });
  }

  return (
    <Panel title="1 · Adoção" action={<Chip tone={saved ? "green" : "amber"}>abertura</Chip>}>
      <p className="mb-3 text-[12.5px] text-ink-dim">
        As posições negativas são custódia anterior à janela exportada. Cada linha vira uma compra
        de abertura na data e no preço informados; a reconciliação a absorve quando o histórico
        cobrir a quantidade.
      </p>

      {drafts.length === 0 ? (
        <p className="mb-3 text-[13px] text-yield">Nenhuma posição negativa para adotar.</p>
      ) : (
        <div className="mb-3 overflow-x-auto rounded-lg border border-line">
          <table className="w-full border-collapse text-[13px]">
            <thead>
              <tr className="bg-surface-2 font-mono text-[10px] tracking-[.09em] text-ink-faint uppercase">
                <th className="px-3 py-2 text-left">Ativo</th>
                <th className="px-3 py-2 text-right">Quantidade</th>
                <th className="px-3 py-2 text-left">Data</th>
                <th className="px-3 py-2 text-right">Preço unitário</th>
              </tr>
            </thead>
            <tbody>
              {drafts.map((draft) => {
                const close = closeByTicker.get(draft.ticker.trim().toUpperCase()) ?? null;
                const priceValue = draft.priceInput ?? (close === null ? "" : String(close));
                return (
                  <tr key={draft.key} className="border-t border-line-soft">
                    <td className="px-3 py-1.5">
                      {draft.fixedTicker ? (
                        <span className="font-mono">{draft.ticker}</span>
                      ) : (
                        <input
                          aria-label="Ticker"
                          value={draft.ticker}
                          onChange={(event) =>
                            update(draft.key, { ticker: event.target.value.toUpperCase() })
                          }
                          className="w-24 rounded-sm border border-line bg-surface-2 px-2 py-1 font-mono text-[13px]"
                        />
                      )}
                    </td>
                    <td className="px-3 py-1.5 text-right">
                      <input
                        aria-label={`Quantidade ${draft.ticker}`}
                        data-testid={`adoption-quantity-${draft.ticker}`}
                        type="number"
                        min="1"
                        step="1"
                        value={draft.quantity}
                        onChange={(event) => update(draft.key, { quantity: event.target.value })}
                        className="w-24 rounded-sm border border-line bg-surface-2 px-2 py-1 text-right font-mono text-[13px]"
                      />
                    </td>
                    <td className="px-3 py-1.5">
                      <input
                        aria-label={`Data ${draft.ticker}`}
                        data-testid={`adoption-date-${draft.ticker}`}
                        type="date"
                        value={draft.dateInput ?? defaultDate}
                        onChange={(event) => update(draft.key, { dateInput: event.target.value })}
                        className="rounded-sm border border-line bg-surface-2 px-2 py-1 font-mono text-[13px]"
                      />
                    </td>
                    <td className="px-3 py-1.5 text-right">
                      <input
                        aria-label={`Preço unitário ${draft.ticker}`}
                        data-testid={`adoption-price-${draft.ticker}`}
                        type="number"
                        min="0"
                        step="0.01"
                        value={priceValue}
                        onChange={(event) => update(draft.key, { priceInput: event.target.value })}
                        className="w-24 rounded-sm border border-line bg-surface-2 px-2 py-1 text-right font-mono text-[13px]"
                      />
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      <div className="flex flex-wrap items-center gap-2">
        <Button onClick={addTicker}>Adicionar ticker</Button>
        <Button variant="primary" onClick={save} disabled={!canSave || saved}>
          {seedOpenings.isPending ? "Salvando…" : saved ? "Adoção salva" : "Salvar adoção"}
        </Button>
        <Button variant="ghost" onClick={onSkip}>
          Pular adoção
        </Button>
        {saved && (
          <span data-testid="adoption-saved" className="text-[12.5px] text-yield">
            Adoção salva — a reconciliação está liberada.
          </span>
        )}
      </div>

      {seedOpenings.error && (
        <p className="mt-2 text-[12.5px] text-tax">{seedOpenings.error.message}</p>
      )}
    </Panel>
  );
}

function resolveDraft(
  draft: AdoptionDraft,
  defaultDate: string,
  closeByTicker: Map<string, number | null>,
): AdoptionRow {
  const ticker = draft.ticker.trim().toUpperCase();
  const close = closeByTicker.get(ticker) ?? null;
  const unitPrice = draft.priceInput === null ? (close ?? Number.NaN) : Number(draft.priceInput);

  return {
    ticker,
    quantity: Number(draft.quantity),
    date: draft.dateInput ?? defaultDate,
    unitPrice,
  };
}

// The two history uploads share one look: a labelled .xlsx picker beside the action it enables.
function FileField({
  label,
  onFile,
}: {
  label: string;
  onFile: (file: File | null) => void;
}) {
  return (
    <label className="flex flex-col gap-1 text-[11.5px] text-ink-dim">
      {label}
      <input
        aria-label={label}
        type="file"
        accept=".xlsx"
        onChange={(event) => onFile(event.target.files?.[0] ?? null)}
        className="block text-[13px] text-ink-dim file:mr-3 file:rounded-sm file:border-0 file:bg-surface-2 file:px-3 file:py-1.5 file:text-ink"
      />
    </label>
  );
}

function BackfillStep({ enabled }: { enabled: boolean }) {
  const backfill = useBackfill();
  const [file, setFile] = useState<File | null>(null);

  return (
    <Panel title="2 · Reconciliar histórico">
      <p className="mb-3 text-[12.5px] text-ink-dim">
        Reexecuta o histórico importado: aposenta as compras de abertura cobertas, substitui as
        lacunas explicadas por registros novos e reprojeta o livro. A Posição é opcional e serve de
        conferência da custódia no fim da janela.
      </p>

      <div className="flex flex-wrap items-end gap-3">
        <FileField label="Posição (opcional)" onFile={setFile} />
        <Button
          variant="primary"
          onClick={() => backfill.mutate(file ?? undefined)}
          disabled={!enabled || backfill.isPending}
        >
          {backfill.isPending ? "Reconciliando…" : "Reconciliar histórico"}
        </Button>
      </div>

      {!enabled && (
        <p data-testid="backfill-gate" className="mt-2 text-[12.5px] text-accent">
          Salve ou pule a adoção antes de reconciliar o histórico.
        </p>
      )}

      {backfill.error && <p className="mt-2 text-[12.5px] text-tax">{backfill.error.message}</p>}

      {backfill.data && <BackfillReportBody report={backfill.data} />}
    </Panel>
  );
}

function BackfillReportBody({ report }: { report: BackfillReport }) {
  return (
    <div
      data-testid="backfill-report"
      className="mt-3 rounded-sm border border-line bg-surface-2 px-3 py-2.5 text-[12.5px] text-ink"
    >
      <p>
        {report.absorbed_seeds} seeds absorvidos · {report.adjusted_seeds} ajustados ·{" "}
        {report.gaps_created} lacunas criadas · {report.gaps_replaced} lacunas substituídas
      </p>
      <p data-testid="backfill-reprojected" className="mt-1 font-mono text-[12px] text-ink-dim">
        {report.positions_reprojected} posições reprojetadas
      </p>
    </div>
  );
}

function LacunasStep() {
  const gaps = useGaps();
  const open = openGaps(gaps.data ?? []);

  return (
    <Panel title="3 · Lacunas">
      <p className="mb-3 text-[12.5px] text-ink-dim">
        Uma lacuna é um delta de custódia que o livro não explica: uma compra faltante entra pelo
        preço de mercado (aproximado) e uma venda faltante espera os proventos que você informar.
      </p>

      {gaps.isLoading ? (
        <p className="text-[13px] text-ink-dim">Carregando…</p>
      ) : open.length === 0 ? (
        <p className="text-[13px] text-yield">Nenhuma lacuna aberta.</p>
      ) : (
        <div data-testid="lacunas" className="divide-y divide-line-soft">
          {open.map((gap) => (
            <GapRow key={gap.id} gap={gap} />
          ))}
        </div>
      )}
    </Panel>
  );
}

function GapRow({ gap }: { gap: Gap }) {
  const priceGap = usePriceGap();
  const [price, setPrice] = useState(gap.unit_price === undefined ? "" : String(gap.unit_price));
  const buy = isMissingBuy(gap);
  const pending = gap.realized_pending === true;

  return (
    <div className="flex flex-wrap items-center justify-between gap-3 py-2.5 text-[12.5px]">
      <span className="font-mono font-medium">{gap.ticker}</span>
      <span className="font-mono text-[11.5px] text-ink-faint">{formatIsoDate(gap.date)}</span>
      <span className="text-ink-dim">{formatNumber(gap.quantity)} un</span>

      {buy ? (
        <span className="flex items-center gap-2">
          <span data-testid={`gap-approximate-${gap.ticker}`}>
            <Chip tone="amber">aproximado</Chip>
          </span>
          <span className="font-mono">{formatCurrency(gap.unit_price ?? 0)}</span>
        </span>
      ) : (
        <span className="flex items-center gap-2">
          {pending && (
            <span data-testid={`gap-pending-${gap.id}`}>
              <Chip tone="rose">proventos pendentes</Chip>
            </span>
          )}
          <input
            aria-label={`Proventos de ${gap.ticker}`}
            data-testid={`gap-price-${gap.id}`}
            type="number"
            min="0"
            step="0.01"
            value={price}
            onChange={(event) => setPrice(event.target.value)}
            className="w-24 rounded-sm border border-line bg-surface-2 px-2 py-1 text-right font-mono text-[13px]"
          />
          <Button
            data-testid={`gap-price-save-${gap.id}`}
            variant="primary"
            onClick={() => priceGap.mutate({ id: gap.id, unitPrice: Number(price) })}
            disabled={price.trim().length === 0 || !(Number(price) >= 0) || priceGap.isPending}
          >
            Salvar
          </Button>
        </span>
      )}
    </div>
  );
}

function CustodyStep() {
  const reconcile = useReconcileCustody();
  const [file, setFile] = useState<File | null>(null);

  return (
    <Panel title="Conferir custódia" action={<Chip>somente leitura</Chip>}>
      <p className="mb-3 text-[12.5px] text-ink-dim">
        Envie uma Posição da B3 para comparar a custódia com o livro reprojetado. Nada é gravado:
        uma divergência é apenas evidência para você revisar.
      </p>

      <div className="flex flex-wrap items-end gap-3">
        <FileField label="Posição" onFile={setFile} />
        <Button
          variant="primary"
          onClick={() => {
            if (file) reconcile.mutate(file);
          }}
          disabled={file === null || reconcile.isPending}
        >
          {reconcile.isPending ? "Conferindo…" : "Conferir custódia"}
        </Button>
      </div>

      {reconcile.error && <p className="mt-2 text-[12.5px] text-tax">{reconcile.error.message}</p>}

      {reconcile.data && <CustodyDriftTable rows={reconcile.data} />}
    </Panel>
  );
}

function CustodyDriftTable({ rows }: { rows: PositionReconciliation[] }) {
  return (
    <div data-testid="custody-drift" className="mt-3 overflow-x-auto rounded-lg border border-line">
      <table className="w-full border-collapse text-[13px]">
        <thead>
          <tr className="bg-surface-2 font-mono text-[10px] tracking-[.09em] text-ink-faint uppercase">
            <th className="px-3 py-2 text-left">Ativo</th>
            <th className="px-3 py-2 text-right">Custódia</th>
            <th className="px-3 py-2 text-right">Livro</th>
            <th className="px-3 py-2 text-left">Divergência</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.ticker} className="border-t border-line-soft">
              <td className="px-3 py-1.5 font-mono">{row.ticker}</td>
              <td className="px-3 py-1.5 text-right font-mono">
                {formatNumber(row.custody_quantity)}
              </td>
              <td className="px-3 py-1.5 text-right font-mono">
                {formatNumber(row.ledger_quantity)}
              </td>
              <td className="px-3 py-1.5">
                {row.has_drift ? <Chip tone="rose">divergente</Chip> : <Chip tone="green">ok</Chip>}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
