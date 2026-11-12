import { useState } from "react";
import { Outlet } from "react-router-dom";
import { Sidebar } from "./Sidebar.tsx";
import { TopBar } from "./TopBar.tsx";

// The shell: the six-object sidebar beside a sticky top bar and the routed page. Navigation lives
// entirely in the objects, the page tabs and attention deep links; the retired command palette has
// no trigger and no shortcut here.
export function Layout() {
  const [collapsed, setCollapsed] = useState(false);

  return (
    <div className="flex h-screen overflow-hidden">
      <Sidebar collapsed={collapsed} />
      <div className="flex min-w-0 flex-1 flex-col overflow-hidden">
        <TopBar collapsed={collapsed} onToggleSidebar={() => setCollapsed((current) => !current)} />
        <main className="flex-1 overflow-y-auto px-5 pt-4 pb-14">
          <div className="mx-auto max-w-[1620px]">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
}
