import { useState, type FormEvent } from "react";
import { Link, useParams } from "react-router-dom";
import {
  useAttributionSummary,
  useConsolidateListedAsset,
  useCorporateActions,
  useCreateCorporateAction,
  useCreateTrade,
  useDividends,
  useListedAssetPositions,
  useListedAssets,
  useRecordAttributionMovement,
  useStrategies,
  useTrades,
} from "../api/queries.ts";
import type { AttributionReason, CorporateAction, Trade, TradeSide } from "../api/types.ts";
import { assetKindLabel } from "../i18n/assetKind.ts";
import {
  CORPORATE_ACTION_KINDS,
  corporateActionKindLabel,
  type CorporateActionKind,
} from "../i18n/corporateActionType.ts";
import { dividendTypeLabel } from "../i18n/dividendType.ts";
import { Panel } from "../components/Panel.tsx";
import { PositionChart } from "../components/PositionChart.tsx";
import { PositionsTable } from "../components/PositionsTable.tsx";
import { formatBRL } from "../lib/money.ts";
import { formatDate } from "../lib/format.ts";
import { lastPosition } from "../lib/positions.ts";

export function ListedAssetPage() {
  const id = Number(useParams().id);
  const assets = useListedAssets();
  const positions = useListedAssetPositions(id);
  const consolidate = useConsolidateListedAsset();
  const asset = assets.data?.find((a) => a.id === id);

  if (assets.isLoading) return <p className="text-slate-400">Carregando…</p>;
  if (!asset)
    return (
      <p className="text-slate-400">
        Ativo não encontrado.{" "}
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
          <h1 className="text-xl font-semibold text-slate-100">
            {asset.ticker}{" "}
            <span className="text-sm font-normal text-slate-500">
              {assetKindLabel(asset.kind)}
            </span>
          </h1>
          <p className="mt-0.5 text-sm text-slate-400">{asset.name}</p>
        </div>
        <button
          onClick={() => consolidate.mutate(id)}
          disabled={consolidate.isPending}
          className="rounded-md bg-accent-500 px-3 py-1.5 text-sm font-medium text-slate-950 transition-colors hover:bg-accent-600 disabled:opacity-50"
        >
          {consolidate.isPending ? "Consolidando…" : "Consolidar"}
        </button>
      </div>
      {consolidate.isError && (
        <p className="text-sm text-tax">{String(consolidate.error)}</p>
      )}

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

      <Panel title="Valor de mercado ao longo do tempo">
        <PositionChart positions={positions.data ?? []} />
      </Panel>

      <div className="grid gap-6 lg:grid-cols-2">
        <Panel title="Negociações">
          <div className="space-y-4">
            <TradeForm assetId={id} />
            <TradesList assetId={id} />
          </div>
        </Panel>

        <Panel title="Eventos societários">
          <div className="space-y-4">
            <CorporateActionForm assetId={id} />
            <CorporateActionsList assetId={id} />
          </div>
        </Panel>
      </div>

      <Panel title="Atribuição por estratégia">
        <AttributionPanel assetId={id} />
      </Panel>

      <Panel title="Proventos declarados (B3)">
        <DividendsList assetId={id} />
      </Panel>

      <Panel title="Posições consolidadas">
        <PositionsTable positions={positions.data ?? []} />
      </Panel>
    </div>
  );
}

function TradeForm({ assetId }: { assetId: number }) {
  const mutation = useCreateTrade(assetId);
  const [side, setSide] = useState<TradeSide>("BUY");
  const [date, setDate] = useState("");
  const [quantity, setQuantity] = useState("");
  const [price, setPrice] = useState("");

  const submit = (e: FormEvent) => {
    e.preventDefault();
    mutation.mutate(
      { date, side, quantity: Math.abs(Number(quantity)), price: Number(price) },
      {
        onSuccess: () => {
          setDate("");
          setQuantity("");
          setPrice("");
        },
      },
    );
  };

  return (
    <form onSubmit={submit} className="flex flex-wrap items-end gap-3">
      <div className="flex gap-2">
        {(["BUY", "SELL"] as const).map((s) => (
          <button
            key={s}
            type="button"
            onClick={() => setSide(s)}
            className={`rounded-md px-3 py-1.5 text-sm font-medium transition-colors ${
              side === s
                ? "bg-accent-500 text-slate-950"
                : "bg-slate-800 text-slate-400 hover:text-slate-200"
            }`}
          >
            {s === "BUY" ? "Compra" : "Venda"}
          </button>
        ))}
      </div>
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
      <label className="text-sm">
        <span className="mb-1 block text-xs text-slate-500">Quantidade</span>
        <input
          type="number"
          step="any"
          min="0"
          required
          value={quantity}
          onChange={(e) => setQuantity(e.target.value)}
          className="w-28 rounded-md border border-white/10 bg-slate-900 px-3 py-2 text-slate-100 outline-none focus:border-accent-500"
        />
      </label>
      <label className="text-sm">
        <span className="mb-1 block text-xs text-slate-500">Preço (R$)</span>
        <input
          type="number"
          step="0.01"
          min="0"
          required
          value={price}
          onChange={(e) => setPrice(e.target.value)}
          className="w-28 rounded-md border border-white/10 bg-slate-900 px-3 py-2 text-slate-100 outline-none focus:border-accent-500"
        />
      </label>
      <button
        type="submit"
        disabled={mutation.isPending}
        className="rounded-md bg-accent-500 px-3 py-2 text-sm font-medium text-slate-950 transition-colors hover:bg-accent-600 disabled:opacity-50"
      >
        {mutation.isPending ? "Salvando…" : "Adicionar"}
      </button>
      {mutation.isError && (
        <p className="w-full text-sm text-tax">{String(mutation.error)}</p>
      )}
    </form>
  );
}

function TradesList({ assetId }: { assetId: number }) {
  const trades = useTrades(assetId);
  const rows = [...(trades.data ?? [])].reverse();

  if (rows.length === 0)
    return <p className="text-sm text-slate-500">Nenhuma negociação lançada.</p>;

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm tabular-nums">
        <thead>
          <tr className="text-left text-xs uppercase tracking-wide text-slate-500">
            <th className="py-2 pr-4 font-medium">Data</th>
            <th className="py-2 pr-4 font-medium">Tipo</th>
            <th className="py-2 pr-4 text-right font-medium">Qtd.</th>
            <th className="py-2 text-right font-medium">Preço</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-white/5">
          {rows.map((t: Trade) => (
            <tr key={t.id}>
              <td className="py-2 pr-4 text-slate-400">{formatDate(t.date)}</td>
              <td className={`py-2 pr-4 ${t.side === "BUY" ? "text-yield" : "text-tax"}`}>
                {t.side === "BUY" ? "Compra" : "Venda"}
              </td>
              <td className="py-2 pr-4 text-right text-slate-200">{t.quantity}</td>
              <td className="py-2 text-right text-slate-200">{formatBRL(t.price)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

const NEEDS_RATIO = new Set<CorporateActionKind>(["split", "reverse-split", "bonus"]);

function CorporateActionForm({ assetId }: { assetId: number }) {
  const mutation = useCreateCorporateAction(assetId);
  const [kind, setKind] = useState<CorporateActionKind>("split");
  const [date, setDate] = useState("");
  const [ratio, setRatio] = useState("");
  const [valuePerNewShare, setValuePerNewShare] = useState("");
  const [newTicker, setNewTicker] = useState("");

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const base = { date };
    const body =
      kind === "split" || kind === "reverse-split"
        ? { kind, ...base, ratio: Number(ratio) }
        : kind === "bonus"
          ? {
              kind,
              ...base,
              ratio: Number(ratio),
              value_per_new_share: Number(valuePerNewShare),
            }
          : { kind, ...base, new_ticker: newTicker.toUpperCase() };

    mutation.mutate(body, {
      onSuccess: () => {
        setDate("");
        setRatio("");
        setValuePerNewShare("");
        setNewTicker("");
      },
    });
  };

  return (
    <form onSubmit={submit} className="space-y-3">
      <select
        value={kind}
        onChange={(e) => setKind(e.target.value as CorporateActionKind)}
        className="w-full rounded-md border border-white/10 bg-slate-900 px-3 py-2 text-sm text-slate-100 outline-none focus:border-accent-500"
      >
        {CORPORATE_ACTION_KINDS.map((k) => (
          <option key={k} value={k}>
            {corporateActionKindLabel(k)}
          </option>
        ))}
      </select>

      <div className="flex flex-wrap items-end gap-3">
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

        {NEEDS_RATIO.has(kind) && (
          <label className="text-sm">
            <span className="mb-1 block text-xs text-slate-500">
              Proporção (novas por antiga)
            </span>
            <input
              type="number"
              step="any"
              min="0"
              required
              value={ratio}
              onChange={(e) => setRatio(e.target.value)}
              className="w-32 rounded-md border border-white/10 bg-slate-900 px-3 py-2 text-slate-100 outline-none focus:border-accent-500"
            />
          </label>
        )}

        {kind === "bonus" && (
          <label className="text-sm">
            <span className="mb-1 block text-xs text-slate-500">
              Valor por ação nova (R$)
            </span>
            <input
              type="number"
              step="0.01"
              min="0"
              required
              value={valuePerNewShare}
              onChange={(e) => setValuePerNewShare(e.target.value)}
              className="w-32 rounded-md border border-white/10 bg-slate-900 px-3 py-2 text-slate-100 outline-none focus:border-accent-500"
            />
          </label>
        )}

        {kind === "ticker-change" && (
          <label className="text-sm">
            <span className="mb-1 block text-xs text-slate-500">Novo ticker</span>
            <input
              required
              value={newTicker}
              onChange={(e) => setNewTicker(e.target.value)}
              className="w-32 rounded-md border border-white/10 bg-slate-900 px-3 py-2 text-slate-100 outline-none focus:border-accent-500"
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
      </div>
      {mutation.isError && <p className="text-sm text-tax">{String(mutation.error)}</p>}
    </form>
  );
}

function CorporateActionsList({ assetId }: { assetId: number }) {
  const actions = useCorporateActions(assetId);
  const rows = [...(actions.data ?? [])].reverse();

  if (rows.length === 0)
    return <p className="text-sm text-slate-500">Nenhum evento registrado.</p>;

  return (
    <ul className="divide-y divide-white/5 text-sm">
      {rows.map((a: CorporateAction) => (
        <li key={a.id} className="flex items-center justify-between gap-3 py-2">
          <span className="text-slate-400">{formatDate(a.date)}</span>
          <span className="text-slate-200">{describeCorporateAction(a)}</span>
        </li>
      ))}
    </ul>
  );
}

function describeCorporateAction(a: CorporateAction): string {
  if (a.new_ticker) return `Troca de ticker → ${a.new_ticker}`;
  if (a.value_per_new_share != null) return `Bonificação (${a.ratio}× a ${formatBRL(a.value_per_new_share)})`;
  if (a.ratio != null && a.ratio >= 1) return `Desdobramento ${a.ratio}×`;
  if (a.ratio != null) return `Grupamento ${1 / a.ratio}×`;
  return "Evento societário";
}

function DividendsList({ assetId }: { assetId: number }) {
  const dividends = useDividends(assetId);

  if (dividends.isLoading) return <p className="text-sm text-slate-500">Buscando na B3…</p>;
  if (dividends.isError)
    return <p className="text-sm text-tax">Falha ao buscar: {String(dividends.error)}</p>;
  if (!dividends.data || dividends.data.length === 0)
    return <p className="text-sm text-slate-500">Nenhum provento encontrado.</p>;

  const rows = [...dividends.data].reverse();

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm tabular-nums">
        <thead>
          <tr className="text-left text-xs uppercase tracking-wide text-slate-500">
            <th className="py-2 pr-4 font-medium">Tipo</th>
            <th className="py-2 pr-4 text-right font-medium">Valor/cota</th>
            <th className="py-2 pr-4 font-medium">Data-com</th>
            <th className="py-2 font-medium">Pagamento</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-white/5">
          {rows.map((d, i) => (
            <tr key={`${d.ex_date}-${i}`}>
              <td className="py-2 pr-4 text-slate-300">{dividendTypeLabel(d.type)}</td>
              <td className="py-2 pr-4 text-right text-yield">
                {formatBRL(d.value_per_share)}
              </td>
              <td className="py-2 pr-4 text-slate-400">{formatDate(d.ex_date)}</td>
              <td className="py-2 text-slate-400">
                {d.payment_date ? formatDate(d.payment_date) : "—"}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

const REASON_LABELS: Record<AttributionReason, string> = {
  BUY: "Compra",
  SELL: "Venda",
  TRANSFER: "Transferência",
  ADJUSTMENT: "Ajuste",
};

function AttributionPanel({ assetId }: { assetId: number }) {
  const summary = useAttributionSummary(assetId);
  const strategies = useStrategies();
  const mutation = useRecordAttributionMovement(assetId);
  const [strategyId, setStrategyId] = useState<number | "">("");
  const [date, setDate] = useState("");
  const [quantity, setQuantity] = useState("");
  const [reason, setReason] = useState<AttributionReason>("BUY");

  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (strategyId === "") return;
    mutation.mutate(
      { strategy_id: strategyId, date, quantity: Number(quantity), reason },
      {
        onSuccess: () => {
          setDate("");
          setQuantity("");
        },
      },
    );
  };

  if (strategies.data && strategies.data.length === 0) {
    return (
      <p className="text-sm text-slate-500">
        Cadastre uma estratégia em <Link to="/carteira" className="text-accent-500 hover:underline">Carteira</Link>{" "}
        antes de atribuir custódia a ela.
      </p>
    );
  }

  return (
    <div className="space-y-4">
      {summary.data && (
        <div className="space-y-2">
          <p className="text-sm text-slate-300">
            Custódia:{" "}
            <span className="tabular-nums text-slate-100">{summary.data.custody_quantity}</span>
            {" · "}Atribuído:{" "}
            <span className="tabular-nums text-slate-100">{summary.data.attributed_quantity}</span>
            {summary.data.unattributed_quantity !== 0 && (
              <span className="ml-2 rounded bg-tax/20 px-1.5 py-0.5 text-xs text-tax">
                {summary.data.unattributed_quantity > 0 ? "não atribuído" : "atribuído em excesso"}:{" "}
                {Math.abs(summary.data.unattributed_quantity)}
              </span>
            )}
          </p>
          {summary.data.balances.length > 0 && (
            <ul className="flex flex-wrap gap-2">
              {summary.data.balances.map((b) => (
                <li
                  key={b.strategy_id}
                  className="rounded bg-slate-800 px-2.5 py-1 text-sm text-slate-300"
                >
                  {b.strategy_name}: <span className="tabular-nums">{b.quantity}</span>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}

      <form onSubmit={submit} className="flex flex-wrap items-end gap-3">
        <label className="text-sm">
          <span className="mb-1 block text-xs text-slate-500">Estratégia</span>
          <select
            required
            value={strategyId}
            onChange={(e) => setStrategyId(e.target.value ? Number(e.target.value) : "")}
            className="rounded-md border border-white/10 bg-slate-900 px-3 py-2 text-slate-100 outline-none focus:border-accent-500"
          >
            <option value="">Selecione…</option>
            {(strategies.data ?? []).map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </select>
        </label>
        <label className="text-sm">
          <span className="mb-1 block text-xs text-slate-500">Tipo</span>
          <select
            value={reason}
            onChange={(e) => setReason(e.target.value as AttributionReason)}
            className="rounded-md border border-white/10 bg-slate-900 px-3 py-2 text-slate-100 outline-none focus:border-accent-500"
          >
            {(Object.keys(REASON_LABELS) as AttributionReason[]).map((r) => (
              <option key={r} value={r}>
                {REASON_LABELS[r]}
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
        <label className="text-sm">
          <span className="mb-1 block text-xs text-slate-500">
            Quantidade (negativo tira da estratégia)
          </span>
          <input
            type="number"
            step="any"
            required
            value={quantity}
            onChange={(e) => setQuantity(e.target.value)}
            className="w-40 rounded-md border border-white/10 bg-slate-900 px-3 py-2 text-slate-100 outline-none focus:border-accent-500"
          />
        </label>
        <button
          type="submit"
          disabled={mutation.isPending}
          className="rounded-md bg-accent-500 px-3 py-2 text-sm font-medium text-slate-950 transition-colors hover:bg-accent-600 disabled:opacity-50"
        >
          {mutation.isPending ? "Salvando…" : "Registrar"}
        </button>
      </form>
      {mutation.isError && <p className="text-sm text-tax">{String(mutation.error)}</p>}
    </div>
  );
}
