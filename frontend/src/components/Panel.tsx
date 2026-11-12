import type { ReactNode } from "react";

// The console panel: a 3 px surface card on the line border. An optional title is the 9,5 px
// uppercase mono label the console uses above a section, separated by a hairline.
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
    <section className="min-w-0 rounded-panel border border-line bg-panel">
      {(title || action) && (
        <div className="flex items-center gap-2 border-b border-line-soft px-[11px] py-2">
          {title && (
            <h2 className="font-mono text-label font-semibold tracking-[.13em] text-muted uppercase">
              {title}
            </h2>
          )}
          <span className="flex-1" />
          {action}
        </div>
      )}
      <div className="p-[11px]">{children}</div>
    </section>
  );
}
