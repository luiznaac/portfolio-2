import { type FormEvent, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useBondPositions, useBonds, useConsolidate, useCreateBondOrder } from "../api/queries.ts";
import type { BondOrderType } from "../api/types.ts";
import { Panel } from "../components/Panel.tsx";
import { PositionChart } from "../components/PositionChart.tsx";
import { PositionsTable } from "../components/PositionsTable.tsx";
import { bondOrderTypeLabel } from "../i18n/bondOrderType.ts";
import { formatDate } from "../lib/format.ts";
import { formatBRL } from "../lib/money.ts";
import { lastPosition } from "../lib/positions.ts";

const ORDER_TYPES: BondOrderType[] = ["BUY", "SELL", "FULL_REDEMPTION", "MATURITY"];
const NEEDS_AMOUNT = new Set<BondOrderType>(["BUY", "SELL"]);

export function BondPage() {
  const id = Number(useParams().id);
  const bonds = useBonds();
  const positions = useBondPositions(id);
  const consolidate = useConsolidate();
  const bond = bonds.data?.find((b) => b.id === id);

  if (bonds.isLoading) return <p className="text-slate-400">Carregando…</p>;
  if (!bond)
    return (
      <p className="text-slate-400">
        Título não encontrado.{" "}
        <Link to="/" className="text-accent-500 hover:underline">
          Voltar
        </Link>
      </p>
    );

  const last = lastPosition(positions.data ?? []);

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold text-slate-100">{bond.name}</h1>
          <p className="mt-0.5 text-sm text-slate-400">
            {bond.index_id ? `Pós-fixado · ${bond.index_id}` : "Prefixado"} · taxa {bond.value} ·
            vence {formatDate(bond.maturity_date)}
          </p>
        </div>
        <button
          type="button"
          onClick={() => consolidate.mutate({ kind: "bond", id })}
          disabled={consolidate.isPending}
          className="rounded-md bg-accent-500 px-3 py-1.5 text-sm font-medium text-slate-950 transition-colors hover:bg-accent-600 disabled:opacity-50"
        >
          {consolidate.isPending ? "Consolidando…" : "Consolidar"}
        </button>
      </div>

      {last && (
        <p className="text-sm text-slate-300">
          Posição em {formatDate(last.date)}:{" "}
          <span className="tabular-nums text-slate-100">
            {formatBRL(last.principal + last.yield - last.taxes)}
          </span>{" "}
          <span className="text-yield">(+{formatBRL(last.yield)})</span>{" "}
          <span className="text-tax">(−{formatBRL(last.taxes)} imp.)</span>
        </p>
      )}

      <Panel title="Valor líquido ao longo do tempo">
        <PositionChart positions={positions.data ?? []} />
      </Panel>

      <Panel title="Novo lançamento">
        <OrderForm bondId={id} />
      </Panel>

      <Panel title="Posições">
        <PositionsTable positions={positions.data ?? []} />
      </Panel>
    </div>
  );
}

function OrderForm({ bondId }: { bondId: number }) {
  const mutation = useCreateBondOrder();
  const [type, setType] = useState<BondOrderType>("BUY");
  const [date, setDate] = useState("");
  const [amount, setAmount] = useState("");

  const submit = (e: FormEvent) => {
    e.preventDefault();
    mutation.mutate({
      bond_id: bondId,
      type,
      date,
      amount: NEEDS_AMOUNT.has(type) ? Number(amount) : undefined,
    });
  };

  return (
    <form onSubmit={submit} className="flex flex-wrap items-end gap-3">
      <label className="text-sm">
        <span className="mb-1 block text-xs text-slate-500">Tipo</span>
        <select
          value={type}
          onChange={(e) => setType(e.target.value as BondOrderType)}
          className="rounded-md border border-white/10 bg-slate-900 px-3 py-2 text-slate-100 outline-none focus:border-accent-500"
        >
          {ORDER_TYPES.map((t) => (
            <option key={t} value={t}>
              {bondOrderTypeLabel(t)}
            </option>
          ))}
        </select>
      </label>
      <label className="text-sm">
        <span className="mb-1 block text-xs text-slate-500">Data</span>
        <input
          type="date"
          required
          value={date}
          onChange={(e) => setDate(e.target.value)}
          className="rounded-md border border-white/10 bg-slate-900 px-3 py-2 text-slate-100 outline-none focus:border-accent-500"
        />
      </label>
      {NEEDS_AMOUNT.has(type) && (
        <label className="text-sm">
          <span className="mb-1 block text-xs text-slate-500">Valor (R$)</span>
          <input
            type="number"
            step="0.01"
            required
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            className="w-36 rounded-md border border-white/10 bg-slate-900 px-3 py-2 text-slate-100 outline-none focus:border-accent-500"
          />
        </label>
      )}
      <button
        type="submit"
        disabled={mutation.isPending}
        className="rounded-md bg-accent-500 px-3 py-2 text-sm font-medium text-slate-950 transition-colors hover:bg-accent-600 disabled:opacity-50"
      >
        {mutation.isPending ? "Salvando…" : "Adicionar"}
      </button>
      {mutation.isError && <p className="w-full text-sm text-tax">{String(mutation.error)}</p>}
      {mutation.isSuccess && <p className="w-full text-sm text-yield">Lançamento criado.</p>}
    </form>
  );
}
