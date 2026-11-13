import { type FormEvent, useState } from "react";
import {
  useBonds,
  useCheckingAccounts,
  useCreateFixedIncomeProduct,
  useFixedIncomePositions,
} from "../api/queries.ts";
import type { IndexId } from "../api/types.ts";
import { Badge } from "../components/Badge.tsx";
import { Button } from "../components/Button.tsx";
import { Chip, type ChipTone } from "../components/Chip.tsx";
import { Panel } from "../components/Panel.tsx";
import { type Column, Table } from "../components/Table.tsx";
import {
  type FixedIncomeFormState,
  type FixedIncomeKind,
  type FixedIncomeRow,
  type FixedIncomeTotal,
  MATURITY_DURATIONS,
  emptyFixedIncomeForm,
  fixedIncomeProductRefs,
  fixedIncomeRows,
  fixedIncomeTotal,
  positionsByProductKey,
  toProductInput,
  withKind,
} from "../lib/fixedIncome.ts";
import { formatCurrency, formatNumber } from "../lib/format.ts";

const FIELD = "rounded-sm border border-line bg-surface-2 px-2 py-1.5 text-[13px] text-ink";

const KINDS: { value: FixedIncomeKind; label: string }[] = [
  { value: "FIXED_BOND", label: "Título pré-fixado" },
  { value: "FLOATING_BOND", label: "Título pós-fixado" },
  { value: "ACCOUNT", label: "Conta" },
];

const INDEXES: IndexId[] = ["CDI", "SELIC", "IPCA"];

// The reference colours the Indexador chip by series.
const INDEX_TONES: Record<string, ChipTone> = {
  CDI: "blue",
  SELIC: "violet",
  IPCA: "rose",
  PRÉ: "neutral",
};

const COLUMNS: Column<FixedIncomeRow>[] = [
  { key: "name", header: "Produto", render: (row) => row.name },
  { key: "kind", header: "Tipo", render: (row) => <Badge>{row.kindLabel}</Badge> },
  {
    key: "index",
    header: "Indexador",
    render: (row) => <Chip tone={INDEX_TONES[row.indexLabel]}>{row.indexLabel}</Chip>,
  },
  { key: "sub_class", header: "Sub-classe", render: (row) => row.subClassLabel },
  {
    key: "rate",
    header: "Taxa",
    align: "right",
    render: (row) => `${formatNumber(row.rate)}%`,
  },
  { key: "maturity", header: "Vencimento", render: (row) => row.maturity },
  {
    key: "value",
    header: "Valor",
    align: "right",
    render: (row) =>
      row.value === undefined ? <span className="text-ink-dim">—</span> : formatCurrency(row.value),
  },
];

const FOOTER_CELL = "border-t border-line px-2.5 py-2";

// The total covers the priced products; a product with no consolidated position keeps it partial
// rather than letting it read as a real zero.
function TotalRow({ total }: { total: FixedIncomeTotal }) {
  return (
    <tr data-testid="fixed-income-total" className="bg-panel-2 font-mono text-num">
      <td
        colSpan={COLUMNS.length - 1}
        className={`${FOOTER_CELL} text-left text-[10px] tracking-[.08em] uppercase`}
      >
        Total{total.partial ? " · parcial" : ""}
      </td>
      <td data-testid="fixed-income-total-value" className={`${FOOTER_CELL} text-right`}>
        {total.value === undefined ? "—" : formatCurrency(total.value)}
      </td>
    </tr>
  );
}

// Ativos · Renda fixa (#380): the product list (títulos and contas in one table) with each product's
// accrual value and a total, plus the lançamentos form that creates either kind. Sub-class is the
// backend's derivation, shown read-only. RF stays its own managed list — it never enters through the
// import assistant.
export function FixedIncome() {
  const bonds = useBonds();
  const accounts = useCheckingAccounts();
  const create = useCreateFixedIncomeProduct();
  const [form, setForm] = useState<FixedIncomeFormState>(emptyFixedIncomeForm());

  const bondList = bonds.data ?? [];
  const accountList = accounts.data ?? [];
  const products = fixedIncomeProductRefs(bondList, accountList);
  const positionReads = useFixedIncomePositions(products);
  const positionsByKey = positionsByProductKey(
    products,
    positionReads.map((read) => read.data),
  );
  const rows = fixedIncomeRows(bondList, accountList, positionsByKey);
  const total = fixedIncomeTotal(rows);
  const input = toProductInput(form);
  // The values are part of the table's answer, so the panel waits for the position reads too rather
  // than flashing an unknown value and a partial total.
  const isLoading =
    bonds.isLoading || accounts.isLoading || positionReads.some((read) => read.isLoading);

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!input) return;
    create.mutate(input, { onSuccess: () => setForm(emptyFixedIncomeForm(form.kind)) });
  }

  return (
    <section>
      <div className="mb-4">
        <Panel title="Produtos">
          {isLoading ? (
            <p className="text-[13.5px] text-ink-dim">Carregando…</p>
          ) : rows.length === 0 ? (
            <p className="text-[13.5px] text-ink-dim">Nenhum produto cadastrado.</p>
          ) : (
            <Table
              columns={COLUMNS}
              rows={rows}
              rowKey={(row) => row.key}
              footer={<TotalRow total={total} />}
            />
          )}
        </Panel>
      </div>

      <Panel title="Novo produto">
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="flex flex-wrap gap-3">
            <label className="flex flex-col gap-1 text-[12.5px] text-ink-dim">
              Tipo
              <select
                value={form.kind}
                onChange={(event) =>
                  setForm((current) => withKind(current, event.target.value as FixedIncomeKind))
                }
                className={FIELD}
              >
                {KINDS.map((kind) => (
                  <option key={kind.value} value={kind.value}>
                    {kind.label}
                  </option>
                ))}
              </select>
            </label>

            <label className="flex flex-col gap-1 text-[12.5px] text-ink-dim">
              Nome
              <input
                type="text"
                placeholder="Tesouro Selic 2031"
                value={form.name}
                onChange={(event) =>
                  setForm((current) => ({ ...current, name: event.target.value }))
                }
                className={FIELD}
              />
            </label>

            <label className="flex flex-col gap-1 text-[12.5px] text-ink-dim">
              Taxa (%)
              <input
                type="number"
                step="0.01"
                placeholder="0,00"
                value={form.rate}
                onChange={(event) =>
                  setForm((current) => ({ ...current, rate: event.target.value }))
                }
                className={FIELD}
              />
            </label>

            {form.kind !== "FIXED_BOND" && (
              <label className="flex flex-col gap-1 text-[12.5px] text-ink-dim">
                Indexador
                <select
                  value={form.index}
                  onChange={(event) =>
                    setForm((current) => ({ ...current, index: event.target.value as IndexId }))
                  }
                  className={FIELD}
                >
                  {INDEXES.map((index) => (
                    <option key={index} value={index}>
                      {index}
                    </option>
                  ))}
                </select>
              </label>
            )}

            {form.kind !== "ACCOUNT" ? (
              <label className="flex flex-col gap-1 text-[12.5px] text-ink-dim">
                Vencimento
                <input
                  type="date"
                  value={form.maturityDate}
                  onChange={(event) =>
                    setForm((current) => ({ ...current, maturityDate: event.target.value }))
                  }
                  className={FIELD}
                />
              </label>
            ) : (
              <label className="flex flex-col gap-1 text-[12.5px] text-ink-dim">
                Prazo
                <select
                  value={form.maturityDuration}
                  onChange={(event) =>
                    setForm((current) => ({ ...current, maturityDuration: event.target.value }))
                  }
                  className={FIELD}
                >
                  {MATURITY_DURATIONS.map((duration) => (
                    <option key={duration.value} value={duration.value}>
                      {duration.label}
                    </option>
                  ))}
                </select>
              </label>
            )}
          </div>

          {create.error && <p className="text-[12.5px] text-tax">{create.error.message}</p>}

          <Button type="submit" variant="primary" disabled={!input || create.isPending}>
            {create.isPending ? "Criando…" : "Criar produto"}
          </Button>
        </form>
      </Panel>
    </section>
  );
}
