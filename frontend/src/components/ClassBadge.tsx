import type { AssetClass } from "../api/types.ts";
import { assetClassLabel } from "../lib/month.ts";
import { Badge } from "./Badge.tsx";

// The one rendering of a ticker's class: the class label, or the classification residue in the
// critical tone. Shared by the Posições table and the ticker drawer so the two never drift.
export function ClassBadge({ assetClass }: { assetClass: AssetClass | null }) {
  return assetClass === null ? (
    <Badge tone="crit">Sem classe</Badge>
  ) : (
    <Badge tone="neutral">{assetClassLabel(assetClass)}</Badge>
  );
}
