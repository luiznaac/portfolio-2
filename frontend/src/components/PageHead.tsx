import type { ReactNode } from "react";
import { cn } from "../lib/cn.ts";

// The page head: object title, an optional mono subtitle and caller actions over a fixed floor so
// the sub-view tab strip below never shifts between tabs (issue #366). The subtitle line is always
// reserved and truncated to one line, so the head's height does not depend on whether a tab has a
// subtitle. The title is the console's 17 px/600 sans page title and the page's single h1.
export function PageHead({
  title,
  subtitle,
  actions,
}: {
  title: string;
  subtitle?: string;
  actions?: ReactNode;
}) {
  return (
    <header
      data-testid="page-head"
      className="mb-3 flex min-h-[var(--height-pagehead)] items-start gap-3.5"
    >
      <div className="min-w-0">
        <h1 className="font-sans text-title font-semibold tracking-[-.02em] text-text">{title}</h1>
        <p
          data-testid="page-subtitle"
          aria-hidden={subtitle === undefined}
          className={cn(
            "mt-[3px] max-w-[860px] truncate font-mono text-label text-faint",
            subtitle === undefined && "invisible",
          )}
        >
          {subtitle ?? "—"}
        </p>
      </div>
      <span className="flex-1" />
      {actions}
    </header>
  );
}
