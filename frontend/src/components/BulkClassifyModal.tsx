import { useState } from "react";
import { useClassificationQueue, useClassifyTickers } from "../api/queries.ts";
import {
  CLASSIFY_CLASSES,
  type ClassifiableAssetClass,
  classifyLabel,
  queueReasonLabel,
} from "../lib/catalog.ts";
import { formatNumber } from "../lib/format.ts";
import { Button } from "./Button.tsx";

// The held-only bulk classification: the residue the last refresh could not classify, one row per
// ticker with its short name, units and reason. Only the rows the owner picks travel — the modal
// never guesses a class — and the whole selection is one atomic write, so a failure leaves the queue
// exactly as it was. It opens from the Dados da carteira panel's unclassified section.
export function BulkClassifyModal({ onClose }: { onClose: () => void }) {
  const queue = useClassificationQueue();
  const classify = useClassifyTickers();
  const [chosen, setChosen] = useState<Record<string, ClassifiableAssetClass>>({});
  const items = queue.data ?? [];
  const chosenCount = Object.keys(chosen).length;

  function toggle(ticker: string, assetClass: ClassifiableAssetClass) {
    setChosen((current) => {
      const next = { ...current };
      if (next[ticker] === assetClass) {
        delete next[ticker];
      } else {
        next[ticker] = assetClass;
      }
      return next;
    });
  }

  async function save() {
    try {
      await classify.mutateAsync(chosen);
      onClose();
    } catch {
      // The mutation error renders inline; the modal stays open with the queue untouched.
    }
  }

  return (
    <dialog
      open
      aria-modal="true"
      aria-label="Classificar tickers sem classe"
      className="fixed inset-0 z-50 m-0 flex h-full w-full max-w-none items-center justify-center border-0 bg-transparent p-0"
    >
      <button
        type="button"
        aria-label="Fechar"
        onClick={onClose}
        className="absolute inset-0 bg-ground/70"
      />
      <div className="relative max-h-[85vh] w-[520px] max-w-full space-y-4 overflow-y-auto rounded-md border border-line bg-surface p-4">
        <h3 className="font-cond text-[16px] font-bold">Classificar tickers sem classe</h3>
        <p className="text-[12.5px] text-ink-dim">
          Só os tickers escolhidos são salvos, em uma única gravação. Um que continuar sem classe
          aparece aqui com o motivo.
        </p>

        {queue.isLoading ? (
          <p className="text-[13px] text-ink-dim">Carregando…</p>
        ) : queue.isError ? (
          <p className="text-[12.5px] text-tax">
            Não foi possível carregar a fila de classificação.
          </p>
        ) : items.length === 0 ? (
          <p data-testid="bulk-classify-empty" className="text-[13px] text-yield">
            Nenhum ticker sem classe.
          </p>
        ) : (
          <ul className="divide-y divide-line-soft">
            {items.map((item) => (
              <li
                key={item.ticker}
                data-testid={`bulk-classify-row-${item.ticker}`}
                className="flex items-center justify-between gap-3 py-2"
              >
                <div className="min-w-0">
                  <p className="font-mono text-[13px]">
                    {item.ticker} <span className="text-ink-dim">{item.short_name}</span>
                  </p>
                  <p className="text-[11.5px] text-ink-faint">
                    {formatNumber(item.quantity)} un · {queueReasonLabel(item.reason)}
                  </p>
                </div>
                <div className="flex shrink-0 gap-2">
                  {CLASSIFY_CLASSES.map((assetClass) => (
                    <button
                      key={assetClass}
                      type="button"
                      aria-label={`Classificar ${item.ticker} como ${classifyLabel(assetClass)}`}
                      aria-pressed={chosen[item.ticker] === assetClass}
                      onClick={() => toggle(item.ticker, assetClass)}
                      className={
                        chosen[item.ticker] === assetClass
                          ? "rounded-sm border border-principal bg-principal/10 px-2 py-1 font-mono text-[11px] text-principal"
                          : "rounded-sm border border-line px-2 py-1 font-mono text-[11px] text-ink-dim hover:border-surface-3 hover:text-ink"
                      }
                    >
                      {classifyLabel(assetClass)}
                    </button>
                  ))}
                </div>
              </li>
            ))}
          </ul>
        )}

        {classify.error && <p className="text-[12.5px] text-tax">{classify.error.message}</p>}

        <div className="flex justify-end gap-2">
          <Button variant="ghost" onClick={onClose}>
            Cancelar
          </Button>
          <Button
            variant="primary"
            data-testid="bulk-classify-save"
            onClick={save}
            disabled={chosenCount === 0 || classify.isPending}
          >
            {classify.isPending
              ? "Salvando…"
              : `Salvar ${chosenCount} ${chosenCount === 1 ? "classificação" : "classificações"}`}
          </Button>
        </div>
      </div>
    </dialog>
  );
}
