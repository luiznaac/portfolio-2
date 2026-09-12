import { type FormEvent, useState } from "react";
import { Link, useParams } from "react-router-dom";
import {
  useCheckingAccountPositions,
  useCheckingAccounts,
  useConsolidate,
  useMovement,
} from "../api/queries.ts";
import { Panel } from "../components/Panel.tsx";
import { PositionChart } from "../components/PositionChart.tsx";
import { PositionsTable } from "../components/PositionsTable.tsx";
import { formatDate, formatPeriod } from "../lib/format.ts";
import { formatBRL } from "../lib/money.ts";
import { lastPosition } from "../lib/positions.ts";

type MovementKind = "deposit" | "withdraw" | "full-withdraw";

const MOVEMENT_LABELS: Record<MovementKind, string> = {
  deposit: "Depósito",
  withdraw: "Saque",
  "full-withdraw": "Saque total",
};

export function CheckingAccountPage() {
  const id = Number(useParams().id);
  const accounts = useCheckingAccounts();
  const positions = useCheckingAccountPositions(id);
  const consolidate = useConsolidate();
  const account = accounts.data?.find((a) => a.id === id);

  if (accounts.isLoading) return <p className="text-slate-400">Carregando…</p>;
  if (!account)
    return (
      <p className="text-slate-400">
        Conta não encontrada.{" "}
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
          <h1 className="text-xl font-semibold text-slate-100">{account.name}</h1>
          <p className="mt-0.5 text-sm text-slate-400">
            {account.index_id} · {account.value} · carência{" "}
            {formatPeriod(account.maturity_duration)}
          </p>
        </div>
        <button
          type="button"
          onClick={() => consolidate.mutate({ kind: "checking-account", id })}
          disabled={consolidate.isPending}
          className="rounded-md bg-accent-500 px-3 py-1.5 text-sm font-medium text-slate-950 transition-colors hover:bg-accent-600 disabled:opacity-50"
        >
          {consolidate.isPending ? "Consolidando…" : "Consolidar"}
        </button>
      </div>

      {last && (
        <p className="text-sm text-slate-300">
          Saldo em {formatDate(last.date)}:{" "}
          <span className="tabular-nums text-slate-100">
            {formatBRL(last.principal + last.yield - last.taxes)}
          </span>
        </p>
      )}

      <Panel title="Saldo ao longo do tempo">
        <PositionChart positions={positions.data ?? []} />
      </Panel>

      <Panel title="Movimentação">
        <MovementForm accountId={id} />
      </Panel>

      <Panel title="Posições">
        <PositionsTable positions={positions.data ?? []} />
      </Panel>
    </div>
  );
}

function MovementForm({ accountId }: { accountId: number }) {
  const mutation = useMovement(accountId);
  const [kind, setKind] = useState<MovementKind>("deposit");
  const [date, setDate] = useState("");
  const [amount, setAmount] = useState("");

  const submit = (e: FormEvent) => {
    e.preventDefault();
    mutation.mutate({
      kind,
      body: {
        date,
        amount: kind === "full-withdraw" ? undefined : Number(amount),
      },
    });
  };

  return (
    <form onSubmit={submit} className="flex flex-wrap items-end gap-3">
      <label className="text-sm">
        <span className="mb-1 block text-xs text-slate-500">Tipo</span>
        <select
          value={kind}
          onChange={(e) => setKind(e.target.value as MovementKind)}
          className="rounded-md border border-white/10 bg-slate-900 px-3 py-2 text-slate-100 outline-none focus:border-accent-500"
        >
          {(Object.keys(MOVEMENT_LABELS) as MovementKind[]).map((k) => (
            <option key={k} value={k}>
              {MOVEMENT_LABELS[k]}
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
      {kind !== "full-withdraw" && (
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
        {mutation.isPending ? "Salvando…" : "Registrar"}
      </button>
      {mutation.isError && <p className="w-full text-sm text-tax">{String(mutation.error)}</p>}
      {mutation.isSuccess && <p className="w-full text-sm text-yield">Movimentação registrada.</p>}
    </form>
  );
}
