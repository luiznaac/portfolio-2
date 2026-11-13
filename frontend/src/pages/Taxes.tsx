import { type FormEvent, useState } from "react";
import {
  useAcceptHarvestPair,
  useClosedMonths,
  useCurrentMonth,
  useHarvest,
  useMonthPeriodRange,
  useSetHarvestOptOut,
  useStockTax,
} from "../api/queries.ts";
import type {
  CarryBucket,
  FiiHarvestReport,
  HarvestIgnored,
  HarvestPair,
  HarvestProposal,
  HarvestReport,
  StockTaxReport,
} from "../api/types.ts";
import { Button } from "../components/Button.tsx";
import { Chip } from "../components/Chip.tsx";
import { Field, Input } from "../components/Field.tsx";
import { Kpi } from "../components/Kpi.tsx";
import { Panel } from "../components/Panel.tsx";
import { PeriodRail } from "../components/PeriodRail.tsx";
import { type Column, Table } from "../components/Table.tsx";
import { useAnalysisWindow } from "../hooks/useAnalysisWindow.tsx";
import { windowMonthKeys, windowRangeLabel } from "../lib/analysisWindow.ts";
import { monthKey, todayIso } from "../lib/dates.ts";
import { formatCurrency, formatIsoDate, formatNumber } from "../lib/format.ts";
import { monthPeriodLabel } from "../lib/month.ts";
import {
  type HarvestPairFormState,
  type RealizedRow,
  type TaxApuracaoRow,
  carryAmount,
  carryClosing,
  darfDeadlineLabel,
  darfIsOverdue,
  harvestPairForm,
  hasSales,
  meterLabel,
  meterProgress,
  realizedKindLabel,
  realizedRows,
  taxApuracoes,
  taxGrainNote,
  taxPeriodChip,
  toHarvestPairCreation,
  unclassifiedStatusLabel,
} from "../lib/taxes.ts";

// Análise · Impostos: the monthly read over the shared rail. The month anchor selects the apuração
// (GET /tax/{period}) and renders the R$20k stock-sale meter, the realized result per kind, the
// three carry buckets and the DARF 6015 deadline for that month. The window never fakes the monthly
// read into a window read — it only filters which apurações the side panel lists. The harvest pass
// is carried as it exists in production, for the open month only.
export function Taxes() {
  const range = useMonthPeriodRange();
  const current = useCurrentMonth();
  const closed = useClosedMonths();
  const currentPeriod = monthKey(todayIso());
  const openPeriod = current.data?.month.period ?? null;
  const closedPeriods = (closed.data ?? []).map((month) => month.period);
  const periods = range.data ? windowMonthKeys({ start: range.data.min, end: range.data.max }) : [];
  const rail = useAnalysisWindow(periods, closed.data?.[0]?.period);
  const anchor = rail.anchor;
  const isOpenMonth = openPeriod !== null && anchor === openPeriod;
  const tax = useStockTax(anchor ?? "");
  const harvest = useHarvest(isOpenMonth);

  if (range.isLoading) {
    return (
      <section>
        <p className="text-[13.5px] text-muted">Carregando…</p>
      </section>
    );
  }

  if (range.isError) {
    return (
      <section>
        <p className="text-[13.5px] text-return-down">{range.error.message}</p>
      </section>
    );
  }

  if (anchor === undefined || rail.range === undefined) {
    return (
      <section>
        <Panel title="Sem apurações">
          <p className="text-[13px] text-muted">
            Nenhum mês na janela de planejamento. Importe um extrato ou abra um mês para começar.
          </p>
        </Panel>
      </section>
    );
  }

  if (tax.isLoading) {
    return (
      <section>
        <p className="text-[13.5px] text-muted">Carregando…</p>
      </section>
    );
  }

  if (tax.isError) {
    return (
      <section>
        <p className="text-[13.5px] text-return-down">{tax.error.message}</p>
      </section>
    );
  }

  if (!tax.data) return null;

  const report = tax.data;
  const apuracoes = taxApuracoes(periods, rail.range, currentPeriod, openPeriod, closedPeriods);
  const chip = taxPeriodChip(anchor, currentPeriod, openPeriod, closedPeriods);

  return (
    <section>
      <PeriodRail
        preset={rail.preset}
        anchor={anchor}
        range={rail.range}
        bounds={rail.bounds}
        onPreset={rail.setPreset}
        onShift={rail.shift}
        grainNote={taxGrainNote(anchor)}
      />

      <TaxStrip report={report} />

      <div className="mb-4 grid items-start gap-3 xl:grid-cols-[minmax(0,1.6fr)_minmax(0,1fr)]">
        <div className="space-y-4">
          <MeterPanel tax={report} />
          <Panel
            title={`Apuração de ${monthPeriodLabel(report.period)}`}
            action={chip && <Chip tone={chip.tone}>{chip.label}</Chip>}
          >
            <div data-testid="tax-realized">
              {hasSales(report) ? (
                <Table
                  columns={REALIZED_COLUMNS}
                  rows={realizedRows(report.realized)}
                  rowKey={(row) => row.kind}
                />
              ) : (
                <p className="text-[13px] text-muted">Sem vendas neste mês.</p>
              )}
            </div>
          </Panel>
          <CarryPanel carry={report.carry} />
          <p data-testid="tax-grain-caption" className="text-[11.5px] text-faint">
            Impostos é leitura mensal: a apuração existe por mês, então o mês-âncora escolhe o
            detalhe e a janela do rail só decide quais meses aparecem na lista ao lado.
          </p>
        </div>

        <aside className="space-y-4">
          <Panel
            title="Apurações na janela"
            action={
              <span className="font-mono text-num text-faint">{windowRangeLabel(rail.range)}</span>
            }
          >
            <div data-testid="tax-apuracoes-list">
              <Table
                columns={apuracaoColumns(anchor)}
                rows={apuracoes}
                rowKey={(row) => row.period}
                rowId={(row) => `tax-apuracao-${row.period}`}
                rowClassName={(row) => (row.period === anchor ? "bg-action-bg" : undefined)}
                onRowClick={(row) => rail.setAnchor(row.period)}
              />
            </div>
          </Panel>
          <Panel title="O que explica o DARF">
            <DarfExplanation tax={report} />
          </Panel>
          <PendenciasPanel tax={report} />
        </aside>
      </div>

      {!isOpenMonth ? (
        <Panel title="Harvest">
          <p className="text-[13px] text-muted">
            O planejador de harvest só existe para o mês aberto; este é um mês histórico.
          </p>
          {openPeriod !== null && (
            <Button className="mt-2" onClick={() => rail.setAnchor(openPeriod)}>
              Ir para o mês aberto · {monthPeriodLabel(openPeriod)}
            </Button>
          )}
        </Panel>
      ) : harvest.isError ? (
        <Panel title="Harvest">
          <p className="text-[13px] text-return-down">{harvest.error.message}</p>
        </Panel>
      ) : harvest.data ? (
        <HarvestPanel harvest={harvest.data} />
      ) : (
        <Panel title="Harvest">
          <p className="text-[13px] text-muted">Calculando as sugestões de harvest…</p>
        </Panel>
      )}
    </section>
  );
}

function TaxStrip({ report }: { report: StockTaxReport }) {
  const { meter } = report;

  return (
    <div className="mb-4 grid grid-cols-2 gap-3 lg:grid-cols-3 xl:grid-cols-6">
      <Kpi
        testId="tax-gross-sales-kpi"
        label="Vendas de ações"
        value={formatCurrency(meter.gross_sales)}
        hint={meterLabel(meter)}
        tone={meter.exceeded ? "attention" : "default"}
      />
      <Kpi
        testId="tax-realized-gain-kpi"
        label="Ganho realizado"
        value={formatCurrency(meter.realized_gain)}
        hint="swing trade"
      />
      <Kpi
        testId="tax-taxable-gain-kpi"
        label="Ganho tributável"
        value={formatCurrency(meter.taxable_gain)}
        hint="após prejuízo acumulado"
      />
      <Kpi
        testId="tax-darf-kpi"
        label="DARF"
        value={formatCurrency(meter.tax)}
        hint={`vence ${darfDeadlineLabel(report)}`}
        tone="attention"
      />
      <Kpi
        testId="tax-carry-swing"
        label="Prejuízo a compensar"
        value={carryAmount(carryClosing(report, "SWING"))}
        hint="swing · saldo final"
      />
      <Kpi
        testId="tax-carry-fii"
        label="FII"
        value={carryAmount(carryClosing(report, "FII"))}
        hint="prejuízo acumulado"
      />
    </div>
  );
}

function apuracaoColumns(anchor: string): Column<TaxApuracaoRow>[] {
  return [
    {
      key: "period",
      header: "Mês",
      render: (row) => (
        <span className="flex items-center gap-2">
          <span className="font-mono text-[12px] text-text">{row.label}</span>
          {row.period === anchor && (
            <span className="font-mono text-[9px] tracking-[.06em] text-action uppercase">
              âncora
            </span>
          )}
        </span>
      ),
    },
    {
      key: "state",
      header: "Estado",
      render: (row) =>
        row.chip ? <Chip tone={row.chip.tone}>{row.chip.label}</Chip> : <span>—</span>,
    },
  ];
}

function MeterPanel({ tax }: { tax: StockTaxReport }) {
  const { meter } = tax;

  return (
    <Panel title={`Vendas de ações × teto mensal · ${monthPeriodLabel(tax.period)}`}>
      <div data-testid="tax-meter">
        <div className="flex flex-wrap items-baseline gap-3">
          <span className="font-mono text-[22px] text-text">
            {formatCurrency(meter.gross_sales)}
          </span>
          <span className="text-[12.5px] text-muted">
            de {formatCurrency(meter.exemption_limit)} vendidos no mês
          </span>
          <Chip tone={meter.exceeded ? "rose" : "green"}>{meterLabel(meter)}</Chip>
        </div>
        <div className="mt-3 h-2 overflow-hidden rounded-full bg-panel-3">
          <div
            className={`h-full ${meter.exceeded ? "bg-return-down" : "bg-return-up"}`}
            style={{ width: `${meterProgress(meter)}%` }}
          />
        </div>
        <div className="mt-2 flex flex-wrap justify-between gap-2 text-[12px] text-muted">
          <span>
            Ganho tributável{" "}
            <b className="font-mono text-text">{formatCurrency(meter.taxable_gain)}</b>
          </span>
          <span>
            IR estimado <b className="font-mono text-attention">{formatCurrency(meter.tax)}</b>
          </span>
        </div>
      </div>
    </Panel>
  );
}

function DarfExplanation({ tax }: { tax: StockTaxReport }) {
  const { meter } = tax;
  const fii = carryClosing(tax, "FII");

  return (
    <div className="space-y-2 text-[12.5px] text-muted">
      <p>
        {meter.exceeded ? (
          <>
            Vendas de ações de{" "}
            <b className="font-mono text-text">{formatCurrency(meter.gross_sales)}</b> cruzaram a
            isenção de {formatCurrency(meter.exemption_limit)}. Todo o ganho do mês vira tributável
            depois de aplicar os prejuízos acumulados.
          </>
        ) : (
          <>
            As vendas de ações de{" "}
            <b className="font-mono text-text">{formatCurrency(meter.gross_sales)}</b> ficaram
            dentro da isenção de {formatCurrency(meter.exemption_limit)}; o mês não gera DARF.
          </>
        )}
      </p>
      <p>
        <b className="font-mono text-text">{formatCurrency(meter.tax)}</b> = {meter.rate}% sobre{" "}
        {formatCurrency(meter.taxable_gain)}, vencimento em {darfDeadlineLabel(tax)}.
      </p>
      <p>
        As vendas de FII ficam fora do medidor de ações; o saldo de prejuízo de FII fecha em{" "}
        {carryAmount(fii)}.
      </p>
      {darfIsOverdue(tax, todayIso()) && (
        <p className="text-return-down">O prazo desta DARF já passou.</p>
      )}
    </div>
  );
}

function PendenciasPanel({ tax }: { tax: StockTaxReport }) {
  const empty =
    tax.pending_gaps.length === 0 && tax.unclassified.length === 0 && tax.day_trades.length === 0;

  return (
    <Panel title="Pendências">
      {empty ? (
        <p className="text-[13px] text-muted">Sem pendências para esta apuração.</p>
      ) : (
        <div data-testid="tax-pendencias" className="space-y-3">
          {tax.pending_gaps.length > 0 && (
            <PendenciaSection
              title="Resultado pendente"
              hint="Venda sem preço declarado: o resultado fica pendente até a lacuna ser precificada e nunca entra no imposto."
              items={tax.pending_gaps.map((gap) => ({
                key: String(gap.id),
                text: `${gap.ticker} · ${formatIsoDate(gap.date)} · ${formatNumber(gap.quantity)} un`,
              }))}
            />
          )}
          {tax.unclassified.length > 0 && (
            <PendenciaSection
              title="Sem classe"
              hint="Tickers sem classe: as vendas ficam fora do imposto até a classificação."
              items={tax.unclassified.map((ticker) => ({
                key: ticker.ticker,
                text: `${ticker.ticker} · ${unclassifiedStatusLabel(ticker.status)}`,
              }))}
            />
          )}
          {tax.day_trades.length > 0 && (
            <PendenciaSection
              title="Day trade"
              hint="Compra e venda no mesmo dia: aviso apenas; o app não calcula imposto de day trade."
              items={tax.day_trades.map((warning) => ({
                key: `${warning.ticker}-${warning.date}-${warning.institution_id}`,
                text: `${warning.ticker} · ${formatIsoDate(warning.date)} · ${formatNumber(warning.quantity)} un`,
              }))}
            />
          )}
        </div>
      )}
    </Panel>
  );
}

function PendenciaSection({
  title,
  hint,
  items,
}: {
  title: string;
  hint: string;
  items: { key: string; text: string }[];
}) {
  return (
    <div>
      <h3 className="mb-1 font-mono text-label tracking-[.1em] text-faint uppercase">{title}</h3>
      <p className="text-[11.5px] text-faint">{hint}</p>
      <ul className="mt-1 space-y-1 font-mono text-[11.5px] text-text">
        {items.map((item) => (
          <li key={item.key}>{item.text}</li>
        ))}
      </ul>
    </div>
  );
}

function CarryPanel({ carry }: { carry: CarryBucket[] }) {
  const unavailable = carry.some((bucket) => !bucket.available);

  return (
    <Panel title="Compensação">
      <div data-testid="tax-carry">
        <Table columns={CARRY_COLUMNS} rows={carry} rowKey={(row) => row.kind} />
      </div>
      {unavailable && (
        <p className="pt-2 text-[11.5px] text-faint">
          Sem base antes da adoção — os baldes sem histórico anterior aparecem como "—".
        </p>
      )}
    </Panel>
  );
}

function HarvestPanel({ harvest }: { harvest: HarvestReport }) {
  return (
    <div className="space-y-4">
      <div>
        <h2 className="mb-2 font-mono text-label font-semibold tracking-[.13em] text-muted uppercase">
          Harvest · step-up
        </h2>
        <Panel>
          <div className="mb-3 flex flex-wrap gap-4 text-[12px] text-muted">
            <span>
              Folga do teto{" "}
              <b className="font-mono text-text">{formatCurrency(harvest.headroom)}</b>
            </span>
            <span>
              Perda mínima{" "}
              <b className="font-mono text-text">{formatCurrency(harvest.minimum_loss)}</b>
            </span>
          </div>
          <p className="mb-3 text-[11.5px] text-faint">
            Recompra no próximo dia útil (D+1). O app não conhece feriados da B3 — confira o
            calendário antes de executar.
          </p>

          {harvest.suggestions.length === 0 ? (
            <p className="text-[13px] text-muted">
              Nenhuma sugestão: sem posições de ações com prejuízo, folga no teto ou acima da perda
              mínima.
            </p>
          ) : (
            <div className="divide-y divide-line-soft">
              {harvest.suggestions.map((proposal) => (
                <HarvestRow key={proposal.ticker} proposal={proposal} />
              ))}
            </div>
          )}

          {harvest.ignored.length > 0 && (
            <div className="mt-3 border-t border-line-soft pt-2">
              <h3 className="mb-1 font-mono text-label tracking-[.1em] text-faint uppercase">
                Ignorados ({harvest.ignored.length})
              </h3>
              <div className="divide-y divide-line-soft">
                {harvest.ignored.map((ignored) => (
                  <IgnoredRow key={ignored.ticker} ignored={ignored} />
                ))}
              </div>
            </div>
          )}

          {harvest.pairs.length > 0 && (
            <div className="mt-3 border-t border-line-soft pt-2">
              <h3 className="mb-1 font-mono text-label tracking-[.1em] text-faint uppercase">
                Pares aceitos ({harvest.pairs.length})
              </h3>
              <Table columns={PAIR_COLUMNS} rows={harvest.pairs} rowKey={(pair) => pair.id} />
            </div>
          )}
        </Panel>
      </div>

      <FiiPanel report={harvest.fii_report} />
    </div>
  );
}

function HarvestRow({ proposal }: { proposal: HarvestProposal }) {
  const accept = useAcceptHarvestPair();
  const optOut = useSetHarvestOptOut();
  // The form is an editable draft seeded on the click that opens it, not a mirror of the proposal:
  // a refetch after another ticker's toggle must not overwrite the owner's edits.
  const [form, setForm] = useState<HarvestPairFormState | null>(null);
  const creation = form ? toHarvestPairCreation(proposal, form) : null;

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!creation) return;
    accept.mutate(creation, { onSuccess: () => setForm(null) });
  }

  function patch(change: Partial<HarvestPairFormState>) {
    setForm((current) => (current ? { ...current, ...change } : current));
  }

  return (
    <div className="py-2.5" data-testid={`harvest-${proposal.ticker}`}>
      <div className="flex flex-wrap items-center gap-2">
        <span className="font-mono text-[12.5px] font-semibold text-text">{proposal.ticker}</span>
        {proposal.strategy && <Chip>{proposal.strategy}</Chip>}
        {proposal.neutro && <Chip tone="blue">NEUTRO</Chip>}
        <span className="text-[12px] text-muted">
          {formatNumber(proposal.quantity)} un · PM {formatCurrency(proposal.average_price)} →{" "}
          {formatCurrency(proposal.price)}
        </span>
        <span className="ml-auto font-mono text-return-down">−{formatCurrency(proposal.loss)}</span>
      </div>
      <div className="mt-1 flex flex-wrap items-center gap-3 text-[11.5px] text-faint">
        <span>
          Venda {formatIsoDate(proposal.sell.date)} · {formatCurrency(proposal.sell.amount)}
        </span>
        <span>
          Recompra {formatIsoDate(proposal.rebuy.date)} (D+1) ·{" "}
          {formatCurrency(proposal.rebuy.amount)}
        </span>
      </div>

      <div className="mt-2 flex flex-wrap gap-2">
        <Button onClick={() => setForm((current) => (current ? null : harvestPairForm(proposal)))}>
          {form ? "Fechar" : "Ajustar par"}
        </Button>
        <Button
          variant="ghost"
          data-testid={`harvest-opt-out-${proposal.ticker}`}
          onClick={() => optOut.mutate({ ticker: proposal.ticker, optedOut: true })}
          disabled={optOut.isPending}
        >
          Ignorar
        </Button>
      </div>

      {form && (
        <form onSubmit={handleSubmit} className="mt-2 space-y-2" data-testid="harvest-pair-form">
          <div className="flex flex-wrap gap-2">
            <Field label="Venda em">
              <Input
                type="date"
                value={form.sellDate}
                onChange={(event) => patch({ sellDate: event.target.value })}
              />
            </Field>
            <Field label="Quantidade">
              <Input
                type="number"
                min="1"
                step="1"
                value={form.sellQuantity}
                onChange={(event) => patch({ sellQuantity: event.target.value })}
              />
            </Field>
            <Field label="Preço">
              <Input
                type="number"
                min="0"
                step="0.01"
                value={form.sellPrice}
                onChange={(event) => patch({ sellPrice: event.target.value })}
              />
            </Field>
            <Field label="Recompra em">
              <Input
                type="date"
                value={form.rebuyDate}
                onChange={(event) => patch({ rebuyDate: event.target.value })}
              />
            </Field>
            <Field label="Quantidade">
              <Input
                type="number"
                min="1"
                step="1"
                value={form.rebuyQuantity}
                onChange={(event) => patch({ rebuyQuantity: event.target.value })}
              />
            </Field>
            <Field label="Preço">
              <Input
                type="number"
                min="0"
                step="0.01"
                value={form.rebuyPrice}
                onChange={(event) => patch({ rebuyPrice: event.target.value })}
              />
            </Field>
          </div>
          {form.rebuyDate <= form.sellDate && (
            <p className="text-[11.5px] text-return-down">
              A recompra precisa ser em um dia posterior à venda — nunca no mesmo dia.
            </p>
          )}
          {accept.error && <p className="text-[12px] text-return-down">{accept.error.message}</p>}
          <Button type="submit" variant="primary" disabled={!creation || accept.isPending}>
            {accept.isPending ? "Aceitando…" : "Aceitar par"}
          </Button>
        </form>
      )}
    </div>
  );
}

function IgnoredRow({ ignored }: { ignored: HarvestIgnored }) {
  const optOut = useSetHarvestOptOut();

  return (
    <div className="flex flex-wrap items-center gap-2 py-2">
      <span className="font-mono text-[12.5px] font-semibold text-text">{ignored.ticker}</span>
      {ignored.strategy && <Chip>{ignored.strategy}</Chip>}
      {ignored.neutro && <Chip tone="blue">NEUTRO</Chip>}
      <span className="text-[12px] text-muted">Perda potencial {formatCurrency(ignored.loss)}</span>
      <Button
        variant="ghost"
        className="ml-auto"
        data-testid={`harvest-opt-in-${ignored.ticker}`}
        onClick={() => optOut.mutate({ ticker: ignored.ticker, optedOut: false })}
        disabled={optOut.isPending}
      >
        Reativar
      </Button>
    </div>
  );
}

function FiiPanel({ report }: { report: FiiHarvestReport[] }) {
  return (
    <div>
      <h2 className="mb-2 font-mono text-label font-semibold tracking-[.13em] text-muted uppercase">
        FIIs · somente relatório
      </h2>
      <Panel>
        <p className="mb-3 text-[12px] text-muted">
          Fundos imobiliários não entram na isenção de R$ 20 mil: o ganho é tributado a 20% e o
          prejuízo só compensa ganho de FII. O app não propõe par para FIIs.
        </p>
        {report.length === 0 ? (
          <p className="text-[13px] text-muted">Nenhum FII na carteira.</p>
        ) : (
          <Table columns={FII_COLUMNS} rows={report} rowKey={(row) => row.ticker} />
        )}
      </Panel>
    </div>
  );
}

const REALIZED_COLUMNS: Column<RealizedRow>[] = [
  {
    key: "kind",
    header: "Balde",
    render: (row) => <span className="font-mono text-[12px]">{row.kindLabel}</span>,
  },
  {
    key: "gross",
    header: "Vendas",
    align: "right",
    render: (row) => formatCurrency(row.grossSales),
  },
  {
    key: "gains",
    header: "Ganhos",
    align: "right",
    render: (row) => <span className="text-return-up">{formatCurrency(row.gains)}</span>,
  },
  {
    key: "losses",
    header: "Prejuízos",
    align: "right",
    render: (row) => <span className="text-return-down">{formatCurrency(row.losses)}</span>,
  },
  {
    key: "result",
    header: "Resultado",
    align: "right",
    render: (row) => (
      <span className={row.result < 0 ? "text-return-down" : "text-text"}>
        {formatCurrency(row.result)}
      </span>
    ),
  },
];

const CARRY_COLUMNS: Column<CarryBucket>[] = [
  {
    key: "kind",
    header: "Balde",
    render: (row) => <span className="font-mono text-[12px]">{realizedKindLabel(row.kind)}</span>,
  },
  {
    key: "opening",
    header: "Saldo inicial",
    align: "right",
    render: (row) => carryAmount(row.opening_balance),
  },
  {
    key: "banked",
    header: "Prejuízo do mês",
    align: "right",
    render: (row) => <span className="text-return-down">{carryAmount(row.loss_banked)}</span>,
  },
  {
    key: "applied",
    header: "Compensado",
    align: "right",
    render: (row) => carryAmount(row.loss_applied),
  },
  {
    key: "closing",
    header: "Saldo final",
    align: "right",
    render: (row) => (
      <span className="font-mono text-text">{carryAmount(row.closing_balance)}</span>
    ),
  },
];

const PAIR_COLUMNS: Column<HarvestPair>[] = [
  {
    key: "ticker",
    header: "Ticker",
    render: (row) => <span className="font-mono font-semibold">{row.ticker}</span>,
  },
  { key: "strategy", header: "Slice", render: (row) => row.strategy ?? "—" },
  {
    key: "sell",
    header: "Venda",
    render: (row) => (
      <span className="font-mono text-[11.5px]">{formatIsoDate(row.sell.date)}</span>
    ),
  },
  {
    key: "rebuy",
    header: "Recompra",
    render: (row) => (
      <span className="font-mono text-[11.5px]">{formatIsoDate(row.rebuy.date)}</span>
    ),
  },
  {
    key: "quantity",
    header: "Quantidade",
    align: "right",
    render: (row) => formatNumber(row.sell.quantity),
  },
  {
    key: "loss",
    header: "Perda",
    align: "right",
    render: (row) => <span className="text-return-down">{formatCurrency(row.loss)}</span>,
  },
];

const FII_COLUMNS: Column<FiiHarvestReport>[] = [
  {
    key: "ticker",
    header: "Ticker",
    render: (row) => <span className="font-mono font-semibold text-violet">{row.ticker}</span>,
  },
  { key: "quantity", header: "Qtd.", align: "right", render: (row) => formatNumber(row.quantity) },
  {
    key: "price",
    header: "Preço",
    align: "right",
    render: (row) => formatCurrency(row.price),
  },
  {
    key: "average",
    header: "PM",
    align: "right",
    render: (row) => formatCurrency(row.average_price),
  },
  {
    key: "result",
    header: "Resultado",
    align: "right",
    render: (row) => (
      <span className={row.unrealized_result < 0 ? "text-return-down" : "text-return-up"}>
        {formatCurrency(row.unrealized_result)}
      </span>
    ),
  },
  {
    key: "tax",
    header: "IR estimado (20%)",
    align: "right",
    render: (row) => <span className="text-attention">{formatCurrency(row.estimated_tax)}</span>,
  },
];
