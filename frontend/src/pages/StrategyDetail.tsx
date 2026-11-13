import type { ReactNode } from "react";
import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import {
  useActivateEdition,
  useClosedMonths,
  useCurrentAllocation,
  useEditions,
  useMonthPerformance,
  useStrategies,
} from "../api/queries.ts";
import type { Edition, Strategy } from "../api/types.ts";
import { AttributionTransferModal } from "../components/AttributionTransferModal.tsx";
import { Button } from "../components/Button.tsx";
import { Chip } from "../components/Chip.tsx";
import { EditionDiffPanel } from "../components/EditionDiffPanel.tsx";
import { Kpi } from "../components/Kpi.tsx";
import { Num } from "../components/Num.tsx";
import { PageHead } from "../components/PageHead.tsx";
import { PageTabs } from "../components/PageTabs.tsx";
import { Panel } from "../components/Panel.tsx";
import { type Column, Table } from "../components/Table.tsx";
import { formatPoints, formatTarget } from "../lib/allocation.ts";
import { cn } from "../lib/cn.ts";
import { formatCurrency, formatIsoDate, formatNumber, formatPercent } from "../lib/format.ts";
import { assetClassLabel } from "../lib/month.ts";
import { ROUTES, strategyDetail, strategyDetailTab } from "../lib/routes.ts";
import {
  type EditionEntryChange,
  type EditionReviewRow,
  type EditionTimelineRow,
  type StrategyCompositionRow,
  type StrategyFatiaView,
  type StrategyPositionRow,
  activeEdition,
  editionDiffSummary,
  editionReviewRows,
  editionTimeline,
  isReportDriven,
  ratingLabel,
  referenceMonthLabel,
  stagedEdition,
  strategyComposition,
  strategyFatiaView,
  strategyKpis,
  strategyPositionRows,
} from "../lib/strategy.ts";
import { type Tone, driftTone, toneClass } from "../lib/tones.ts";

// Estratégias · detail (issues #374, #375): the shell the row opens into — a KPI row and its own tab
// strip over Carteira, Edições and Fatia. Edições owns the edition timeline, the staged diff and the
// promote/discard decision; the later slices add Atribuição and the Manual variant.
export function StrategyDetail() {
  const { id, tab } = useParams();
  const strategyId = Number(id);
  const strategies = useStrategies();
  const strategy = strategies.data?.find(
    (candidate) => Number.isInteger(strategyId) && candidate.id === strategyId,
  );
  const allocation = useCurrentAllocation();
  // Nothing to read until the catalog resolves the id: an unknown id must render the not-found
  // state without firing an editions request that can only 404.
  const editions = useEditions(strategy?.id ?? null);
  const closed = useClosedMonths();
  const lastClosed = closed.data?.[0];
  const performance = useMonthPerformance(lastClosed?.id ?? null);

  if (strategies.isLoading) {
    return (
      <DetailFrame>
        <p className="text-[13.5px] text-muted">Carregando…</p>
      </DetailFrame>
    );
  }

  if (strategies.isError) {
    return (
      <DetailFrame>
        <p className="text-[13.5px] text-return-down">{strategies.error.message}</p>
      </DetailFrame>
    );
  }

  if (strategy === undefined) {
    return (
      <>
        <PageHead title="Estratégia" subtitle="Não encontrada" actions={<BackToStrategies />} />
        <Panel title="Estratégia não encontrada">
          <p className="text-[12.5px] text-muted">
            A estratégia <span className="font-mono text-text">{id}</span> não existe no catálogo.
          </p>
          <Link
            to={ROUTES.strategies}
            className="mt-3 inline-block text-[12.5px] text-action hover:underline"
          >
            Voltar para Estratégias
          </Link>
        </Panel>
      </>
    );
  }

  const positions = strategyPositionRows(strategy, allocation.data);
  const kpis = strategyKpis(strategy, allocation.data, performance.data);
  const fatia = strategyFatiaView(strategy, allocation.data, performance.data);
  const staged = stagedEdition(editions.data ?? []);
  const report = isReportDriven(strategy);

  return (
    <>
      <PageHead
        title={strategy.name}
        subtitle={`${report ? "estratégia de relatório" : "bucket manual"} · ${assetClassLabel(strategy.asset_class)}`}
        actions={<BackToStrategies />}
      />

      <div className="mb-3 grid grid-cols-2 gap-2.5 md:grid-cols-3 xl:grid-cols-5">
        {kpis.map((kpi) => (
          <Kpi
            key={kpi.key}
            testId={`strategy-kpi-${kpi.key}`}
            label={kpi.label}
            value={kpi.value}
            hint={kpi.hint}
            tone={kpi.tone}
            size="lg"
          />
        ))}
      </div>

      <PageTabs
        tabs={[
          { to: strategyDetail(strategy.id), label: "Carteira", end: true },
          ...(report ? [{ to: strategyDetailTab(strategy.id, "edicoes"), label: "Edições" }] : []),
          { to: strategyDetailTab(strategy.id, "fatia"), label: "Fatia no portfólio" },
        ]}
        trailing={report ? "relatório" : "sem alvo"}
      />

      {tab === "edicoes" && report ? (
        <EdicoesTab
          editions={editions.data ?? []}
          loading={editions.isLoading}
          error={editions.error?.message}
        />
      ) : tab === "fatia" ? (
        <FatiaTab strategy={strategy} view={fatia} positions={positions} />
      ) : (
        <CarteiraTab strategy={strategy} positions={positions} staged={staged} />
      )}
    </>
  );
}

// The loading and error states still carry a page head, so the shell never renders a bare fragment.
function DetailFrame({ children }: { children: ReactNode }) {
  return (
    <>
      <PageHead
        title="Estratégia"
        subtitle="Detalhe da estratégia"
        actions={<BackToStrategies />}
      />
      {children}
    </>
  );
}

function BackToStrategies() {
  return (
    <Link to={ROUTES.strategies}>
      <Button variant="ghost">← Todas as estratégias</Button>
    </Link>
  );
}

function CarteiraTab({
  strategy,
  positions,
  staged,
}: {
  strategy: Strategy;
  positions: StrategyPositionRow[];
  staged?: Edition;
}) {
  // A manual bucket has no target: every target column reads "sem alvo" and the unit slices are the
  // only thing a donation can move. The modal mounts on demand, so its reads start on open.
  const manual = !isReportDriven(strategy);
  const [donating, setDonating] = useState<string | null>(null);

  const columns: Column<StrategyPositionRow>[] = [
    {
      key: "ticker",
      header: "Ativo",
      render: (row) => (
        <span className="flex items-center gap-1.5">
          <span className="font-mono font-semibold text-text">{row.ticker}</span>
          {row.stale === true && row.quoteDate !== undefined && (
            <Chip tone="amber">cotação {formatIsoDate(row.quoteDate)}</Chip>
          )}
          {manual && (
            <button
              type="button"
              aria-label={`Transferir ${row.ticker}`}
              onClick={() => setDonating(row.ticker)}
              className="font-mono text-[10px] text-action hover:underline"
            >
              Transferir
            </button>
          )}
        </span>
      ),
    },
    {
      key: "quantity",
      header: "Qtd",
      align: "right",
      render: (row) =>
        row.quantity === undefined ? (
          <Unknown />
        ) : (
          <Num
            value={row.quantity}
            format={formatNumber}
            testId={`strategy-ticker-${row.ticker}-quantity`}
          />
        ),
    },
    {
      key: "price",
      header: "Preço",
      align: "right",
      render: (row) =>
        row.price === undefined ? (
          <Unknown />
        ) : (
          <Num
            value={row.price}
            format={formatCurrency}
            testId={`strategy-ticker-${row.ticker}-price`}
          />
        ),
    },
    {
      key: "value",
      header: "Valor",
      align: "right",
      render: (row) =>
        row.value === undefined ? (
          <Unknown />
        ) : (
          <Num
            value={row.value}
            format={formatCurrency}
            testId={`strategy-ticker-${row.ticker}-value`}
          />
        ),
    },
    {
      key: "target",
      header: "Alvo",
      align: "right",
      render: (row) =>
        row.target === undefined ? (
          manual ? (
            <span data-testid={`strategy-ticker-${row.ticker}-target`} className="text-muted">
              sem alvo
            </span>
          ) : (
            <Unknown testId={`strategy-ticker-${row.ticker}-target`} />
          )
        ) : (
          <Num
            value={row.target}
            format={formatTarget}
            testId={`strategy-ticker-${row.ticker}-target`}
          />
        ),
    },
    {
      key: "ideal",
      header: "Ideal",
      align: "right",
      render: (row) =>
        row.ideal === undefined ? (
          <Unknown testId={`strategy-ticker-${row.ticker}-ideal`} />
        ) : (
          <Num
            value={row.ideal}
            format={formatCurrency}
            testId={`strategy-ticker-${row.ticker}-ideal`}
          />
        ),
    },
    {
      key: "drift",
      header: "Drift",
      align: "right",
      render: (row) =>
        row.drift === undefined ? (
          <Unknown testId={`strategy-ticker-${row.ticker}-drift`} />
        ) : (
          <span
            data-testid={`strategy-ticker-${row.ticker}-drift`}
            className={cn("font-mono", toneClass(driftTone(row.drift)))}
          >
            {formatPoints(row.drift)}
          </span>
        ),
    },
  ];

  return (
    <div className="space-y-3.5">
      {staged !== undefined && <StagedEditionStrip edition={staged} />}

      <Panel
        title="Carteira da estratégia"
        action={<span className="font-mono text-label text-faint">{positions.length} ativos</span>}
      >
        {positions.length === 0 ? (
          <p className="text-[12.5px] text-muted">
            A cadeia do mês não carrega ativos para esta estratégia.
          </p>
        ) : (
          <Table columns={columns} rows={positions} rowKey={(row) => row.ticker} />
        )}
      </Panel>

      {donating !== null && (
        <AttributionTransferModal
          ticker={donating}
          source={{ id: strategy.id, assetClass: strategy.asset_class }}
          onClose={() => setDonating(null)}
        />
      )}
    </div>
  );
}

// The one-line review strip: a staged report waits on the detail, and the strip says so without
// repeating the edition card the Edições tab will own.
function StagedEditionStrip({ edition }: { edition: Edition }) {
  return (
    <div
      data-testid="staged-edition"
      className="flex flex-wrap items-center gap-2 rounded-panel border border-attention-bg bg-panel px-3 py-2 text-[12.5px] text-muted"
    >
      <Chip tone="amber">revisão</Chip>
      <span className="text-text">Edição em revisão</span>
      <span className="text-faint">·</span>
      <b className="font-mono text-text">{referenceMonthLabel(edition.reference_month)}</b>
      <span className="text-faint">Promover substitui a edição ativa.</span>
    </div>
  );
}

// The Edições tab (issue #375): the edition timeline with the current edition marked, the staged
// report's diff summary and full weight/rating/target-price table, and the promote/discard decision.
// "Alterada" is reserved for a row whose weight, rating or target price actually moved against the
// active edition — a target price the fixture merely restates never earns the label.
type EditionDecision = "promoted" | "discarded";

function EdicoesTab({
  editions,
  loading,
  error,
}: {
  editions: Edition[];
  loading: boolean;
  error?: string;
}) {
  const activate = useActivateEdition();
  const [decision, setDecision] = useState<EditionDecision>();
  const timeline = editionTimeline(editions);
  const staged = stagedEdition(editions);
  const active = activeEdition(editions);

  function promote(edition: Edition) {
    activate.mutate(
      {
        strategyId: edition.strategy_id,
        activation: { reference_month: edition.reference_month, entries: edition.entries },
      },
      { onSuccess: () => setDecision("promoted") },
    );
  }

  const columns: Column<EditionTimelineRow>[] = [
    {
      key: "month",
      header: "Mês",
      render: (row) => (
        <span className="font-mono text-text">
          {referenceMonthLabel(row.edition.reference_month)}
        </span>
      ),
    },
    {
      key: "status",
      header: "Situação",
      render: (row) => <Chip tone={row.tone}>{row.statusLabel}</Chip>,
    },
    {
      key: "entries",
      header: "Ativos",
      align: "right",
      render: (row) => `${row.edition.entries.length} ativos`,
    },
  ];

  return (
    <div className="space-y-3.5">
      <Panel title="Linha do tempo">
        {loading ? (
          <p className="text-[12.5px] text-muted">Carregando…</p>
        ) : error !== undefined ? (
          <p className="text-[12.5px] text-return-down">{error}</p>
        ) : timeline.length === 0 ? (
          <p className="text-[12.5px] text-muted">
            Nenhuma edição importada.{" "}
            <Link to={ROUTES.strategiesImport} className="text-action hover:underline">
              Importar relatório
            </Link>
          </p>
        ) : (
          <div data-testid="edition-timeline">
            <Table
              columns={columns}
              rows={timeline}
              rowKey={(row) => row.edition.id}
              rowClassName={(row) => (row.active ? "bg-panel-2" : undefined)}
            />
          </div>
        )}
      </Panel>

      {decision !== undefined && <EditionDecisionNote decision={decision} />}

      {staged !== undefined && (
        <StagedEditionReview
          key={staged.id}
          edition={staged}
          active={active}
          promoting={activate.isPending}
          error={activate.error?.message}
          onPromote={() => promote(staged)}
          // The API has no discard: discarding is the non-destructive "not now" — the staged
          // edition stays staged and the active edition untouched, and the note states that.
          onDiscard={() => setDecision("discarded")}
        />
      )}
    </div>
  );
}

function EditionDecisionNote({ decision }: { decision: EditionDecision }) {
  return (
    <div
      data-testid="edition-decision-note"
      className="rounded-panel border border-line-soft bg-panel-2 px-3 py-2 text-[12.5px] text-muted"
    >
      {decision === "promoted"
        ? "Edição promovida · o plano usa os novos pesos no próximo recálculo."
        : "Edição não promovida · a edição ativa segue valendo até o próximo recálculo."}
    </div>
  );
}

function StagedEditionReview({
  edition,
  active,
  promoting,
  error,
  onPromote,
  onDiscard,
}: {
  edition: Edition;
  active?: Edition;
  promoting: boolean;
  error?: string;
  onPromote: () => void;
  onDiscard: () => void;
}) {
  const rows = editionReviewRows(edition, active);

  const columns: Column<EditionReviewRow>[] = [
    {
      key: "ticker",
      header: "Ativo",
      render: (row) => <span className="font-mono font-semibold text-text">{row.ticker}</span>,
    },
    {
      key: "weight",
      header: "Peso",
      align: "right",
      render: (row) => <span className="font-mono">{formatTarget(row.weight)}</span>,
    },
    {
      key: "rating",
      header: "Rating",
      render: (row) => ratingLabel(row.rating),
    },
    {
      key: "target",
      header: "Preço-alvo",
      align: "right",
      render: (row) =>
        row.targetPrice === undefined ? (
          <Unknown />
        ) : (
          <span className="font-mono">{formatCurrency(row.targetPrice)}</span>
        ),
    },
    {
      key: "change",
      header: "Situação",
      render: (row) => <EntryChange change={row.change} />,
    },
  ];

  return (
    <div data-testid="staged-edition-review" className="space-y-3.5">
      {edition.diff_from_previous !== undefined && (
        <EditionDiffPanel diff={edition.diff_from_previous} />
      )}

      <Panel
        title={`Edição em revisão · ${referenceMonthLabel(edition.reference_month)}`}
        action={
          <span className="flex items-center gap-1.5">
            <span className="font-mono text-label text-faint">relatório</span>
            <Chip tone="neutral">{editionDiffSummary(edition.diff_from_previous)}</Chip>
          </span>
        }
      >
        <p className="mb-2 text-[12.5px] text-muted">
          Comparação com a edição ativa{" "}
          <span className="font-mono text-text">
            {active === undefined ? "—" : referenceMonthLabel(active.reference_month)}
          </span>
          : uma linha só aparece como "alterada" quando peso, rating ou preço-alvo mudam. O resumo
          acima é o que o relatório declarou.
        </p>
        <div data-testid="edition-entries">
          <Table columns={columns} rows={rows} rowKey={(row) => row.ticker} />
        </div>
      </Panel>

      <Panel title="Decisão">
        <p className="text-[12.5px] text-muted">
          Promover substitui a edição ativa. Descartar deixa a edição ativa como está.
        </p>
        <div className="mt-2.5 flex flex-wrap items-center gap-2">
          <Button variant="primary" onClick={onPromote} disabled={promoting}>
            {promoting ? "Promovendo…" : "Promover edição"}
          </Button>
          <Button variant="ghost" onClick={onDiscard} disabled={promoting}>
            Descartar
          </Button>
        </div>
        {error !== undefined && <p className="mt-2 text-[12.5px] text-return-down">{error}</p>}
      </Panel>
    </div>
  );
}

function EntryChange({ change }: { change: EditionEntryChange }) {
  if (change === "new") return <Chip tone="green">nova</Chip>;
  if (change === "changed") return <Chip tone="amber">alterada</Chip>;
  return <Unknown />;
}

function FatiaTab({
  strategy,
  view,
  positions,
}: {
  strategy: Strategy;
  view: StrategyFatiaView;
  positions: StrategyPositionRow[];
}) {
  const composition = strategyComposition(positions);

  const columns: Column<StrategyCompositionRow>[] = [
    {
      key: "ticker",
      header: "Ativo",
      render: (row) => <span className="font-mono text-text">{row.ticker}</span>,
    },
    {
      key: "value",
      header: "Valor",
      align: "right",
      render: (row) =>
        row.value === undefined ? <Unknown /> : <Num value={row.value} format={formatCurrency} />,
    },
    {
      key: "weight",
      header: "Peso na estratégia",
      align: "right",
      render: (row) =>
        row.weight === undefined ? (
          <Unknown />
        ) : (
          <span className="font-mono">{formatPercent(row.weight, 1)}</span>
        ),
    },
  ];

  return (
    <div className="grid items-start gap-3.5 lg:grid-cols-[minmax(0,1fr)_330px]">
      <Panel title="Composição da fatia">
        {composition.rows.length === 0 ? (
          <p className="text-[12.5px] text-muted">
            A cadeia do mês não carrega ativos para esta estratégia.
          </p>
        ) : (
          <Table
            columns={columns}
            rows={composition.rows}
            rowKey={(row) => row.ticker}
            footer={
              <tr className="bg-panel-2 font-semibold text-text">
                <td className="h-[var(--height-row)] px-2.5 text-left">Total</td>
                <td className="h-[var(--height-row)] px-2.5 text-right font-mono">
                  {formatCurrency(composition.total)}
                </td>
                <td className="h-[var(--height-row)] px-2.5 text-right font-mono">
                  {composition.complete ? "100,0%" : "—"}
                </td>
              </tr>
            }
          />
        )}
      </Panel>

      <aside className="flex min-w-0 flex-col gap-3.5">
        <Panel title="Contexto">
          <Kv
            label="Fatia na carteira"
            value={view.portfolioShare}
            testId="strategy-share-portfolio"
          />
          <Kv label="Fatia na classe" value={view.classShare} testId="strategy-share-class" />
          <Kv label="Alvo na classe" value={view.target} testId="strategy-target" />
          <Kv label="Drift" value={view.drift} tone={view.driftTone} testId="strategy-drift" />
        </Panel>

        <Panel
          title={
            view.period === undefined
              ? "Retorno do mês"
              : `Retorno ${referenceMonthLabel(view.period)}`
          }
        >
          <div data-testid="strategy-decomposition">
            {view.decomposition === undefined ? (
              <p className="text-[12.5px] text-muted">
                Sem fechamento para <span className="font-mono">{strategy.name}</span>.
              </p>
            ) : (
              <>
                <Kv
                  label="Início"
                  value={view.decomposition.valueStart}
                  testId="strategy-return-start"
                />
                <Kv label="Fim" value={view.decomposition.valueEnd} testId="strategy-return-end" />
                <Kv
                  label="Aportes"
                  value={view.decomposition.aportes}
                  testId="strategy-return-aportes"
                />
                <Kv
                  label="Proventos"
                  value={view.decomposition.income}
                  testId="strategy-return-income"
                />
                <Kv
                  label="Retorno"
                  value={view.decomposition.return}
                  tone={view.decomposition.returnTone}
                  testId="strategy-return-percentage"
                />
              </>
            )}
          </div>
        </Panel>
      </aside>
    </div>
  );
}

function Kv({
  label,
  value,
  tone,
  testId,
}: {
  label: string;
  value: string;
  tone?: Tone;
  testId?: string;
}) {
  return (
    <div className="flex items-baseline gap-2 border-t border-line-soft py-1.5 first:border-t-0 first:pt-0">
      <span className="text-[11.5px] text-muted">{label}</span>
      <span className="flex-1" />
      <span
        data-testid={testId}
        className={cn(
          "font-mono text-num tabular-nums",
          tone === undefined ? "text-text" : toneClass(tone),
        )}
      >
        {value}
      </span>
    </div>
  );
}

// An unknown number — a slice the chain does not price, or a bucket with no target. Always an em
// dash, never R$ 0,00.
function Unknown({ testId }: { testId?: string }) {
  return (
    <span data-testid={testId} className="text-muted">
      —
    </span>
  );
}
