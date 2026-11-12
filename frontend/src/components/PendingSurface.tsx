import { Panel } from "./Panel.tsx";

// A registered sub-view whose own slice has not landed yet. It keeps the route reachable and the
// page head/tab strip intact, so the object shell never carries a dead link.
export function PendingSurface() {
  return (
    <Panel title="Ainda não construído">
      <p className="text-[12.5px] text-muted">
        Esta superfície chega na próxima fatia do redesenho. Até lá, o restante da navegação
        continua disponível.
      </p>
    </Panel>
  );
}
