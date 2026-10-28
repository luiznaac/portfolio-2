import type { AssetClass } from "../api/types.ts";
import type { AllocationStripSegment } from "../lib/cockpit.ts";

// The class palette, following the reference: Ações principal (blue), FIIs violet, RF yield (green).
const CLASS_COLOR: Record<AssetClass, string> = {
  ACOES: "bg-principal",
  FIIS: "bg-violet",
  RENDA_FIXA: "bg-yield",
};

// The compact class-level bar: each class's Actual share, with a dark marker at its cumulative
// Ideal. The labels row below names the classes in the same order.
export function AllocationStrip({ segments }: { segments: AllocationStripSegment[] }) {
  if (segments.length === 0) {
    return <p className="text-[13px] text-ink-dim">Alocação ainda não disponível.</p>;
  }

  return (
    <div>
      <div data-testid="allocation-strip" className="relative">
        <div className="flex h-[30px] overflow-hidden rounded-md border border-line">
          {segments.map((segment) => (
            <div
              key={segment.assetClass}
              style={{ width: `${segment.actualPercent}%` }}
              className={`flex h-full items-center justify-center ${CLASS_COLOR[segment.assetClass]}`}
            >
              {segment.actualPercent >= 8 && (
                <span className="font-mono text-[10.5px] font-semibold text-ground">
                  {segment.actualPercent.toFixed(1).replace(".", ",")}%
                </span>
              )}
            </div>
          ))}
        </div>
        {segments.map((segment) =>
          segment.idealPercent === undefined ? null : (
            <div
              key={`marker-${segment.assetClass}`}
              style={{
                // Keep the 2px marker's centre inside the bar so the 100% boundary stays visible.
                left: `calc(${Math.min(99.5, Math.max(0.5, segment.idealPercent))}% - 1px)`,
              }}
              className="pointer-events-none absolute inset-y-0 z-10 w-0.5 bg-ink"
            />
          ),
        )}
      </div>
      <div className="mt-2 flex justify-between gap-2 text-[11px] text-ink-faint">
        {segments.map((segment) => (
          <span key={segment.assetClass}>{segment.label}</span>
        ))}
      </div>
    </div>
  );
}
