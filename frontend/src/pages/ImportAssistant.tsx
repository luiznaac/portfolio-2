import { type FormEvent, useReducer, useState } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import {
  useActivateEdition,
  useApplyImport,
  useEditions,
  useImport,
  useImportEdition,
  useImportRows,
  useReviewImport,
  useStageImport,
  useStrategies,
} from "../api/queries.ts";
import type { Edition, EditionImport, Import, ImportRow, Strategy } from "../api/types.ts";
import { Badge } from "../components/Badge.tsx";
import { Button } from "../components/Button.tsx";
import { Chip } from "../components/Chip.tsx";
import { ImportOutcomeSummary } from "../components/ImportOutcomeSummary.tsx";
import { ImportProvenance, shortHash } from "../components/ImportProvenance.tsx";
import { Panel } from "../components/Panel.tsx";
import { SituationChip } from "../components/SituationChip.tsx";
import { type Column, Table } from "../components/Table.tsx";
import { WizardStepper } from "../components/WizardStepper.tsx";
import { formatTarget } from "../lib/allocation.ts";
import { formatCurrency, formatNumber, formatTradeSide, plural } from "../lib/format.ts";
import {
  DESTINATIONS,
  type DestinationMeta,
  destinationRoute,
  resolveDestination,
  sessionPath,
  sessionStep,
} from "../lib/importDestinations.ts";
import { rejectedBatch, summarizeOutcomes, summarizeSituations } from "../lib/importSituations.ts";
import { ROUTES, strategyDetailTab } from "../lib/routes.ts";
import {
  type EditionDecision,
  type EditionDeltaRow,
  type EditionEntryChange,
  activeEdition,
  activeEditionBefore,
  editionDeltaRows,
  editionDeltaSummary,
  editionExitedRows,
  ratingLabel,
  referenceMonthLabel,
  reportDrivenStrategies,
  stagedEdition,
} from "../lib/strategy.ts";

const STEPS = ["Arquivo", "Destino e mapeamento", "Linhas", "Aplicar"] as const;
const EDITION_STEPS = ["Arquivo", "Destino e mapeamento", "Diff da edição", "Promover"] as const;
const NEXT_RECOMPUTE_NOTE =
  "O plano atual já está calculado; os novos pesos entram no próximo recálculo, não retroativamente.";

type EditionSession = {
  staged: EditionImport | null;
  decisionEditionId: number | null;
  decision?: EditionDecision;
};

type EditionSessionAction =
  | { type: "staged"; imported: EditionImport }
  | { type: "promote"; editionId: number }
  | { type: "decided"; decision: EditionDecision }
  | { type: "reset" };

const EMPTY_EDITION_SESSION: EditionSession = { staged: null, decisionEditionId: null };

function editionSessionReducer(
  session: EditionSession,
  action: EditionSessionAction,
): EditionSession {
  switch (action.type) {
    case "staged":
      return { staged: action.imported, decisionEditionId: null };
    case "promote":
      return { ...session, decisionEditionId: action.editionId };
    case "decided":
      return { ...session, decision: action.decision };
    case "reset":
      return EMPTY_EDITION_SESSION;
  }
}

const ROW_COLUMNS: Column<ImportRow>[] = [
  { key: "date", header: "Data", render: (row) => row.date ?? "—" },
  {
    key: "side",
    header: "Lado",
    render: (row) => (
      <Badge tone={row.side === "BUY" ? "ok" : "warn"}>{formatTradeSide(row.side)}</Badge>
    ),
  },
  {
    key: "ticker",
    header: "Ticker",
    render: (row) => <span className="font-mono font-medium">{row.ticker}</span>,
  },
  { key: "quantity", header: "Qtd", align: "right", render: (row) => formatNumber(row.quantity) },
  {
    key: "unit_price",
    header: "Preço",
    align: "right",
    render: (row) => formatNumber(row.unit_price),
  },
  { key: "institution", header: "Instituição", render: (row) => row.institution_name },
  {
    key: "situation",
    header: "Situação",
    render: (row) => <SituationChip situation={row.situation} />,
  },
  {
    key: "error",
    header: "Observação",
    render: (row) => <span className="text-tax">{row.error ?? ""}</span>,
  },
];

const FIELD = "rounded-sm border border-line bg-surface-2 px-2 py-1.5 text-[13px] text-ink";
const CALLOUT =
  "rounded-sm border-l-[3px] border-accent bg-surface-2 px-3 py-2 text-[12.5px] text-ink";
const DANGER = "rounded-sm border-l-[3px] border-tax bg-surface-2 px-3 py-2 text-[12.5px] text-ink";

// The one review surface: a full-screen four-step assistant opened by every upload. Step 1 reads
// the file and stages it — nothing is applied there. The staged record is the session: leaving at
// any point parks it, and the record's destination decides where an apply lands.
export function ImportAssistant() {
  const { importId } = useParams();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const id = importId === undefined ? null : Number(importId);
  const sessionId = id !== null && Number.isFinite(id) && id > 0 ? id : null;

  const contextDestination = resolveDestination(searchParams.get("object"));
  const isEdition = contextDestination.object === "STRATEGY_EDITION";
  const contextStrategyId = Number(searchParams.get("strategy"));
  const [strategyChoice, setStrategyChoice] = useState<number | null>(null);
  const strategyId =
    Number.isInteger(contextStrategyId) && contextStrategyId > 0
      ? contextStrategyId
      : strategyChoice;

  const record = useImport(sessionId);
  const rows = useImportRows(sessionId);
  const stageImport = useStageImport();
  const reviewImport = useReviewImport();
  const applyImport = useApplyImport();
  const strategies = useStrategies(isEdition);
  const editions = useEditions(isEdition ? strategyId : null);
  const importEdition = useImportEdition();
  const activateEdition = useActivateEdition();

  const [file, setFile] = useState<File | null>(null);
  const [stepOverride, setStepOverride] = useState<number | null>(null);
  const [editionSession, dispatchEdition] = useReducer(
    editionSessionReducer,
    EMPTY_EDITION_SESSION,
  );

  const destination =
    record.data === undefined
      ? contextDestination
      : DESTINATIONS[record.data.destination.object_type];
  const strategy = strategies.data?.find((candidate) => candidate.id === strategyId);
  // Promotion flips the staged edition to ACTIVE in the editions read, so the session keeps the
  // promoted edition's id and derives it from the read instead of holding a stale copy.
  const decisionEdition = editions.data?.find(
    (edition) => edition.id === editionSession.decisionEditionId,
  );
  // The staged edition is the session: a report staged in this assistant, or the one a previous
  // visit parked on the strategy. Re-entering resumes the diff instead of asking for the file again.
  const staged =
    editionSession.staged?.edition ?? decisionEdition ?? stagedEdition(editions.data ?? []);
  // The backend stages a report against the newest active edition before its month; the assistant
  // reads the same baseline so an older report never shows a diff promotion would not produce.
  const active =
    staged === undefined
      ? activeEdition(editions.data ?? [])
      : activeEditionBefore(editions.data ?? [], staged.reference_month);
  const editionDecision = editionSession.decision;
  const step =
    stepOverride ??
    (isEdition
      ? staged === undefined
        ? 1
        : 3
      : sessionStep(sessionId !== null, record.data?.status));

  const rowItems = rows.data ?? [];
  const situations = summarizeSituations(rowItems);
  const outcomes = summarizeOutcomes(rowItems);
  const rejected = rejectedBatch(rowItems);
  const validRows = situations.new + situations.duplicate_in_file;

  const mutationError =
    stageImport.error ??
    reviewImport.error ??
    applyImport.error ??
    importEdition.error ??
    activateEdition.error;

  function handleStage(event: FormEvent) {
    event.preventDefault();
    if (!file) return;
    if (isEdition) {
      if (strategy === undefined) return;
      importEdition.mutate(
        { strategyId: strategy.id, file },
        {
          onSuccess: (imported) => {
            dispatchEdition({ type: "staged", imported });
            setStepOverride(2);
          },
        },
      );
      return;
    }
    if (!destination.supported) return;
    stageImport.mutate(
      { source: destination.source, file, destination: destination.object },
      {
        onSuccess: ({ import: stagedRecord, created }) => {
          // A fresh upload walks the full flow; a hash match resumes wherever the record already is.
          setStepOverride(created ? 2 : sessionStep(true, stagedRecord.status));
          navigate(sessionPath(stagedRecord.id), { replace: true });
        },
      },
    );
  }

  function handleContinue() {
    if (isEdition) {
      setStepOverride(Math.min(4, step + 1));
      return;
    }
    if (sessionId === null || record.data === undefined) return;
    if (record.data.status === "REVIEWED") {
      setStepOverride(4);
      return;
    }
    reviewImport.mutate(sessionId, {
      onSuccess: () => setStepOverride(4),
    });
  }

  function handleApply() {
    if (isEdition || sessionId === null) return;
    applyImport.mutate(sessionId, {
      onSuccess: (result) =>
        navigate(destinationRoute(result.record.destination.object_type), {
          state: { importApplied: result },
        }),
    });
  }

  function handlePromote() {
    if (strategy === undefined || staged === undefined) return;
    const promoting = staged;
    dispatchEdition({ type: "promote", editionId: promoting.id });
    activateEdition.mutate(
      {
        strategyId: strategy.id,
        activation: { reference_month: promoting.reference_month, entries: promoting.entries },
      },
      { onSuccess: () => dispatchEdition({ type: "decided", decision: "promoted" }) },
    );
  }

  function handleExit() {
    if (isEdition && strategy !== undefined) {
      navigate(strategyDetailTab(strategy.id, "edicoes"));
      return;
    }
    navigate(ROUTES.imports);
  }

  function handleAnotherFile() {
    dispatchEdition({ type: "reset" });
    setStepOverride(1);
  }

  return (
    <div
      data-testid="import-assistant"
      className="fixed inset-0 z-50 flex flex-col overflow-hidden bg-bg"
    >
      <header className="flex h-11 flex-none items-center gap-3 border-b border-line px-5">
        <span className="text-[13px] font-semibold text-ink">Importar arquivo</span>
        <span className="text-[12.5px] text-ink-dim">{destination.label}</span>
        {isEdition && strategy !== undefined && (
          <span className="text-[12.5px] text-ink-faint">· {strategy.name}</span>
        )}
        <span className="flex-1" />
        {record.data !== undefined && (
          <ImportProvenance record={record.data} className="text-ink-faint" />
        )}
        <Button variant="ghost" onClick={handleExit}>
          Sair
        </Button>
      </header>

      <div className="flex-1 overflow-y-auto px-5 py-4">
        <div className="mx-auto max-w-[1040px]">
          <WizardStepper steps={isEdition ? EDITION_STEPS : STEPS} current={step - 1} />
          {mutationError && <p className={`mb-4 ${DANGER}`}>{mutationError.message}</p>}

          {step === 1 &&
            (isEdition ? (
              <StepEditionFile
                destination={destination}
                strategies={reportDrivenStrategies(strategies.data ?? [])}
                strategy={strategy}
                onStrategy={setStrategyChoice}
                file={file}
                onFile={setFile}
                onStage={handleStage}
                pending={importEdition.isPending}
              />
            ) : (
              <StepFile
                destination={destination}
                file={file}
                onFile={setFile}
                onStage={handleStage}
                pending={stageImport.isPending}
              />
            ))}

          {step === 2 &&
            (isEdition
              ? strategy !== undefined &&
                staged !== undefined && (
                  <StepEditionDestination
                    destination={destination}
                    strategy={strategy}
                    staged={staged}
                    parser={editionSession.staged?.parser}
                    fileName={file?.name}
                  />
                )
              : record.data !== undefined && (
                  <StepDestination destination={destination} record={record.data} />
                ))}

          {step === 3 &&
            (isEdition
              ? staged !== undefined && (
                  <StepEditionDiff active={active} staged={staged} loading={editions.isLoading} />
                )
              : record.data !== undefined && (
                  <StepRows
                    rows={rowItems}
                    situations={situations}
                    rejected={rejected.length}
                    loading={rows.isLoading}
                  />
                ))}

          {step === 4 &&
            (isEdition
              ? strategy !== undefined &&
                staged !== undefined && (
                  <StepEditionPromote
                    destination={destination}
                    strategy={strategy}
                    staged={staged}
                    active={active}
                    decision={editionDecision}
                    pending={activateEdition.isPending}
                    error={activateEdition.error?.message}
                    onPromote={handlePromote}
                    onDiscard={() => dispatchEdition({ type: "decided", decision: "discarded" })}
                    onAnotherFile={handleAnotherFile}
                  />
                )
              : record.data !== undefined && (
                  <StepApply
                    destination={destination}
                    record={record.data}
                    situations={situations}
                    outcomes={outcomes}
                    validRows={validRows}
                    rejectedRows={rejected}
                    pending={applyImport.isPending}
                    onApply={handleApply}
                  />
                ))}
        </div>
      </div>

      <footer className="flex flex-none items-center gap-3 border-t border-line px-5 py-3">
        {step > 1 && (
          <Button variant="ghost" onClick={() => setStepOverride(step - 1)}>
            ← Voltar
          </Button>
        )}
        <span className="flex-1" />
        <span className="text-[11.5px] text-ink-faint">
          {isEdition
            ? step < 4
              ? "Sair a qualquer momento guarda a edição em revisão na estratégia."
              : "Promover é a decisão; descartar deixa a edição ativa como está."
            : step < 4
              ? "Sair a qualquer momento guarda a sessão na Caixa de entrada."
              : "Aplicar é a decisão; o registro guarda o hash e o original."}
        </span>
        {step === 2 && (isEdition || record.data !== undefined) && (
          <Button variant="primary" onClick={() => setStepOverride(3)}>
            Continuar →
          </Button>
        )}
        {step === 3 && (
          <Button
            variant="primary"
            onClick={handleContinue}
            disabled={!isEdition && (reviewImport.isPending || record.data?.status === "APPLIED")}
          >
            {!isEdition && reviewImport.isPending ? "Revisando…" : "Continuar →"}
          </Button>
        )}
      </footer>
    </div>
  );
}

function ReportFileInput({
  accept,
  onFile,
}: {
  accept: string;
  onFile: (file: File | null) => void;
}) {
  return (
    <input
      type="file"
      accept={accept}
      aria-label="Arquivo"
      onChange={(event) => onFile(event.target.files?.[0] ?? null)}
      className="block w-full text-[13px] text-ink-dim file:mr-3 file:rounded-sm file:border-0 file:bg-surface-2 file:px-3 file:py-1.5 file:text-ink"
    />
  );
}

function MappingGrid({ mapping }: { mapping: readonly { from: string; to: string }[] }) {
  return (
    <div className="grid grid-cols-2 gap-x-6 gap-y-1 text-[12.5px]">
      {mapping.map((cell) => (
        <div key={cell.from} className="flex items-center gap-2">
          <span className="text-ink-dim">{cell.from}</span>
          <span className="text-ink-faint">→</span>
          <span className="text-ink">{cell.to}</span>
        </div>
      ))}
    </div>
  );
}

function StepEditionFile({
  destination,
  strategies,
  strategy,
  onStrategy,
  file,
  onFile,
  onStage,
  pending,
}: {
  destination: DestinationMeta;
  strategies: Strategy[];
  strategy?: Strategy;
  onStrategy: (strategyId: number | null) => void;
  file: File | null;
  onFile: (file: File | null) => void;
  onStage: (event: FormEvent) => void;
  pending: boolean;
}) {
  return (
    <Panel
      title="Arquivo"
      action={
        <span className="text-[11.5px] text-ink-faint">destino e parser pré-preenchidos</span>
      }
    >
      <form onSubmit={onStage} className="space-y-4">
        <div className="rounded-sm border border-line bg-surface-2 px-3 py-2.5 text-[12.5px]">
          <p className="text-ink">
            Destino definido pelo contexto: <b>{destination.label}</b>
            {strategy !== undefined && (
              <>
                {" · "}
                <b>{strategy.name}</b>
              </>
            )}
          </p>
          <p className="mt-1 text-ink-dim">Parser reconhecido: {destination.parser}</p>
        </div>

        {strategies.length === 0 ? (
          <p className={CALLOUT}>Nenhuma estratégia com relatório para receber a edição.</p>
        ) : (
          strategy === undefined && (
            <label className="block text-[12.5px] text-ink-dim">
              Estratégia
              <select
                aria-label="Estratégia"
                value=""
                onChange={(event) =>
                  onStrategy(event.target.value === "" ? null : Number(event.target.value))
                }
                className={`mt-1 block w-full ${FIELD}`}
              >
                <option value="">Escolha a estratégia</option>
                {strategies.map((option) => (
                  <option key={option.id} value={option.id}>
                    {option.name}
                  </option>
                ))}
              </select>
            </label>
          )
        )}

        <ReportFileInput accept=".pdf" onFile={onFile} />
        <p className={CALLOUT}>
          Nada é aplicado no upload: o relatório vira uma edição em revisão. Promover substitui a
          edição ativa e é uma decisão separada.
        </p>
        <Button
          type="submit"
          variant="primary"
          disabled={!file || strategy === undefined || pending}
        >
          {pending ? "Enviando…" : "Enviar e revisar"}
        </Button>
      </form>
    </Panel>
  );
}

function StepEditionDestination({
  destination,
  strategy,
  staged,
  parser,
  fileName,
}: {
  destination: DestinationMeta;
  strategy: Strategy;
  staged: Edition;
  parser?: string;
  fileName?: string;
}) {
  return (
    <div className="space-y-4">
      <Panel
        title="Destino"
        action={<span className="text-[11.5px] text-ink-faint">pré-preenchido</span>}
      >
        <dl className="divide-y divide-line-soft text-[12.5px]">
          <Detail
            label="Destino"
            value={`${strategy.name} · ${referenceMonthLabel(staged.reference_month)}`}
          />
          <Detail label="Parser" value={parser ?? destination.parser} />
          <Detail label="Aplicação" value="promover a edição a ativa" />
          {fileName !== undefined && <Detail label="Arquivo" value={fileName} />}
        </dl>
      </Panel>

      <Panel
        title="Mapeamento"
        action={<span className="text-[11.5px] text-ink-faint">template lembrado</span>}
      >
        <MappingGrid mapping={destination.mapping} />
        <p className={`mt-3 ${CALLOUT}`}>
          O template reconhecido preenche as colunas, mas nada entra sem esta revisão.
        </p>
      </Panel>
    </div>
  );
}

function StepEditionDiff({
  active,
  staged,
  loading,
}: {
  active?: Edition;
  staged: Edition;
  loading: boolean;
}) {
  const rows = editionDeltaRows(staged, active);
  const summary = editionDeltaSummary(rows);
  const alerts = rows.filter((row) => row.doNotTradeFlip);
  const exited = editionExitedRows(staged, active);

  const columns: Column<EditionDeltaRow>[] = [
    {
      key: "ticker",
      header: "Ticker",
      render: (row) => <span className="font-mono font-semibold text-ink">{row.ticker}</span>,
    },
    {
      key: "weight",
      header: "Peso",
      align: "right",
      render: (row) => (
        <span className="font-mono">{deltaText(row.weightBefore, row.weight, formatTarget)}</span>
      ),
    },
    {
      key: "target",
      header: "Preço-alvo",
      align: "right",
      render: (row) => (
        <span className="font-mono">
          {deltaText(row.targetPriceBefore, row.targetPrice, priceLabel)}
        </span>
      ),
    },
    {
      key: "rating",
      header: "Rating",
      render: (row) => deltaText(row.ratingBefore, row.rating, ratingLabel),
    },
    {
      key: "do_not_trade",
      header: "Não operar",
      render: (row) => <DoNotTradeCell row={row} />,
    },
    {
      key: "change",
      header: "Situação",
      render: (row) => <EditionChangeCell change={row.change} />,
    },
  ];

  return (
    <div className="space-y-4">
      <p className={CALLOUT}>
        O assistente compara o relatório com a edição ativa antes de promover; a promoção acontece
        na última etapa.
      </p>

      <Panel
        title="Diff da edição"
        action={
          <div className="flex flex-wrap items-center gap-2 text-[11.5px]">
            <span className="text-ink-dim">
              {plural(summary.weightChanges, "peso alterado", "pesos alterados")}
            </span>
            <span className="text-ink-dim">
              {plural(summary.targetPriceChanges, "preço-alvo alterado", "preços-alvo alterados")}
            </span>
            <span className="text-ink-dim">
              {plural(summary.ratingChanges, "rating alterado", "ratings alterados")}
            </span>
            {summary.alerts > 0 && (
              <Chip tone="rose">{plural(summary.alerts, "alerta", "alertas")}</Chip>
            )}
            {exited.length > 0 && (
              <Chip tone="rose">{plural(exited.length, "saída", "saídas")}</Chip>
            )}
          </div>
        }
      >
        {loading ? (
          <p className="text-[13.5px] text-ink-dim">Carregando…</p>
        ) : (
          <>
            <p className="mb-2 text-[12.5px] text-ink-dim">
              Contra a edição ativa{" "}
              <span className="font-mono text-ink">
                {active === undefined ? "—" : referenceMonthLabel(active.reference_month)}
              </span>
              . Uma linha aparece como alterada quando peso, rating ou preço-alvo mudam; o{" "}
              <b className="text-ink">não operar</b> é um alerta de política.
            </p>
            <div data-testid="edition-delta">
              <Table columns={columns} rows={rows} rowKey={(row) => row.ticker} />
            </div>

            {exited.length > 0 && (
              <div data-testid="edition-exits" className="mt-3">
                <p className="mb-1 text-[11.5px] text-ink-dim">Saídas</p>
                <div className="flex flex-wrap gap-1.5">
                  {exited.map((row) => (
                    <Chip key={row.ticker} tone="rose">
                      − {row.ticker} {formatTarget(row.weight)}
                    </Chip>
                  ))}
                </div>
              </div>
            )}

            {alerts.length > 0 && (
              <div className="mt-3 space-y-2">
                {alerts.map((row) => (
                  <p key={row.ticker} className={DANGER}>
                    <b>Alerta.</b>{" "}
                    {row.doNotTrade
                      ? `${row.ticker} passa a não operar nesta edição: o plano não sugere compra nem venda do ativo enquanto ela estiver ativa.`
                      : `${row.ticker} deixa de não operar nesta edição: o plano volta a poder sugerir compra ou venda do ativo.`}
                  </p>
                ))}
              </div>
            )}

            <p className={`mt-3 ${CALLOUT}`}>
              <b>O que o diff não diz.</b> Esta edição muda alvos de estratégia, não custódia.{" "}
              {NEXT_RECOMPUTE_NOTE}
            </p>
          </>
        )}
      </Panel>
    </div>
  );
}

function StepEditionPromote({
  destination,
  strategy,
  staged,
  active,
  decision,
  pending,
  error,
  onPromote,
  onDiscard,
  onAnotherFile,
}: {
  destination: DestinationMeta;
  strategy: Strategy;
  staged: Edition;
  active?: Edition;
  decision?: EditionDecision;
  pending: boolean;
  error?: string;
  onPromote: () => void;
  onDiscard: () => void;
  onAnotherFile: () => void;
}) {
  const promoted = decision === "promoted";

  return (
    <div className="space-y-4">
      <Panel title="O que esta importação muda">
        <ul className="divide-y divide-line-soft text-[12.5px]">
          {destination.changes.map((change) => (
            <li key={change.object} className="flex flex-wrap items-baseline gap-2 py-1.5">
              <span className="font-medium text-ink">{change.object}</span>
              <span className="text-ink-dim">{change.what}</span>
            </li>
          ))}
        </ul>
        <Link
          to={strategyDetailTab(strategy.id, "edicoes")}
          className="mt-2 inline-block text-[12px] font-medium text-accent hover:underline"
        >
          Abrir Edições de {strategy.name} →
        </Link>
      </Panel>

      <Panel title={promoted ? "Promovida" : "Promover"}>
        {promoted ? (
          <div className="space-y-2 text-[12.5px] text-ink">
            <p data-testid="edition-decision-note">
              <b>Edição promovida.</b> O plano usa os novos pesos no próximo recálculo.
            </p>
            <Link
              to={strategyDetailTab(strategy.id, "edicoes")}
              className="inline-block text-[12px] font-medium text-accent hover:underline"
            >
              Ver Estratégias →
            </Link>
          </div>
        ) : (
          <div className="space-y-3 text-[12.5px] text-ink">
            <p>
              Edição {referenceMonthLabel(staged.reference_month)} contra a ativa{" "}
              <span className="font-mono">
                {active === undefined ? "—" : referenceMonthLabel(active.reference_month)}
              </span>
              .
            </p>
            <p data-testid="edition-decision-note">
              {decision === "discarded"
                ? "Edição não promovida · a edição ativa segue valendo até o próximo recálculo; a edição em revisão continua na estratégia até ser substituída."
                : "Promover substitui a edição ativa; descartar deixa a edição ativa como está."}
            </p>
            <p className={CALLOUT}>{NEXT_RECOMPUTE_NOTE}</p>
            <div className="flex flex-wrap items-center gap-2">
              <Button variant="primary" onClick={onPromote} disabled={pending}>
                {pending ? "Promovendo…" : "Promover a ativa"}
              </Button>
              <Button variant="ghost" onClick={onDiscard} disabled={pending}>
                Descartar
              </Button>
              <Button variant="ghost" onClick={onAnotherFile} disabled={pending}>
                Enviar outro arquivo
              </Button>
            </div>
            {error !== undefined && <p className="text-tax">{error}</p>}
          </div>
        )}
      </Panel>
    </div>
  );
}

function DoNotTradeCell({ row }: { row: EditionDeltaRow }) {
  if (row.doNotTradeFlip) {
    return row.doNotTrade ? (
      <Chip tone="rose">novo: não operar</Chip>
    ) : (
      <Chip tone="amber">deixa de não operar</Chip>
    );
  }
  return row.doNotTrade ? <Chip tone="amber">sim</Chip> : <span className="text-ink-dim">—</span>;
}

function EditionChangeCell({ change }: { change: EditionEntryChange }) {
  if (change === "new") return <Chip tone="green">nova</Chip>;
  if (change === "changed") return <Chip tone="amber">alterada</Chip>;
  return <span className="text-ink-dim">—</span>;
}

function priceLabel(value: number | undefined): string {
  return value === undefined ? "—" : formatCurrency(value);
}

function deltaText<T>(
  before: T | undefined,
  after: T | undefined,
  format: (value: T | undefined) => string,
): string {
  if (before === undefined || before === after) return format(after);
  return `${format(before)} → ${format(after)}`;
}

function StepFile({
  destination,
  file,
  onFile,
  onStage,
  pending,
}: {
  destination: DestinationMeta;
  file: File | null;
  onFile: (file: File | null) => void;
  onStage: (event: FormEvent) => void;
  pending: boolean;
}) {
  return (
    <Panel
      title="Arquivo"
      action={
        <span className="text-[11.5px] text-ink-faint">destino e parser pré-preenchidos</span>
      }
    >
      {!destination.supported ? (
        <p className={CALLOUT}>
          O assistente para {destination.label} chega numa próxima fatia. Por enquanto, envie um
          extrato B3 de negociação para Ativos · Negócios.
        </p>
      ) : (
        <form onSubmit={onStage} className="space-y-4">
          <div className="rounded-sm border border-line bg-surface-2 px-3 py-2.5 text-[12.5px]">
            <p className="text-ink">
              Destino definido pelo contexto: <b>{destination.label}</b>
            </p>
            <p className="mt-1 text-ink-dim">Parser reconhecido: {destination.parser}</p>
          </div>
          <ReportFileInput accept=".xlsx" onFile={onFile} />
          <p className={CALLOUT}>
            Nada é aplicado no upload: o arquivo é lido, tem o hash calculado e fica em revisão. A
            aplicação é uma decisão separada.
          </p>
          <Button type="submit" variant="primary" disabled={!file || pending}>
            {pending ? "Enviando…" : "Enviar e revisar"}
          </Button>
        </form>
      )}
    </Panel>
  );
}

function StepDestination({
  destination,
  record,
}: { destination: DestinationMeta; record: Import }) {
  return (
    <div className="space-y-4">
      <Panel
        title="Destino"
        action={<span className="text-[11.5px] text-ink-faint">pré-preenchido</span>}
      >
        <dl className="divide-y divide-line-soft text-[12.5px]">
          <Detail label="Destino" value={destination.label} />
          <Detail label="Parser" value={destination.parser} />
          <Detail label="Aplicação" value="aplicar as linhas válidas" />
          <Detail
            label="Arquivo"
            value={`${record.file_name} · sha256 ${shortHash(record.file_hash)}`}
          />
        </dl>
      </Panel>

      <Panel
        title="Mapeamento"
        action={<span className="text-[11.5px] text-ink-faint">template lembrado</span>}
      >
        <MappingGrid mapping={destination.mapping} />
        <p className={`mt-3 ${CALLOUT}`}>
          O template reconhecido preenche as colunas, mas nada entra sem esta revisão.
        </p>
      </Panel>
    </div>
  );
}

function StepRows({
  rows,
  situations,
  rejected,
  loading,
}: {
  rows: ImportRow[];
  situations: ReturnType<typeof summarizeSituations>;
  rejected: number;
  loading: boolean;
}) {
  return (
    <Panel
      title="Linhas"
      action={
        <div className="flex flex-wrap items-center gap-3 text-[11.5px]">
          <span className="text-yield">{situations.new} aceitas</span>
          <span className="text-accent">{situations.duplicate_in_ledger} já no livro</span>
          <span className="text-accent">{situations.duplicate_in_file} repetidas no arquivo</span>
          <span className="text-tax">{rejected} com atenção</span>
        </div>
      }
    >
      {rejected > 0 && (
        <p className={`mb-3 ${CALLOUT}`}>
          {rejected} linha(s) com atenção ficam no lote de reenvio: as linhas válidas aplicam, a
          rejeitada não bloqueia o lote.
        </p>
      )}
      {loading ? (
        <p className="text-[13.5px] text-ink-dim">Carregando…</p>
      ) : rows.length === 0 ? (
        <p className="text-[13.5px] text-ink-dim">Nenhuma linha no arquivo.</p>
      ) : (
        <Table columns={ROW_COLUMNS} rows={rows} rowKey={(row) => row.id} />
      )}
    </Panel>
  );
}

function StepApply({
  destination,
  record,
  situations,
  outcomes,
  validRows,
  rejectedRows,
  pending,
  onApply,
}: {
  destination: DestinationMeta;
  record: Import;
  situations: ReturnType<typeof summarizeSituations>;
  outcomes: ReturnType<typeof summarizeOutcomes>;
  validRows: number;
  rejectedRows: ImportRow[];
  pending: boolean;
  onApply: () => void;
}) {
  return (
    <div className="space-y-4">
      <Panel title="O que esta importação muda">
        <ul className="divide-y divide-line-soft text-[12.5px]">
          {destination.changes.map((change) => (
            <li key={change.object} className="flex flex-wrap items-baseline gap-2 py-1.5">
              <span className="font-medium text-ink">{change.object}</span>
              <span className="text-ink-dim">{change.what}</span>
            </li>
          ))}
        </ul>
        <Link
          to={destination.route}
          className="mt-2 inline-block text-[12px] font-medium text-accent hover:underline"
        >
          Abrir {destination.label} →
        </Link>
      </Panel>

      <Panel
        title={
          record.status === "APPLIED"
            ? "Aplicada"
            : record.status === "FAILED"
              ? "Falhou"
              : "Aplicar"
        }
      >
        {record.status === "APPLIED" ? (
          <div className="text-[12.5px] text-ink">
            <ImportOutcomeSummary
              applied={outcomes.applied}
              rejected={outcomes.rejected}
              skippedDuplicate={outcomes.skipped_duplicate}
            />
          </div>
        ) : record.status === "FAILED" ? (
          <p className="text-[12.5px] text-tax">
            Esta tentativa falhou e não pode ser aplicada. O arquivo fica no registro da fonte;
            reenvie-o para abrir uma nova sessão.
          </p>
        ) : (
          <div className="space-y-3 text-[12.5px] text-ink">
            <p>
              {validRows} linha(s) válida(s) · {situations.duplicate_in_ledger} já no livro ·{" "}
              {rejectedRows.length} no lote de reenvio.
            </p>
            <p className={DANGER}>
              Nada foi aplicado ainda. Aplicar grava as linhas válidas no destino; as rejeitadas
              ficam guardadas no lote de reenvio.
            </p>
            {rejectedRows.length > 0 && (
              <div className="text-ink-dim">
                {rejectedRows.map((row) => (
                  <p key={row.id}>
                    {row.ticker}: {row.error ?? "linha com atenção"}
                  </p>
                ))}
              </div>
            )}
            <Button variant="danger" onClick={onApply} disabled={pending}>
              {pending ? "Aplicando…" : `Aplicar ${validRows} linha(s) válida(s)`}
            </Button>
          </div>
        )}
      </Panel>
    </div>
  );
}

function Detail({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-baseline gap-3 py-1.5">
      <dt className="w-24 flex-none text-ink-dim">{label}</dt>
      <dd className="text-ink">{value}</dd>
    </div>
  );
}
