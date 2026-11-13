import { useState } from "react";
import { Link } from "react-router-dom";
import {
  useAttribution,
  useOrderPlan,
  useStrategies,
  useStrategyEditions,
  useTransferAttribution,
} from "../api/queries.ts";
import type { AssetClass, Edition } from "../api/types.ts";
import {
  defaultTransferQuantity,
  isValidTransferQuantity,
  reportStrategiesOfClass,
  transferDestinations,
} from "../lib/attribution.ts";
import { formatNumber } from "../lib/format.ts";
import { assetClassLabel } from "../lib/month.ts";
import { ROUTES } from "../lib/routes.ts";
import { Button } from "./Button.tsx";
import { Field, Input } from "./Field.tsx";

const FIELD = "rounded-sm border border-line bg-surface-2 px-2 py-1.5 text-[13px] text-ink";

// The manual Attribution Transfer out of the legacy bucket (issues #371, #377): the destination list
// is the report-driven Strategies of the same class whose current Edition weighs the ticker, and the
// quantity defaults to the receiver's whole-unit shortfall, capped by the bucket slice. It applies
// immediately — no inbox row, no open month required by the endpoint — and moves ledger slices only:
// custody never changes. Shared by the Alocação tree and the Manual Strategy detail.
export interface TransferSource {
  id: number;
  assetClass: AssetClass;
}

export function AttributionTransferModal({
  ticker,
  source,
  onClose,
}: {
  ticker: string;
  source: TransferSource;
  onClose: () => void;
}) {
  const strategies = useStrategies();
  const plan = useOrderPlan();
  const attribution = useAttribution(ticker);
  const transfer = useTransferAttribution(ticker);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [quantityInput, setQuantityInput] = useState<string | null>(null);

  const catalog = strategies.data ?? [];
  const reportStrategies = reportStrategiesOfClass(catalog, source.assetClass);
  const editions = useStrategyEditions(reportStrategies);
  const editionsLoading = editions.some((query) => query.isLoading);
  const editionsById = new Map<number, Edition[]>();
  editions.forEach((query, index) => {
    if (query.data) editionsById.set(reportStrategies[index].id, query.data);
  });

  const destinations = transferDestinations(
    reportStrategies,
    editionsById,
    source.assetClass,
    ticker,
    plan.data,
  );
  const selected =
    destinations.find((destination) => destination.strategyId === selectedId) ?? destinations[0];
  const bucketSlice = attribution.data?.slices.find((slice) => slice.strategy_id === source.id);
  const bucketQuantity = bucketSlice?.quantity;
  const defaultQuantity =
    selected && bucketQuantity !== undefined
      ? defaultTransferQuantity(selected, bucketQuantity)
      : 0;
  const quantity = quantityInput === null ? defaultQuantity : Number(quantityInput);
  const canSubmit =
    selected !== undefined &&
    bucketQuantity !== undefined &&
    isValidTransferQuantity(quantity, bucketQuantity) &&
    !transfer.isPending;

  async function submit() {
    if (selected === undefined) return;
    try {
      await transfer.mutateAsync({
        from_strategy_id: source.id,
        to_strategy_id: selected.strategyId,
        quantity,
      });
      onClose();
    } catch {
      // The mutation error is rendered inline; the modal stays open for a retry.
    }
  }

  return (
    <dialog
      open
      aria-modal="true"
      aria-label="Transferir do legado"
      className="fixed inset-0 z-50 m-0 flex h-full w-full max-w-none items-center justify-center border-0 bg-transparent p-0"
    >
      <button
        type="button"
        aria-label="Fechar"
        onClick={onClose}
        className="absolute inset-0 bg-ground/70"
      />
      <div className="relative w-[420px] max-w-full space-y-4 rounded-md border border-line bg-surface p-4">
        <h3 className="font-cond text-[16px] font-bold">Transferir do legado</h3>

        <Field label="Ativo">
          <Input value={ticker} readOnly />
        </Field>

        {strategies.isLoading || attribution.isLoading || editionsLoading || plan.isLoading ? (
          <p className="text-[13px] text-ink-dim">Carregando…</p>
        ) : strategies.isError || attribution.isError ? (
          <p className="text-[12.5px] text-tax">Não foi possível carregar a transferência.</p>
        ) : destinations.length === 0 ? (
          <p data-testid="transfer-empty" className="text-[12.5px] text-ink-dim">
            Nenhuma estratégia com relatório da classe {assetClassLabel(source.assetClass)} tem uma
            edição ativa que atribua {ticker}.{" "}
            <Link to={ROUTES.strategies} className="text-principal hover:underline">
              Importe uma edição
            </Link>{" "}
            para transferir.
          </p>
        ) : (
          <>
            <Field label="Estratégia de destino">
              <select
                data-testid="transfer-destination"
                value={selected?.strategyId ?? ""}
                onChange={(event) => {
                  setSelectedId(Number(event.target.value));
                  setQuantityInput(null);
                }}
                className={FIELD}
              >
                {destinations.map((destination) => (
                  <option key={destination.strategyId} value={destination.strategyId}>
                    {destination.shortfall === undefined
                      ? destination.strategyName
                      : `${destination.strategyName} · falta ${formatNumber(destination.shortfall)} cotas`}
                  </option>
                ))}
              </select>
            </Field>

            <Field label="Quantidade">
              <Input
                data-testid="transfer-quantity"
                type="number"
                min="1"
                max={bucketQuantity}
                step="1"
                value={quantityInput ?? (defaultQuantity > 0 ? String(defaultQuantity) : "")}
                onChange={(event) => setQuantityInput(event.target.value)}
              />
            </Field>

            <p data-testid="transfer-hint" className="text-[11.5px] text-ink-faint">
              {formatNumber(bucketQuantity ?? 0)} cotas no legado. A custódia não muda: só a
              atribuição.
            </p>
          </>
        )}

        {transfer.error && <p className="text-[12.5px] text-tax">{transfer.error.message}</p>}

        <div className="flex justify-end gap-2">
          <Button variant="ghost" onClick={onClose}>
            Cancelar
          </Button>
          <Button variant="primary" onClick={submit} disabled={!canSubmit}>
            {transfer.isPending ? "Transferindo…" : `Transferir ${formatNumber(quantity)} cotas`}
          </Button>
        </div>
      </div>
    </dialog>
  );
}
