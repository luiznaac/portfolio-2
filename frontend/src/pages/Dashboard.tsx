import { useHealth } from "../api/queries.ts";
import { PageHead } from "../components/PageHead.tsx";
import { Panel } from "../components/Panel.tsx";
import { formatHealthStatus } from "../lib/format.ts";

// The health-check vertical slice: usecase -> gateway/client -> TanStack Query -> this page. Keep
// this working end-to-end — it's the reference example for "how do I wire a new API call through
// every layer", the frontend equivalent of the health check in the kotlin/python scaffolds. It is
// off-nav but carries a page head so every route in the smoke harness has one.
export function Dashboard() {
  const { data, isLoading, error } = useHealth();

  return (
    <section>
      <PageHead title="Health" subtitle="Verificação ponta a ponta das dependências." />
      {isLoading ? (
        <Panel title="Health">Loading…</Panel>
      ) : error ? (
        <Panel title="Health">Error: {(error as Error).message}</Panel>
      ) : (
        <Panel title="Health">
          <ul className="space-y-1 text-sm">
            {data?.map((check) => (
              <li key={check.service_name} className="flex justify-between">
                <span>{check.service_name}</span>
                <span className={check.is_healthy ? "text-yield" : "text-tax"}>
                  {formatHealthStatus(check.is_healthy)}
                </span>
              </li>
            ))}
          </ul>
        </Panel>
      )}
    </section>
  );
}
