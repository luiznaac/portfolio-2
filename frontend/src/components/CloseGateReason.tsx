import { cn } from "../lib/cn.ts";
import type { CloseGateView } from "../lib/fechamento.ts";

// The one gate sentence Fechamentos and step 6 render. The rule and its copy live in
// lib/fechamento.ts; this component owns only the shared presentation.
export function CloseGateReason({ gate }: { gate: CloseGateView }) {
  return (
    <div data-testid="close-gate-reason" className="mb-3">
      <h3
        className={cn(
          "font-cond text-[13.5px] font-semibold",
          gate.canClose && !gate.blocked ? "text-return-up" : "text-attention",
        )}
      >
        {gate.title}
      </h3>
      <p className="mt-1 text-[12px] leading-[1.5] text-ink-dim">{gate.detail}</p>
    </div>
  );
}

export const CLOSE_NON_BLOCKING_NOTE =
  "Só a transferência pendente recusa o fechamento. Ordens não executadas, lacunas, ações corporativas em revisão, cotações desatualizadas e proventos a receber congelam como estão.";
