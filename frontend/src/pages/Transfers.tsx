import { Link } from "react-router-dom";
import { useDecideTransfer, useOrders, useStrategies } from "../api/queries.ts";
import type { OrderList, Strategy, TransferProposal } from "../api/types.ts";
import { Button } from "../components/Button.tsx";
import { Chip } from "../components/Chip.tsx";
import { Panel } from "../components/Panel.tsx";
import {
  decidedTransferNoun,
  decidedTransfers,
  pendingTransferNoun,
  pendingTransfers,
} from "../lib/fechamento.ts";
import { monthPeriodLabel } from "../lib/month.ts";
import {
  transferCopy,
  transferEffect,
  transferOutcome,
  transferQuantityLabel,
  transferStatusLabel,
} from "../lib/orders.ts";
import { ROUTES } from "../lib/routes.ts";

// Plano / Mês · Transferências (issue #390): the Attribution Transfer inbox on its own surface. A
// decision is atomic — approve moves the proposal's whole quantity, reject moves none — so the page
// offers no quantity control, only the two decisions. Custody never moves; only the ledger slices do.
export function Transfers() {
  const orders = useOrders();
  const strategies = useStrategies().data;

  return (
    <section>
      {orders.isLoading ? (
        <p className="text-[13.5px] text-ink-dim">Carregando…</p>
      ) : orders.isError ? (
        <p className="text-[13.5px] text-tax">{orders.error.message}</p>
      ) : orders.data ? (
        <TransfersView list={orders.data} strategies={strategies} />
      ) : (
        <Panel title="Sem mês aberto">
          <p className="text-[13px] text-ink-dim">
            Abra um mês em{" "}
            <Link to={ROUTES.plan} className="text-principal underline">
              Mês atual
            </Link>{" "}
            para calcular o plano e decidir as transferências.
          </p>
        </Panel>
      )}
    </section>
  );
}

function TransfersView({
  list,
  strategies,
}: {
  list: OrderList;
  strategies: Strategy[] | undefined;
}) {
  const decide = useDecideTransfer();
  const pending = pendingTransfers(list);
  const decided = decidedTransfers(list);

  return (
    <div data-testid="transfers-view">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <span className="font-cond text-[15px] font-semibold">{monthPeriodLabel(list.period)}</span>
        <span className="font-mono text-[11px] text-ink-dim">
          {pending.length} {pendingTransferNoun(pending.length)} · {decided.length}{" "}
          {decidedTransferNoun(decided.length)}
        </span>
      </div>

      <div className="space-y-4">
        <Panel
          title="A decidir"
          action={
            pending.length === 0 ? (
              <Chip tone="green">Nenhuma pendente</Chip>
            ) : (
              <Chip tone="amber">{pending.length} pendentes</Chip>
            )
          }
        >
          <p className="mb-3 text-[12.5px] text-ink-dim">
            A decisão é atômica: aprovar move a quantidade inteira, rejeitar não move nenhuma. A
            custódia nunca muda — só a atribuição entre estratégias.
          </p>
          {decide.error && <p className="mb-3 text-[12.5px] text-tax">{decide.error.message}</p>}
          {pending.length === 0 ? (
            <p className="text-[13px] text-ink-dim">
              Nenhuma transferência pendente. O fechamento está liberado —{" "}
              <Link to={ROUTES.planClose} className="text-principal underline">
                Ver fechamento →
              </Link>
            </p>
          ) : (
            <div className="divide-y divide-line-soft">
              {pending.map((transfer) => (
                <PendingRow
                  key={transfer.id}
                  transfer={transfer}
                  strategies={strategies}
                  isPending={decide.isPending}
                  onDecide={decide.mutate}
                />
              ))}
            </div>
          )}
        </Panel>

        <Panel
          title="História"
          action={
            <span className="font-mono text-[11px] text-ink-dim">{decided.length} decididas</span>
          }
        >
          {decided.length === 0 ? (
            <p className="text-[13px] text-ink-dim">
              Nenhuma decisão registrada neste mês. Aprovar ou rejeitar uma proposta grava o
              resultado aqui.
            </p>
          ) : (
            <div className="divide-y divide-line-soft">
              {decided.map((transfer) => (
                <DecidedRow key={transfer.id} transfer={transfer} />
              ))}
            </div>
          )}
        </Panel>
      </div>
    </div>
  );
}

function PendingRow({
  transfer,
  strategies,
  isPending,
  onDecide,
}: {
  transfer: TransferProposal;
  strategies: Strategy[] | undefined;
  isPending: boolean;
  onDecide: (decision: { id: number; decision: "approve" | "reject" }) => void;
}) {
  return (
    <div
      data-testid={`transfer-pending-${transfer.id}`}
      className="flex flex-wrap items-center justify-between gap-3 py-2.5"
    >
      <div>
        <div className="flex flex-wrap items-center gap-2">
          <span className="font-mono text-[12.5px] font-semibold text-ink">{transfer.ticker}</span>
          <Chip>{transfer.from_strategy}</Chip>
          <span className="text-ink-faint">→</span>
          <Chip tone="blue">{transfer.to_strategy}</Chip>
          <span className="text-[11.5px] text-ink-faint">{transferCopy(transfer, strategies)}</span>
        </div>
        <div className="mt-1 flex flex-wrap items-center gap-2 text-[12px] text-ink-dim">
          <span className="font-mono">{transferQuantityLabel(transfer.quantity)}</span>
          <span className="text-ink-faint">·</span>
          <span data-testid={`transfer-effect-${transfer.id}`}>
            {transferEffect(transfer.quantity)}
          </span>
        </div>
      </div>
      <div className="flex items-center gap-2">
        <Button
          variant="primary"
          data-testid={`transfer-approve-${transfer.id}`}
          onClick={() => onDecide({ id: transfer.id, decision: "approve" })}
          disabled={isPending}
        >
          Aprovar
        </Button>
        <Button
          variant="ghost"
          data-testid={`transfer-reject-${transfer.id}`}
          onClick={() => onDecide({ id: transfer.id, decision: "reject" })}
          disabled={isPending}
        >
          Rejeitar
        </Button>
      </div>
    </div>
  );
}

function DecidedRow({ transfer }: { transfer: TransferProposal }) {
  return (
    <div
      data-testid={`transfer-decided-${transfer.id}`}
      className="flex flex-wrap items-center justify-between gap-3 py-2.5"
    >
      <div>
        <div className="flex flex-wrap items-center gap-2">
          <span className="font-mono text-[12.5px] font-semibold text-ink">{transfer.ticker}</span>
          <Chip>{transfer.from_strategy}</Chip>
          <span className="text-ink-faint">→</span>
          <Chip tone="blue">{transfer.to_strategy}</Chip>
        </div>
        <div className="mt-1 flex flex-wrap items-center gap-2 text-[12px] text-ink-dim">
          <span className="font-mono">{transferQuantityLabel(transfer.quantity)}</span>
          <span>{transferOutcome(transfer)}</span>
          {transfer.auto_approved && <span>aprovada automaticamente</span>}
        </div>
      </div>
      <Chip tone={transfer.status === "APPROVED" ? "green" : "rose"}>
        {transferStatusLabel(transfer.status)}
      </Chip>
    </div>
  );
}
