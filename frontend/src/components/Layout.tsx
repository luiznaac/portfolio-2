import { useEffect, useState } from "react";
import { Outlet, useLocation } from "react-router-dom";
import { navCrumb } from "../lib/navigation.ts";
import { CommandPalette } from "./CommandPalette.tsx";
import { MonthPill } from "./MonthPill.tsx";
import { Sidebar } from "./Sidebar.tsx";
import { IconMenu } from "./icons.tsx";

// The shell: collapsing sidebar + a topbar carrying the breadcrumb, the month pill and the command
// palette trigger. Ctrl/Cmd+K is bound here; the palette owns its own list, filtering and keys.
export function Layout() {
  const [collapsed, setCollapsed] = useState(false);
  const [paletteOpen, setPaletteOpen] = useState(false);
  const { pathname } = useLocation();
  const crumb = navCrumb(pathname);

  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent) {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") {
        event.preventDefault();
        setPaletteOpen(true);
      }
    }
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, []);

  return (
    <div className="flex h-screen overflow-hidden">
      <Sidebar collapsed={collapsed} />
      <div className="flex min-w-0 flex-1 flex-col overflow-hidden">
        <header className="flex h-[52px] flex-none items-center gap-3.5 border-b border-line bg-surface px-4">
          <button
            type="button"
            data-testid="sidebar-toggle"
            title={collapsed ? "Expandir menu" : "Recolher menu"}
            onClick={() => setCollapsed((current) => !current)}
            className="grid h-[30px] w-[30px] flex-none place-items-center rounded-sm border border-line text-ink-dim hover:border-surface-3 hover:text-ink"
          >
            <IconMenu />
          </button>
          {crumb && (
            <div data-testid="breadcrumb" className="text-[13px] text-ink-dim">
              {crumb.group} · <b className="font-semibold text-ink">{crumb.title}</b>
            </div>
          )}
          <div className="flex-1" />
          <MonthPill />
          <button
            type="button"
            data-testid="command-trigger"
            onClick={() => setPaletteOpen(true)}
            className="flex items-center gap-2 rounded-md border border-line bg-surface-2 px-2.5 py-1.5 text-[12px] text-ink-dim hover:border-surface-3 hover:text-ink"
          >
            Comandos
            <kbd className="rounded-xs bg-surface-3 px-1 font-mono text-[10.5px] text-ink-faint">
              Ctrl K
            </kbd>
          </button>
        </header>
        <main className="flex-1 overflow-y-auto px-[26px] pt-[22px] pb-[60px]">
          <div className="mx-auto max-w-[1180px]">
            <Outlet />
          </div>
        </main>
      </div>
      {paletteOpen && <CommandPalette onClose={() => setPaletteOpen(false)} />}
    </div>
  );
}
