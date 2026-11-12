import { type ReactNode, createContext, useContext, useState } from "react";
import {
  DEFAULT_WINDOW_PRESET,
  type WindowBounds,
  type WindowPreset,
  type WindowRange,
  clampPeriod,
  defaultAnchor,
  shiftAnchor,
  windowBounds,
  windowRange,
} from "../lib/analysisWindow.ts";

// The Análise area's shared window state. Mounted once at the `/analysis` route element, so a switch
// between Performance, Proventos and Impostos keeps the preset and the month anchor, while leaving
// the area unmounts the provider and the next visit opens on the default window again. There is no
// URL or storage layer: "no cross-visit memory" is the mount lifetime, not a clearing effect.

interface AnalysisWindowState {
  preset: WindowPreset;
  // Null until the owner touches the rail; the effective anchor is the last closed month.
  anchor: string | null;
}

interface AnalysisWindowContextValue extends AnalysisWindowState {
  setPreset: (preset: WindowPreset) => void;
  setAnchor: (anchor: string) => void;
}

const AnalysisWindowContext = createContext<AnalysisWindowContextValue | null>(null);

export function AnalysisWindowProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AnalysisWindowState>({
    preset: DEFAULT_WINDOW_PRESET,
    anchor: null,
  });

  return (
    <AnalysisWindowContext.Provider
      value={{
        preset: state.preset,
        anchor: state.anchor,
        setPreset: (preset) => setState((previous) => ({ ...previous, preset })),
        setAnchor: (anchor) => setState((previous) => ({ ...previous, anchor })),
      }}
    >
      {children}
    </AnalysisWindowContext.Provider>
  );
}

export interface AnalysisWindow {
  preset: WindowPreset;
  anchor?: string;
  range?: WindowRange;
  bounds?: WindowBounds;
  setPreset: (preset: WindowPreset) => void;
  // One month per arrow, clamped to the months the read holds.
  shift: (delta: number) => void;
}

// Resolves the shared state against the periods the active tab actually carries. The last closed
// month is the default anchor; the computed history has no close, so the newest series month stands
// in. A preset/anchor pair whose anchor fell outside the data is clamped at render, never trusted.
export function useAnalysisWindow(periods: string[], lastClosedPeriod?: string): AnalysisWindow {
  const context = useContext(AnalysisWindowContext);
  if (context === null) {
    throw new Error("useAnalysisWindow must be used inside AnalysisWindowProvider");
  }

  const bounds = windowBounds(periods);
  const fallback = defaultAnchor(periods, lastClosedPeriod);
  const anchor =
    bounds === undefined || fallback === undefined
      ? undefined
      : clampPeriod(context.anchor ?? fallback, bounds);

  return {
    preset: context.preset,
    anchor,
    range: anchor === undefined ? undefined : windowRange(context.preset, anchor),
    bounds,
    setPreset: context.setPreset,
    shift: (delta) => {
      if (anchor === undefined || bounds === undefined) return;
      context.setAnchor(shiftAnchor(anchor, delta, bounds));
    },
  };
}
