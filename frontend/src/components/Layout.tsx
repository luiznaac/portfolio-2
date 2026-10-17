import { useState } from "react";
import { Outlet, useLocation } from "react-router-dom";
import { navCrumb } from "../lib/navigation.ts";
import { Sidebar } from "./Sidebar.tsx";
import { IconMenu } from "./icons.tsx";

// The shell: collapsing sidebar + a topbar carrying the breadcrumb. The month pill, Ctrl+K palette
// and consolidation-freshness indicator arrive with #26.
export function Layout() {
  const [collapsed, setCollapsed] = useState(false);
  const { pathname } = useLocation();
  const crumb = navCrumb(pathname);

  return (
    <div className="flex h-screen overflow-hidden">
      <Sidebar collapsed={collapsed} />
      <div className="flex min-w-0 flex-1 flex-col overflow-hidden">
        <header className="flex h-[52px] flex-none items-center gap-3.5 border-b border-line bg-surface px-4">
          <button
            type="button"
            title={collapsed ? "Expandir menu" : "Recolher menu"}
            onClick={() => setCollapsed((current) => !current)}
            className="grid h-[30px] w-[30px] flex-none place-items-center rounded-sm border border-line text-ink-dim hover:border-surface-3 hover:text-ink"
          >
            <IconMenu />
          </button>
          {crumb && (
            <div className="text-[13px] text-ink-dim">
              {crumb.group} · <b className="font-semibold text-ink">{crumb.title}</b>
            </div>
          )}
        </header>
        <main className="flex-1 overflow-y-auto px-[26px] pt-[22px] pb-[60px]">
          <div className="mx-auto max-w-[1180px]">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
}
