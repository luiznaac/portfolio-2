import type { ReactNode } from "react";

/** Titled card — the shell every dashboard/detail section sits in. */
export function Panel({
  title,
  action,
  children,
}: {
  title: string;
  action?: ReactNode;
  children: ReactNode;
}) {
  return (
    <section className="rounded-xl border border-white/10 bg-slate-900/50 p-5">
      <div className="mb-4 flex items-center justify-between gap-3">
        <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-400">
          {title}
        </h2>
        {action}
      </div>
      {children}
    </section>
  );
}
