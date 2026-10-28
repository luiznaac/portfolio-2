import { useClassifyTicker, useRefreshCatalog } from "../api/queries.ts";
import { CLASSIFY_CLASSES, type ClassifiableAssetClass, classifyLabel } from "../lib/catalog.ts";
import { Button } from "./Button.tsx";

// The small classify dialog shared by Alocação's "Sem classe" leaves and the Ativos detail header.
// The two class buttons are the manual classification; "Atualizar catálogo" is the bulk
// alternative, which lets the next COTAHIST refresh attribute the class instead.
export function ClassifyTickerModal({ ticker, onClose }: { ticker: string; onClose: () => void }) {
  const classify = useClassifyTicker(ticker);
  const refresh = useRefreshCatalog();
  const pending = classify.isPending || refresh.isPending;
  const error = classify.error ?? refresh.error;

  async function pick(assetClass: ClassifiableAssetClass) {
    try {
      await classify.mutateAsync(assetClass);
      onClose();
    } catch {
      // The mutation error renders inline; the modal stays open for a retry.
    }
  }

  async function refreshCatalog() {
    try {
      await refresh.mutateAsync();
      onClose();
    } catch {
      // Same as above — keep the dialog up with the error shown.
    }
  }

  return (
    <dialog
      open
      aria-modal="true"
      aria-label={`Classificar ${ticker}`}
      className="fixed inset-0 z-50 m-0 flex h-full w-full max-w-none items-center justify-center border-0 bg-transparent p-0"
    >
      <button
        type="button"
        aria-label="Fechar"
        onClick={onClose}
        className="absolute inset-0 bg-ground/70"
      />
      <div className="relative w-[380px] max-w-full space-y-4 rounded-md border border-line bg-surface p-4">
        <h3 className="font-cond text-[16px] font-bold">Classificar {ticker}</h3>
        <p className="text-[12.5px] text-ink-dim">
          O catálogo ainda não atribuiu uma classe a este ativo. A classificação manual vale até
          você revertê-la, mesmo que o catálogo aprenda outra classe depois.
        </p>

        <div className="grid grid-cols-2 gap-2">
          {CLASSIFY_CLASSES.map((assetClass) => (
            <Button key={assetClass} onClick={() => pick(assetClass)} disabled={pending}>
              {classifyLabel(assetClass)}
            </Button>
          ))}
        </div>

        {error && <p className="text-[12.5px] text-tax">{error.message}</p>}

        <div className="flex items-center justify-between">
          <Button variant="ghost" onClick={refreshCatalog} disabled={pending}>
            {refresh.isPending ? "Atualizando…" : "Atualizar catálogo"}
          </Button>
          <Button variant="ghost" onClick={onClose}>
            Cancelar
          </Button>
        </div>
      </div>
    </dialog>
  );
}
