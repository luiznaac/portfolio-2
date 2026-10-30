import type { ReactNode } from "react";

// The reference panel (`.panel`): surface card on the line border. An optional title is the
// condensed uppercase section label the reference uses above/inside panels.
export function Panel({
  title,
  action,
  children,
}: {
  title?: string;
  action?: ReactNode;
  children?: ReactNode;
}) {
  return (
    <section className="rounded-lg border border-line bg-surface px-[18px] py-4">
      {(title || action) && (
        <div className="mb-3 flex items-center justify-between gap-3">
          {title && (
            <h2 className="font-cond text-[13px] font-bold tracking-[.04em] text-ink-dim uppercase">
              {title}
            </h2>
          )}
          {action}
        </div>
      )}
      {children}
    </section>
  );
}
