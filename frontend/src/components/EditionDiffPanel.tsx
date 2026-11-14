import type { EditionDiff } from "../api/types.ts";
import { ratingLabel } from "../lib/strategy.ts";
import { Chip } from "./Chip.tsx";
import { Panel } from "./Panel.tsx";

// The edition diff as console chips — entered, exited and the weight/rating moves — shown on the
// strategy detail's Edições tab.
export function EditionDiffPanel({ diff }: { diff: EditionDiff }) {
  const hasMoves = diff.entered.length + diff.exited.length + diff.changed.length > 0;

  return (
    <Panel title="Mudanças vs. edição anterior">
      <div className="flex flex-wrap gap-1.5">
        {diff.entered.map((entry) => (
          <Chip key={`entered-${entry.ticker}`} tone="green">
            + {entry.ticker} {entry.weight}%
          </Chip>
        ))}
        {diff.exited.map((entry) => (
          <Chip key={`exited-${entry.ticker}`} tone="rose">
            − {entry.ticker}
          </Chip>
        ))}
        {diff.changed.map((change) => (
          <Chip key={`changed-${change.ticker}`} tone="neutral">
            {change.ticker} {change.weight_before}%→{change.weight_after}%
            {change.rating_before === change.rating_after
              ? ""
              : ` · ${ratingLabel(change.rating_before)}→${ratingLabel(change.rating_after)}`}
          </Chip>
        ))}
        {!hasMoves && <span className="text-[12.5px] text-muted">Sem mudanças.</span>}
      </div>
    </Panel>
  );
}
