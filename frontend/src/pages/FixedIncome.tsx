import { type FormEvent, useState } from "react";
import { useBonds, useCheckingAccounts, useCreateFixedIncomeProduct } from "../api/queries.ts";
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
  MATURITY_DURATIONS,
  emptyFixedIncomeForm,
  fixedIncomeRows,
  toProductInput,
  withKind,
} from "../lib/fixedIncome.ts";
import { formatNumber } from "../lib/format.ts";

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
];

// Renda fixa: the product list (títulos and contas in one table) and the lançamentos form that
// creates either kind. Sub-class is the backend's derivation, shown read-only.
export function FixedIncome() {
  const bonds = useBonds();
  const accounts = useCheckingAccounts();
  const create = useCreateFixedIncomeProduct();
  const [form, setForm] = useState<FixedIncomeFormState>(emptyFixedIncomeForm());

  const rows = fixedIncomeRows(bonds.data ?? [], accounts.data ?? []);
  const input = toProductInput(form);
  const isLoading = bonds.isLoading || accounts.isLoading;

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!input) return;
    create.mutate(input, { onSuccess: () => setForm(emptyFixedIncomeForm(form.kind)) });
  }

  return (
    <section>
      <h1 className="mb-4 font-cond text-[24px] font-bold tracking-[.005em]">Renda fixa</h1>

      <div className="mb-4">
        <Panel title="Produtos">
          {isLoading ? (
            <p className="text-[13.5px] text-ink-dim">Carregando…</p>
          ) : rows.length === 0 ? (
            <p className="text-[13.5px] text-ink-dim">Nenhum produto cadastrado.</p>
          ) : (
            <Table columns={COLUMNS} rows={rows} rowKey={(row) => row.key} />
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
