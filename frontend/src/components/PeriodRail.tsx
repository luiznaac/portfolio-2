import type { ReactNode } from "react";
import {
  WINDOW_PRESETS,
  type WindowBounds,
  type WindowPreset,
  type WindowRange,
  windowRangeLabel,
} from "../lib/analysisWindow.ts";
import { cn } from "../lib/cn.ts";
import { monthPeriodLabel } from "../lib/month.ts";

function RailArrow({
  label,
  disabled,
  onClick,
  children,
}: {
  label: string;
  disabled: boolean;
  onClick: () => void;
  children: ReactNode;
}) {
  return (
    <button
      type="button"
      aria-label={label}
      onClick={onClick}
      disabled={disabled}
      className="rounded-sm border border-line bg-panel-2 px-1.5 py-1 font-mono text-[12px] text-muted hover:text-text disabled:opacity-40"
    >
      {children}
    </button>
  );
}

// The one period control the Análise tabs share: presets, a month arrow each side and the month
// anchor. Performance renders it now; Proventos and Impostos adopt it in their own slices. It only
// describes the window; the provider owns the state so a tab switch keeps it.
export function PeriodRail({
  preset,
  anchor,
  range,
  bounds,
  onPreset,
  onShift,
  grainNote,
}: {
  preset: WindowPreset;
  anchor: string;
  range: WindowRange;
  bounds?: WindowBounds;
  onPreset: (preset: WindowPreset) => void;
  onShift: (delta: number) => void;
  // Impostos states its monthly grain here; Performance and Proventos leave it empty.
  grainNote?: string;
}) {
  const atFirst = bounds !== undefined && anchor === bounds.first;
  const atLast = bounds !== undefined && anchor === bounds.last;

  return (
    <div
      data-testid="period-rail"
      className="mb-3.5 flex flex-wrap items-center gap-2 rounded-md border border-line bg-panel px-3 py-2"
    >
      <span className="font-mono text-label tracking-[.08em] text-faint uppercase">Período</span>

      <fieldset
        aria-label="Período"
        className="m-0 flex overflow-hidden rounded-sm border border-line p-0"
      >
        {WINDOW_PRESETS.map((option) => (
          <button
            key={option.key}
            type="button"
            data-testid={`rail-preset-${option.key}`}
            aria-pressed={preset === option.key}
            onClick={() => onPreset(option.key)}
            className={cn(
              "border-l border-line px-2 py-1 font-mono text-[11px] first:border-l-0",
              preset === option.key
                ? "bg-action-bg text-action"
                : "bg-panel-2 text-muted hover:text-text",
            )}
          >
            {option.label}
          </button>
        ))}
      </fieldset>

      <RailArrow label="Um mês antes" disabled={atFirst} onClick={() => onShift(-1)}>
        ‹
      </RailArrow>

      <span data-testid="rail-range" className="font-mono text-[12.5px] text-text">
        <span data-testid="rail-window">{windowRangeLabel(range)}</span>
        <small className="block font-sans text-[10.5px] text-faint">
          {monthPeriodLabel(anchor)} é o mês-âncora
        </small>
      </span>

      <RailArrow label="Um mês depois" disabled={atLast} onClick={() => onShift(1)}>
        ›
      </RailArrow>

      <span className="flex-1" />

      {grainNote !== undefined && <span className="text-[10.5px] text-muted">{grainNote}</span>}
      <span className="hidden rounded-full border border-line px-2 py-0.5 text-[10px] text-faint lg:inline">
        janela da Análise
      </span>
    </div>
  );
}
