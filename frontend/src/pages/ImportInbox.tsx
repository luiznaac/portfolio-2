import { Link } from "react-router-dom";
import { Panel } from "../components/Panel.tsx";
import { ResumableImportStrip } from "../components/ResumableImportStrip.tsx";
import { assistantPath } from "../lib/importDestinations.ts";

// Caixa de entrada: the parked sessions and the one upload entry. The full queue, per-source status
// and history land with their own slices; this is the resume surface the assistant parks into.
export function ImportInbox() {
  return (
    <section className="space-y-4">
      <Panel
        title="Caixa de entrada"
        action={
          <Link
            to={assistantPath("TRADES")}
            className="text-[12px] font-medium text-accent hover:underline"
          >
            Enviar arquivo →
          </Link>
        }
      >
        <p className="text-[12.5px] text-ink-dim">
          Todo upload de extrato B3 abre o mesmo assistente de quatro etapas. O arquivo é lido e
          fica em revisão; nada é aplicado até a última etapa. Sair no meio guarda a sessão aqui.
        </p>
      </Panel>

      <ResumableImportStrip />
    </section>
  );
}
