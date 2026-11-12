// Pure Carteira · Visão geral derivations — no React imports here (see AGENTS.md). The landing reads
// the status aggregate, the class allocation, the performance series and the last close's tax, and
// this module turns them into the six-cell strip, the month line, the standfirst, the class rows,
// the results panel, the attention queue grouped by severity and the data-state panel. Every
// sentence is written here from typed fields; the wire never carries prose.

import type {
  Allocation,
  AssetClass,
  AttentionItem,
  AttentionSeverity,
  PerformanceSeries,
  PeriodPerformance,
  PortfolioStatus,
  StockTaxReport,
} from "../api/types.ts";
import { distanceText, formatPoints, formatTarget } from "./allocation.ts";
import { pendingTransferNoun } from "./fechamento.ts";
import {
  formatCurrency,
  formatIsoDate,
  formatNumber,
  formatPercent,
  formatSignedPercent,
} from "./format.ts";
import { assetClassLabel, monthPeriodLabel } from "./month.ts";
import { ROUTES, positionDetail } from "./routes.ts";
import { openMonthRef } from "./status.ts";
import { type Tone, deficitTone, driftTone } from "./tones.ts";

export type StripTone = Extract<Tone, "default" | "deficit" | "surplus" | "muted">;

export interface StripCell {
  key: string;
  label: string;
  value: string;
  hint: string;
  tone: StripTone;
}

// The six cells above the fold: Capital, Investido, Caixa, Drift, Déficit and Atenção. The attention
// cell's value is the action count, so the one number that asks for a decision is the one on screen.
function attentionActionCount(items: AttentionItem[]): number {
  return items.filter((item) => item.severity === "ACTION").length;
}

function countLabel(count: number, singular: string, plural: string): string {
  return `${formatNumber(count)} ${count === 1 ? singular : plural}`;
}

// The latest performance point, shared by the results panel and the standfirst.
function latestMonth(series: PerformanceSeries | undefined): PeriodPerformance | undefined {
  const months = series?.months ?? [];
  return months[months.length - 1];
}

export function statusCells(status: PortfolioStatus): StripCell[] {
  const total = status.attention.length;
  const action = attentionActionCount(status.attention);
  const openMonth = openMonthRef(status);
  const investedShare =
    status.capital > 0 ? formatPercent((status.invested / status.capital) * 100, 1) : "—";

  return [
    {
      key: "capital",
      label: "Capital",
      value: formatCurrency(status.capital),
      hint:
        openMonth === undefined
          ? "sem mês aberto"
          : `base de ${monthPeriodLabel(openMonth.period)}`,
      tone: "default",
    },
    {
      key: "invested",
      label: "Investido",
      value: formatCurrency(status.invested),
      hint: `${investedShare} do Capital`,
      tone: "default",
    },
    {
      key: "cash",
      label: "Caixa",
      value:
        status.uninvested_balance === undefined ? "—" : formatCurrency(status.uninvested_balance),
      hint:
        status.uninvested_balance === undefined
          ? "capital − investido incompleto"
          : "capital − investido",
      tone:
        status.uninvested_balance !== undefined && status.uninvested_balance < 0
          ? "deficit"
          : "default",
    },
    {
      key: "drift",
      label: "Drift",
      value: formatPoints(status.drift),
      hint: "contra os alvos",
      tone: driftTone(status.drift),
    },
    {
      key: "deficit",
      label: "Déficit",
      value: formatCurrency(status.deficit),
      hint: "a alocar",
      tone: deficitTone(status.deficit),
    },
    {
      key: "attention",
      label: "Atenção",
      value: String(action),
      hint: total === 0 ? "nada pendente" : total === action ? "para agir" : `${total} na fila`,
      tone: total > 0 ? "surplus" : "muted",
    },
  ];
}

export interface MonthLineView {
  period: string;
  planState: string;
  steps: string;
  orders: string;
  transfers: string;
  to: string;
}

// The month is a line, not a card: the open month, its plan state, the tracked step fraction, the
// suggested orders and the transfer state, with the single link into Plano / Mês.
export function monthLine(status: PortfolioStatus): MonthLineView {
  const openMonth = openMonthRef(status);
  const open = openMonth !== undefined;
  const pending = status.month.pending_transfers;
  return {
    period: openMonth === undefined ? "Nenhum mês aberto" : monthPeriodLabel(openMonth.period),
    planState: open ? "plano aberto" : "sem mês aberto",
    steps: `${status.month.steps_done} de ${status.month.steps_total} etapas`,
    orders: countLabel(status.month.suggested_orders, "ordem sugerida", "ordens sugeridas"),
    transfers: !open
      ? "sem mês aberto"
      : pending === 0
        ? "sem transferências pendentes"
        : `${formatNumber(pending)} ${pendingTransferNoun(pending)}`,
    to: ROUTES.plan,
  };
}

export interface ClassRow {
  key: AssetClass;
  label: string;
  target: string;
  ideal: string;
  actual: string;
  distance: string;
  distanceTone: StripTone;
  drift: number;
  driftTone: StripTone;
}

// Exactly the three class rungs — never a strategy or a ticker. The full tree belongs to Alocação.
// The derivation also owns each cell's tone, so the page never re-decodes the sign into a colour.
export function classRows(allocation: Allocation | null): ClassRow[] {
  if (allocation === null) return [];
  return allocation.classes.map((rung) => ({
    key: rung.asset_class,
    label: assetClassLabel(rung.asset_class),
    target: formatTarget(rung.target),
    ideal: formatCurrency(rung.ideal),
    actual: formatCurrency(rung.actual),
    distance: distanceText(rung.deficit),
    distanceTone: deficitTone(rung.deficit),
    drift: rung.drift,
    driftTone: driftTone(rung.drift),
  }));
}

export interface AttentionRow {
  key: string;
  severity: AttentionSeverity;
  title: string;
  detail: string;
  to: string;
}

export interface AttentionQueueView {
  action: AttentionRow[];
  watch: AttentionRow[];
  total: number;
}

interface AttentionDescriptor {
  title: string;
  detail: string;
  to: string;
}

function days(count: number): string {
  return `${formatNumber(count)} ${count === 1 ? "dia" : "dias"}`;
}

// One descriptor per finding owns both the sentence and the surface it deep-links to, so a new kind
// is one case here and never two switch edits that can drift apart.
function attentionDescriptor(item: AttentionItem): AttentionDescriptor {
  const ticker = item.ref.ticker;
  const quantity = item.params.quantity;
  const amount = item.params.amount;

  switch (item.kind) {
    case "PENDING_TRANSFER": {
      const parts = [ticker ?? "—"];
      if (quantity !== undefined) parts.push(`${formatNumber(quantity)} cotas`);
      const detail = parts.join(" · ");
      return {
        title: "Transferência pendente",
        detail: amount === undefined ? detail : `${detail} · ${formatCurrency(amount)}`,
        to: ROUTES.planTransfers,
      };
    }
    case "STAGED_IMPORT":
      return {
        title: "Importação em revisão",
        detail: `Enviada há ${days(item.params.age_days ?? 0)}`,
        to: ROUTES.imports,
      };
    case "STAGED_EDITION":
      return {
        title: "Edição aguardando promoção",
        detail: `${formatNumber(item.params.entry_count ?? 0)} linhas no diff`,
        to: ROUTES.strategies,
      };
    case "CORPORATE_ACTION_REVIEW":
      return {
        title: ticker === undefined ? "Ação corporativa para revisar" : `${ticker} para revisar`,
        detail: `Espera há ${days(item.params.age_days ?? 0)}`,
        to: ROUTES.corporateActions,
      };
    case "UNCLASSIFIED_TICKER":
      return {
        title: `${ticker ?? "Ativo"} sem classe`,
        detail: `${formatNumber(quantity ?? 0)} cotas fora da alocação`,
        // The ticker's own detail carries the Classificar action; the bulk queue is on Alocação.
        to: ticker === undefined ? ROUTES.allocation : positionDetail(ticker),
      };
    case "OPEN_GAP":
      return {
        title: ticker === undefined ? "Lacuna aberta" : `Lacuna em ${ticker}`,
        detail: `${formatNumber(quantity ?? 0)} cotas · aberta há ${days(item.params.age_days ?? 0)}`,
        to: ROUTES.gaps,
      };
    case "STALE_QUOTE":
      return {
        title: `Cotação de ${ticker ?? "ativo"} desatualizada`,
        detail: `${days(item.params.days_old ?? 0)} · fechamento ${formatCurrency(
          item.params.close ?? 0,
        )}`,
        // Ativos · Cotações owns quote freshness and the refresh; the sub-view is reachable as a
        // placeholder until its own slice lands.
        to: ROUTES.quotes,
      };
  }
}

function attentionRow(item: AttentionItem): AttentionRow {
  return {
    // The aggregate guarantees a ref id or ticker; the fallback keeps a malformed row keyed rather
    // than reaching for the array position, which would change when the queue regroups by severity.
    key: `${item.kind}:${item.ref.type}:${item.ref.id ?? item.ref.ticker ?? "sem-ref"}`,
    severity: item.severity,
    ...attentionDescriptor(item),
  };
}

// The queue split by severity: action first, then watch. Order inside each group is the server's,
// which already lists the actionable object kinds before the watched ones.
export function attentionQueue(items: AttentionItem[]): AttentionQueueView {
  const rows = items.map((item) => attentionRow(item));
  return {
    action: rows.filter((row) => row.severity === "ACTION"),
    watch: rows.filter((row) => row.severity === "WATCH"),
    total: rows.length,
  };
}

export type DataTone = Extract<Tone, "default" | "attention">;

export interface DataRow {
  key: string;
  label: string;
  value: string;
  tone: DataTone;
}

export interface DataPanelView {
  rows: DataRow[];
  empty: boolean;
}

// How much to trust the numbers on screen: quote freshness, every stale ticker by name, the frozen
// snapshot, the open custody gaps and the last close.
export function dataStatePanel(status: PortfolioStatus): DataPanelView {
  const { data, last_close } = status;
  const rows: DataRow[] = [
    {
      key: "quotes",
      label: "Cotações",
      value: data.quotes_as_of === undefined ? "sem cotações" : formatIsoDate(data.quotes_as_of),
      tone: data.quotes_as_of === undefined ? "attention" : "default",
    },
    ...data.stale_quotes.map((quote) => ({
      key: `stale-${quote.ticker}`,
      label: quote.ticker,
      value: `${formatIsoDate(quote.date)} · ${days(quote.days_old)} · desatualizada`,
      tone: "attention" as const,
    })),
    {
      key: "snapshot",
      label: "Snapshot",
      value:
        data.last_snapshot === undefined
          ? "sem fechamento"
          : `${monthPeriodLabel(data.last_snapshot.period)} · congelado`,
      tone: "default",
    },
    {
      key: "gaps",
      label: "Lacunas",
      value:
        data.open_gaps === 0
          ? "nenhuma aberta"
          : `${formatNumber(data.open_gaps)} ${data.open_gaps === 1 ? "aberta" : "abertas"}`,
      tone: data.open_gaps > 0 ? "attention" : "default",
    },
    {
      key: "last-close",
      label: "Último fechamento",
      value: last_close === undefined ? "sem fechamento" : monthPeriodLabel(last_close.period),
      tone: "default",
    },
  ];

  const empty =
    data.quotes_as_of === undefined &&
    data.stale_quotes.length === 0 &&
    data.open_gaps === 0 &&
    data.last_snapshot === undefined &&
    last_close === undefined;

  return { rows, empty };
}

export interface SparkBar {
  period: string;
  percentage?: number;
  // Pixel height on the 46 px rail, scaled by the square root so small months stay visible.
  height: number;
  positive: boolean;
}

export interface ResultRow {
  key: string;
  label: string;
  value: string;
  hint?: string;
  tone: Extract<Tone, "default" | "return-up" | "return-down">;
}

export interface ResultsView {
  period?: string;
  bars: SparkBar[];
  rows: ResultRow[];
  empty: boolean;
}

// The results panel: twelve months of returns as a sparkline, the last month against its benchmark,
// the month's income, the DARF it owes and the annualized return since the first close.
export function resultsPanel(
  series: PerformanceSeries | undefined,
  tax: StockTaxReport | undefined,
): ResultsView {
  const months = series?.months ?? [];
  const last = latestMonth(series);
  if (last === undefined) return { bars: [], rows: [], empty: true };

  const bars = months.slice(-12).map((point) => {
    const percentage = point.decomposition.return_percentage;
    return {
      period: point.period,
      percentage,
      height: Math.max(3, Math.min(40, Math.abs(percentage ?? 0) ** 0.7 * 16)),
      positive: (percentage ?? 0) >= 0,
    };
  });

  const result = last.decomposition.return_percentage;
  const benchmark = last.benchmark_return;
  const xirr = series?.xirr ?? series?.computed_xirr;
  const rows: ResultRow[] = [
    {
      key: "last",
      label: monthPeriodLabel(last.period),
      value: formatSignedPercent(result),
      hint: benchmark === undefined ? "sem benchmark" : `vs ${formatSignedPercent(benchmark)}`,
      tone:
        result === undefined || result === 0 ? "default" : result > 0 ? "return-up" : "return-down",
    },
    {
      key: "income",
      label: "Proventos do mês",
      value: formatCurrency(last.decomposition.net_income),
      tone: "default",
    },
    {
      key: "darf",
      label: tax === undefined ? "DARF" : `DARF de ${monthPeriodLabel(tax.period)}`,
      value: tax === undefined ? "—" : formatCurrency(tax.meter.tax),
      hint: tax === undefined ? undefined : `vence ${formatIsoDate(tax.darf_due_date)}`,
      tone: "default",
    },
  ];
  if (xirr !== undefined) {
    rows.push({
      key: "xirr",
      label: `XIRR desde ${monthPeriodLabel(months[0]?.period ?? last.period)}`,
      value: formatSignedPercent(xirr),
      tone: "default",
    });
  }

  return { period: monthPeriodLabel(last.period), bars, rows, empty: false };
}

export interface StandfirstPart {
  key: string;
  text: string;
  strong?: boolean;
}

function appendPart(parts: StandfirstPart[], key: string, text: string, strong = false): void {
  parts.push(strong ? { key, text, strong: true } : { key, text });
}

// The month standfirst: one written sentence, values inline. It borrows the document direction's
// prose rather than stacking a second card under the strip.
export function monthStandfirst(
  status: PortfolioStatus,
  series: PerformanceSeries | undefined,
): StandfirstPart[] {
  const last = latestMonth(series);
  const open = openMonthRef(status);

  if (last === undefined && open === undefined) {
    return [{ key: "empty", text: "Nenhum mês foi fechado e nenhum mês está aberto." }];
  }

  const parts: StandfirstPart[] = [];
  if (last !== undefined) {
    const result = last.decomposition.return_percentage;
    appendPart(parts, "result-intro", `${monthPeriodLabel(last.period)} fechou em `);
    appendPart(parts, "result", formatSignedPercent(result), true);
    const benchmark = last.benchmark_return;
    if (benchmark === undefined) {
      appendPart(parts, "benchmark-missing", ", sem benchmark no período");
    } else {
      appendPart(
        parts,
        "benchmark-direction",
        result !== undefined && result >= benchmark
          ? ", à frente do benchmark de "
          : ", atrás do benchmark de ",
      );
      appendPart(parts, "benchmark", formatSignedPercent(benchmark), true);
    }
  } else {
    appendPart(parts, "no-close", "A carteira ainda não tem um mês fechado");
  }

  if (open !== undefined) {
    appendPart(parts, "month", `; ${monthPeriodLabel(open.period)} abre com `);
    if (status.uninvested_balance === undefined) {
      appendPart(parts, "cash-unknown", "caixa ainda indefinido");
    } else {
      appendPart(parts, "cash", formatCurrency(status.uninvested_balance), true);
      appendPart(parts, "cash-unit", " de caixa");
    }
    appendPart(parts, "deficit-intro", " e ");
    appendPart(parts, "deficit", formatCurrency(status.deficit), true);
    appendPart(parts, "deficit-unit", " a alocar");
  } else {
    appendPart(parts, "no-month", "; nenhum mês está aberto");
  }

  appendPart(parts, "drift-intro", "; a carteira está ");
  if (status.drift === 0) {
    appendPart(parts, "drift", "nos alvos");
  } else {
    appendPart(parts, "drift", formatPoints(Math.abs(status.drift)), true);
    appendPart(
      parts,
      "drift-direction",
      status.drift > 0 ? " acima dos alvos" : " abaixo dos alvos",
    );
  }

  const transfer = status.attention.find((item) => item.kind === "PENDING_TRANSFER");
  const actions = attentionActionCount(status.attention);
  if (transfer !== undefined) {
    appendPart(
      parts,
      "transfer",
      ` e a transferência${transfer.ref.ticker === undefined ? "" : ` de ${transfer.ref.ticker}`} espera decisão`,
    );
  } else if (actions > 0) {
    appendPart(
      parts,
      "action-count",
      ` e ${actions} ${actions === 1 ? "achado espera" : "achados esperam"} decisão`,
    );
  }
  appendPart(parts, "terminal", ".");

  return parts;
}
