import { type FormEvent, useState } from "react";
import { Link } from "react-router-dom";
import {
  useAcceptHarvestPair,
  useHarvest,
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
import { Panel } from "../components/Panel.tsx";
import { type Column, Table } from "../components/Table.tsx";
import { formatCurrency, formatIsoDate, formatNumber } from "../lib/format.ts";
import { monthPeriodLabel } from "../lib/month.ts";
import {
  type HarvestPairFormState,
  type RealizedRow,
  darfDeadlineLabel,
  harvestPairForm,
  meterLabel,
  meterProgress,
  realizedKindLabel,
  realizedRows,
  toHarvestPairCreation,
} from "../lib/taxes.ts";

// Impostos (issue #173): the month's tax ledger — the R$20k stock-sale meter, the realized result
// per kind, the three carry buckets and the DARF 6015 deadline — plus the harvest pass: loss pairs
// sold D and rebought D+1, sized to the remaining headroom, with a per-ticker opt-out.
export function Taxes() {
  const tax = useStockTax();
  const harvest = useHarvest();

  return (
    <section>
      <div className="mb-4">
        <h1 className="font-cond text-[24px] font-bold tracking-[.005em]">Impostos</h1>
        <p className="text-[13px] text-ink-dim">
          Livro mensal de ganho de capital, ações e FIIs em baldes separados. Planejador de step-up
          para preencher a isenção.
        </p>
      </div>

      {tax.isLoading ? (
        <p className="text-[13.5px] text-ink-dim">Carregando…</p>
      ) : tax.isError ? (
        <p className="text-[13.5px] text-tax">{tax.error.message}</p>
      ) : tax.data ? (
        <TaxLedger
          tax={tax.data}
          harvest={harvest.data ?? null}
          harvestError={harvest.isError ? harvest.error.message : null}
        />
      ) : (
        <Panel title="Sem mês aberto">
          <p className="text-[13.5px] text-ink-dim">
            Abra um mês em{" "}
            <Link to="/month" className="text-principal underline">
              Capital e alvos
            </Link>{" "}
            para ver o livro de impostos e o planejador de harvest.
          </p>
        </Panel>
      )}
    </section>
  );
}

function TaxLedger({
  tax,
  harvest,
  harvestError,
}: {
  tax: StockTaxReport;
  harvest: HarvestReport | null;
  harvestError: string | null;
}) {
  return (
    <div data-testid="tax-ledger">
      <div className="mb-4">
        <MeterPanel tax={tax} />
      </div>

      <div className="mb-4 grid items-start gap-3 lg:grid-cols-[1.6fr_1fr]">
        <RealizedPanel tax={tax} />
        <DarfPanel tax={tax} />
      </div>

      <div className="mb-4">
        <CarryPanel carry={tax.carry} />
      </div>

      {harvestError ? (
        <Panel title="Harvest">
          <p className="text-[13px] text-tax">{harvestError}</p>
        </Panel>
      ) : harvest ? (
        <HarvestPanel harvest={harvest} />
      ) : (
        <Panel title="Harvest">
          <p className="text-[13px] text-ink-dim">Calculando as sugestões de harvest…</p>
        </Panel>
      )}
    </div>
  );
}

function MeterPanel({ tax }: { tax: StockTaxReport }) {
  const { meter } = tax;

  return (
    <Panel title="Vendas de ações × teto mensal">
      <div className="flex flex-wrap items-baseline gap-3">
        <span className="font-mono text-[22px] text-ink">{formatCurrency(meter.gross_sales)}</span>
        <span className="text-[12.5px] text-ink-dim">
          de {formatCurrency(meter.exemption_limit)} vendidos no mês
        </span>
        <Chip tone={meter.exceeded ? "rose" : "green"}>{meterLabel(meter)}</Chip>
      </div>
      <div className="mt-3 h-2 overflow-hidden rounded-full bg-surface-3">
        <div
          className={`h-full ${meter.exceeded ? "bg-tax" : "bg-yield"}`}
          style={{ width: `${meterProgress(meter)}%` }}
        />
      </div>
      <div className="mt-2 flex flex-wrap justify-between gap-2 text-[12px] text-ink-dim">
        <span>
          Ganho tributável{" "}
          <b className="font-mono text-ink">{formatCurrency(meter.taxable_gain)}</b>
        </span>
        <span>
          IR estimado <b className="font-mono text-tax">{formatCurrency(meter.tax)}</b>
        </span>
      </div>
    </Panel>
  );
}

function RealizedPanel({ tax }: { tax: StockTaxReport }) {
  const rows = realizedRows(tax.realized);

  return (
    <div>
      <h2 className="mb-2 font-cond text-[13px] font-bold tracking-[.04em] text-ink-dim uppercase">
        Livro mensal
      </h2>
      <Table columns={REALIZED_COLUMNS} rows={rows} rowKey={(row) => row.kind} />
    </div>
  );
}

function DarfPanel({ tax }: { tax: StockTaxReport }) {
  return (
    <Panel title="DARF 6015">
      <div className="font-mono text-[22px] text-tax">{formatCurrency(tax.meter.tax)}</div>
      <dl className="mt-2 space-y-1 text-[12.5px]">
        <div className="flex justify-between gap-3">
          <dt className="text-ink-dim">Competência</dt>
          <dd className="font-mono">{monthPeriodLabel(tax.period)}</dd>
        </div>
        <div className="flex justify-between gap-3">
          <dt className="text-ink-dim">Vencimento</dt>
          <dd className="font-mono">{darfDeadlineLabel(tax)}</dd>
        </div>
      </dl>
      <p className="mt-3 text-[11.5px] text-ink-faint">
        O app não emite a DARF. O vencimento é o último dia útil do mês seguinte; confira feriados
        no calendário da Receita antes de pagar.
      </p>
    </Panel>
  );
}

function CarryPanel({ carry }: { carry: CarryBucket[] }) {
  return (
    <div>
      <h2 className="mb-2 font-cond text-[13px] font-bold tracking-[.04em] text-ink-dim uppercase">
        Baldes de prejuízo
      </h2>
      <Table columns={CARRY_COLUMNS} rows={carry} rowKey={(row) => row.kind} />
    </div>
  );
}

function HarvestPanel({ harvest }: { harvest: HarvestReport }) {
  return (
    <div className="space-y-4">
      <div>
        <h2 className="mb-2 font-cond text-[13px] font-bold tracking-[.04em] text-ink-dim uppercase">
          Harvest · step-up
        </h2>
        <Panel>
          <div className="mb-3 flex flex-wrap gap-4 text-[12px] text-ink-dim">
            <span>
              Folga do teto <b className="font-mono text-ink">{formatCurrency(harvest.headroom)}</b>
            </span>
            <span>
              Perda mínima{" "}
              <b className="font-mono text-ink">{formatCurrency(harvest.minimum_loss)}</b>
            </span>
          </div>
          <p className="mb-3 text-[11.5px] text-ink-faint">
            Recompra no próximo dia útil (D+1). O app não conhece feriados da B3 — confira o
            calendário antes de executar.
          </p>

          {harvest.suggestions.length === 0 ? (
            <p className="text-[13px] text-ink-dim">
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
              <h3 className="mb-1 font-mono text-[10px] tracking-[.1em] text-ink-faint uppercase">
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
              <h3 className="mb-1 font-mono text-[10px] tracking-[.1em] text-ink-faint uppercase">
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
        <span className="font-mono text-[12.5px] font-semibold text-ink">{proposal.ticker}</span>
        {proposal.strategy && <Chip>{proposal.strategy}</Chip>}
        {proposal.neutro && <Chip tone="blue">NEUTRO</Chip>}
        <span className="text-[12px] text-ink-dim">
          {formatNumber(proposal.quantity)} un · PM {formatCurrency(proposal.average_price)} →{" "}
          {formatCurrency(proposal.price)}
        </span>
        <span className="ml-auto font-mono text-tax">−{formatCurrency(proposal.loss)}</span>
      </div>
      <div className="mt-1 flex flex-wrap items-center gap-3 text-[11.5px] text-ink-faint">
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
            <p className="text-[11.5px] text-tax">
              A recompra precisa ser em um dia posterior à venda — nunca no mesmo dia.
            </p>
          )}
          {accept.error && <p className="text-[12px] text-tax">{accept.error.message}</p>}
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
      <span className="font-mono text-[12.5px] font-semibold text-ink">{ignored.ticker}</span>
      {ignored.strategy && <Chip>{ignored.strategy}</Chip>}
      {ignored.neutro && <Chip tone="blue">NEUTRO</Chip>}
      <span className="text-[12px] text-ink-dim">
        Perda potencial {formatCurrency(ignored.loss)}
      </span>
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
      <h2 className="mb-2 font-cond text-[13px] font-bold tracking-[.04em] text-ink-dim uppercase">
        FIIs · somente relatório
      </h2>
      <Panel>
        <p className="mb-3 text-[12px] text-ink-dim">
          Fundos imobiliários não entram na isenção de R$ 20 mil: o ganho é tributado a 20% e o
          prejuízo só compensa ganho de FII. O app não propõe par para FIIs.
        </p>
        {report.length === 0 ? (
          <p className="text-[13px] text-ink-dim">Nenhum FII na carteira.</p>
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
    render: (row) => <span className="text-yield">{formatCurrency(row.gains)}</span>,
  },
  {
    key: "losses",
    header: "Prejuízos",
    align: "right",
    render: (row) => <span className="text-principal">{formatCurrency(row.losses)}</span>,
  },
  {
    key: "result",
    header: "Resultado",
    align: "right",
    render: (row) => (
      <span className={row.result < 0 ? "text-principal" : "text-ink"}>
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
    render: (row) => formatCurrency(row.opening_balance),
  },
  {
    key: "banked",
    header: "Prejuízo do mês",
    align: "right",
    render: (row) => <span className="text-principal">{formatCurrency(row.loss_banked)}</span>,
  },
  {
    key: "applied",
    header: "Compensado",
    align: "right",
    render: (row) => formatCurrency(row.loss_applied),
  },
  {
    key: "closing",
    header: "Saldo final",
    align: "right",
    render: (row) => (
      <span className="font-mono text-ink">{formatCurrency(row.closing_balance)}</span>
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
    render: (row) => <span className="text-tax">{formatCurrency(row.loss)}</span>,
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
      <span className={row.unrealized_result < 0 ? "text-principal" : "text-yield"}>
        {formatCurrency(row.unrealized_result)}
      </span>
    ),
  },
  {
    key: "tax",
    header: "IR estimado (20%)",
    align: "right",
    render: (row) => <span className="text-tax">{formatCurrency(row.estimated_tax)}</span>,
  },
];
