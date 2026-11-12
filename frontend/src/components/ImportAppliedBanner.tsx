import { useLocation } from "react-router-dom";
import type { ImportApplyResult } from "../api/types.ts";
import { ImportOutcomeSummary } from "./ImportOutcomeSummary.tsx";
import { ImportProvenance } from "./ImportProvenance.tsx";

interface AppliedNavigationState {
  importApplied?: ImportApplyResult;
}

// The apply lands on the destination object with its outcome and provenance — file name, sha256
// prefix and the retained original.
export function ImportAppliedBanner() {
  const location = useLocation();
  const result = (location.state as AppliedNavigationState | null)?.importApplied;
  if (result === undefined) return null;

  return (
    <div
      data-testid="import-applied"
      className="rounded-sm border-l-[3px] border-yield bg-surface-2 px-3 py-2.5 text-[12.5px] text-ink"
    >
      <ImportOutcomeSummary
        applied={result.applied}
        rejected={result.rejected}
        skippedDuplicate={result.skipped_duplicate}
      />
      <p className="mt-1">
        <ImportProvenance record={result.record} className="text-ink-dim" />
      </p>
    </div>
  );
}
