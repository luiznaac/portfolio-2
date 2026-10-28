import { Link } from "react-router-dom";
import { ApiError } from "../api/client.ts";
import type {
  Attribution,
  AttributionSlice,
  AttributionSliceCreation,
  Strategy,
} from "../api/types.ts";
import { attributionPercent, remainingAttributable } from "../lib/attribution.ts";
import { formatNumber } from "../lib/format.ts";
import { Button } from "./Button.tsx";
import { Chip } from "./Chip.tsx";
import { Panel } from "./Panel.tsx";

const QUANTITY_FIELD = {
  type: "number",
  min: "0.000001",
  step: "any",
  required: true,
  className:
    "w-[120px] rounded-sm border border-line bg-surface-2 px-2 py-1.5 font-mono text-[12.5px] text-ink",
} as const;

// The strategy slices cycle through the sign palette; the order is stable because the backend sorts
// by strategy name. An unattributed segment always trails them in the neutral line colour.
const SLICE_TONES = ["bg-principal", "bg-violet", "bg-yield", "bg-accent", "bg-tax"];

// Presentational attribution panel for the Ativos detail: the page owns the queries and mutations
// (the data seam lives in pages/), this component renders the slices, the unattributed remainder,
// and the add/edit/remove controls.
export function AttributionPanel({
  attribution,
  loading,
  loadError,
  strategies,
  strategiesLoading,
  pending,
  mutationError,
  onSave,
  onRemove,
}: {
  attribution?: Attribution;
  loading: boolean;
  loadError: Error | null;
  strategies: Strategy[];
  strategiesLoading: boolean;
  pending: boolean;
  mutationError: Error | null;
  onSave: (creation: AttributionSliceCreation) => Promise<unknown>;
  onRemove: (strategyId: number) => Promise<unknown>;
}) {
  if (loading) {
    return (
      <Panel title="Atribuição">
        <p className="text-[13.5px] text-ink-dim">Carregando…</p>
      </Panel>
    );
  }

  if (loadError || !attribution) {
    return (
      <Panel title="Atribuição">
        <p className="text-[13.5px] text-tax">Não foi possível carregar a atribuição.</p>
      </Panel>
    );
  }

  return (
    <Panel title="Atribuição">
      <AttributionLedger
        attribution={attribution}
        strategies={strategies}
        strategiesLoading={strategiesLoading}
        pending={pending}
        error={mutationError}
        onSave={onSave}
        onRemove={onRemove}
      />
    </Panel>
  );
}

function AttributionLedger({
  attribution,
  strategies,
  strategiesLoading,
  pending,
  error,
  onSave,
  onRemove,
}: {
  attribution: Attribution;
  strategies: Strategy[];
  strategiesLoading: boolean;
  pending: boolean;
  error: Error | null;
  onSave: (creation: AttributionSliceCreation) => Promise<unknown>;
  onRemove: (strategyId: number) => Promise<unknown>;
}) {
  const { custody_quantity, attributed_quantity, unattributed_quantity, slices } = attribution;
  const attributedIds = new Set(slices.map((slice) => slice.strategy_id));
  const available = strategies.filter((strategy) => !attributedIds.has(strategy.id));
  const availableQuantity = remainingAttributable(custody_quantity, slices);

  return (
    <>
      <div
        className="mb-2 flex flex-wrap items-center gap-2 text-[13px] text-ink-dim"
        data-testid="attribution-summary"
      >
        <Chip tone="blue">{`${formatNumber(attributed_quantity)} atribuído`}</Chip>
        <Chip tone={unattributed_quantity === 0 ? "green" : "amber"}>
          {`${formatNumber(unattributed_quantity)} não atribuído`}
        </Chip>
        <span className="text-ink-faint">custódia {formatNumber(custody_quantity)}</span>
      </div>

      <div
        className="mb-4 flex h-2.5 overflow-hidden rounded-xs border border-line-soft bg-surface-3"
        data-testid="attribution-bar"
      >
        {slices.map((slice, index) => (
          <div
            key={slice.strategy_id}
            className={SLICE_TONES[index % SLICE_TONES.length]}
            style={{ width: `${attributionPercent(slice.quantity, custody_quantity)}%` }}
            title={`${slice.strategy_name}: ${formatNumber(slice.quantity)}`}
          />
        ))}
        {unattributed_quantity > 0 && (
          <div
            className="bg-line"
            style={{ width: `${attributionPercent(unattributed_quantity, custody_quantity)}%` }}
            title={`Não atribuído: ${formatNumber(unattributed_quantity)}`}
          />
        )}
      </div>

      {slices.length === 0 ? (
        <p className="mb-3 text-[13px] text-ink-dim">Nenhuma estratégia atribuída.</p>
      ) : (
        <div className="mb-4 space-y-2" data-testid="attribution-slices">
          {slices.map((slice) => (
            <AttributionRow
              key={slice.strategy_id}
              slice={slice}
              maximum={remainingAttributable(custody_quantity, slices, slice.strategy_id)}
              pending={pending}
              onSave={onSave}
              onRemove={onRemove}
            />
          ))}
        </div>
      )}

      {strategiesLoading ? (
        <p className="text-[12.5px] text-ink-faint">Carregando estratégias…</p>
      ) : strategies.length === 0 ? (
        <p className="text-[12.5px] text-ink-dim">
          <Link to="/strategies" className="text-principal hover:underline">
            Cadastre uma estratégia
          </Link>{" "}
          para atribuir parte da custódia.
        </p>
      ) : available.length === 0 ? (
        <p className="text-[12.5px] text-ink-faint">Todas as estratégias já têm uma fatia.</p>
      ) : availableQuantity <= 0 ? (
        <p className="text-[12.5px] text-ink-faint">Custódia totalmente atribuída.</p>
      ) : (
        <AddAttributionForm
          strategies={available}
          availableQuantity={availableQuantity}
          pending={pending}
          onSave={onSave}
        />
      )}

      {error && (
        <p className="mt-3 text-[12.5px] text-tax">
          {error instanceof ApiError && error.status === 400
            ? "Atribuição inválida: a soma das fatias não pode passar da custódia."
            : "Não foi possível salvar a atribuição."}
        </p>
      )}
    </>
  );
}

function AttributionRow({
  slice,
  maximum,
  pending,
  onSave,
  onRemove,
}: {
  slice: AttributionSlice;
  maximum: number;
  pending: boolean;
  onSave: (creation: AttributionSliceCreation) => Promise<unknown>;
  onRemove: (strategyId: number) => Promise<unknown>;
}) {
  return (
    <form
      className="flex flex-wrap items-center gap-2"
      onSubmit={(event) => {
        event.preventDefault();
        void onSave(readCreation(event.currentTarget)).catch(() => undefined);
      }}
    >
      <input type="hidden" name="strategy_id" value={slice.strategy_id} />
      <span className="w-[220px] text-[13.5px] text-ink">{slice.strategy_name}</span>
      <input {...QUANTITY_FIELD} name="quantity" max={maximum} defaultValue={slice.quantity} />
      <span className="font-mono text-[11px] text-ink-faint">de {formatNumber(maximum)}</span>
      <Button type="submit" disabled={pending}>
        Salvar
      </Button>
      <Button
        type="button"
        variant="danger"
        disabled={pending}
        onClick={() => void onRemove(slice.strategy_id).catch(() => undefined)}
      >
        Remover
      </Button>
    </form>
  );
}

function AddAttributionForm({
  strategies,
  availableQuantity,
  pending,
  onSave,
}: {
  strategies: Strategy[];
  availableQuantity: number;
  pending: boolean;
  onSave: (creation: AttributionSliceCreation) => Promise<unknown>;
}) {
  return (
    <form
      className="flex flex-wrap items-center gap-2 border-t border-line-soft pt-3"
      onSubmit={(event) => {
        event.preventDefault();
        const form = event.currentTarget;
        void onSave(readCreation(form))
          .then(() => form.reset())
          .catch(() => undefined);
      }}
    >
      <select
        name="strategy_id"
        required
        defaultValue=""
        className="w-[220px] rounded-sm border border-line bg-surface-2 px-2 py-1.5 text-[13px] text-ink"
      >
        <option value="" disabled>
          Estratégia
        </option>
        {strategies.map((strategy) => (
          <option key={strategy.id} value={strategy.id}>
            {strategy.name}
          </option>
        ))}
      </select>
      <input
        {...QUANTITY_FIELD}
        name="quantity"
        max={availableQuantity}
        placeholder={`até ${formatNumber(availableQuantity)}`}
      />
      <Button type="submit" variant="primary" disabled={pending}>
        Adicionar
      </Button>
    </form>
  );
}

function readCreation(form: HTMLFormElement): AttributionSliceCreation {
  const data = new FormData(form);
  return {
    strategy_id: Number(data.get("strategy_id")),
    quantity: Number(data.get("quantity")),
  };
}
