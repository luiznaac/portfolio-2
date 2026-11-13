import { useState } from "react";
import { useGaps, usePriceGap } from "../api/queries.ts";
import type { Gap } from "../api/types.ts";
import { Button } from "../components/Button.tsx";
import { Chip } from "../components/Chip.tsx";
import { Panel } from "../components/Panel.tsx";
import { formatCurrency, formatIsoDate, formatNumber } from "../lib/format.ts";
import { isMissingBuy, openGaps } from "../lib/history.ts";

// Ativos · Lacunas: the custody deltas the ledger cannot explain. Adoption seeds, the backfill replay
// and the custody cross-check live on Ativos · Negócios ("Reconstruir histórico") since #379, so this
// page carries only the gap list and its pricing.
export function History() {
  return (
    <section className="space-y-4">
      <GapsStep />
    </section>
  );
}

function GapsStep() {
  const gaps = useGaps();
  const open = openGaps(gaps.data ?? []);

  return (
    <Panel title="Lacunas">
      <p className="mb-3 text-[12.5px] text-ink-dim">
        Uma lacuna é um delta de custódia que o livro não explica: uma compra faltante entra pelo
        preço de mercado (aproximado) e uma venda faltante espera os proventos que você informar.
      </p>

      {gaps.isLoading ? (
        <p className="text-[13px] text-ink-dim">Carregando…</p>
      ) : open.length === 0 ? (
        <p className="text-[13px] text-yield">Nenhuma lacuna aberta.</p>
      ) : (
        <div data-testid="lacunas" className="divide-y divide-line-soft">
          {open.map((gap) => (
            <GapRow key={gap.id} gap={gap} />
          ))}
        </div>
      )}
    </Panel>
  );
}

function GapRow({ gap }: { gap: Gap }) {
  const priceGap = usePriceGap();
  const [price, setPrice] = useState(gap.unit_price === undefined ? "" : String(gap.unit_price));
  const buy = isMissingBuy(gap);
  const pending = gap.realized_pending === true;

  return (
    <div className="flex flex-wrap items-center justify-between gap-3 py-2.5 text-[12.5px]">
      <span className="font-mono font-medium">{gap.ticker}</span>
      <span className="font-mono text-[11.5px] text-ink-faint">{formatIsoDate(gap.date)}</span>
      <span className="text-ink-dim">{formatNumber(gap.quantity)} un</span>

      {buy ? (
        <span className="flex items-center gap-2">
          <span data-testid={`gap-approximate-${gap.ticker}`}>
            <Chip tone="amber">aproximado</Chip>
          </span>
          <span className="font-mono">{formatCurrency(gap.unit_price ?? 0)}</span>
        </span>
      ) : (
        <span className="flex items-center gap-2">
          {pending && (
            <span data-testid={`gap-pending-${gap.id}`}>
              <Chip tone="rose">proventos pendentes</Chip>
            </span>
          )}
          <input
            aria-label={`Proventos de ${gap.ticker}`}
            data-testid={`gap-price-${gap.id}`}
            type="number"
            min="0"
            step="0.01"
            value={price}
            onChange={(event) => setPrice(event.target.value)}
            className="w-24 rounded-sm border border-line bg-surface-2 px-2 py-1 text-right font-mono text-[13px]"
          />
          <Button
            data-testid={`gap-price-save-${gap.id}`}
            variant="primary"
            onClick={() => priceGap.mutate({ id: gap.id, unitPrice: Number(price) })}
            disabled={price.trim().length === 0 || !(Number(price) >= 0) || priceGap.isPending}
          >
            Salvar
          </Button>
        </span>
      )}
    </div>
  );
}
