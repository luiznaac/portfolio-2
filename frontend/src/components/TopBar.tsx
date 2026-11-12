import { Link, useLocation } from "react-router-dom";
import { usePortfolioStatus } from "../api/queries.ts";
import { resolveCrumb } from "../lib/routes.ts";
import { ROUTES } from "../lib/routes.ts";
import { openMonthLabel, quoteFreshnessLabel } from "../lib/status.ts";
import { IconMenu } from "./icons.tsx";

// The shell's top bar: the group / object / sub-view breadcrumb (entity name at depth), the quotes'
// freshness and the open month's status, all read from the status aggregate. There is no command
// palette, no Ctrl/Cmd+K and no cross-object lookup — navigation is the sidebar, page tabs,
// attention deep links and per-list filters.
export function TopBar({
  collapsed,
  onToggleSidebar,
}: {
  collapsed: boolean;
  onToggleSidebar: () => void;
}) {
  const { pathname } = useLocation();
  const crumb = resolveCrumb(pathname);
  const status = usePortfolioStatus();

  return (
    <header className="flex h-[var(--height-topbar)] flex-none items-center gap-3 border-b border-line bg-panel px-4">
      <button
        type="button"
        data-testid="sidebar-toggle"
        title={collapsed ? "Expandir menu" : "Recolher menu"}
        onClick={onToggleSidebar}
        className="grid h-[26px] w-[26px] flex-none place-items-center rounded-sm border border-line text-muted hover:border-panel-3 hover:text-text"
      >
        <IconMenu />
      </button>

      {crumb !== null && (
        <div data-testid="breadcrumb" className="font-mono text-[11px] tracking-[.02em] text-faint">
          {crumb.group} / {crumb.object} / <b className="font-semibold text-text">{crumb.view}</b>
        </div>
      )}

      <span className="flex-1" />

      <span
        data-testid="quote-freshness"
        className="flex items-center gap-1.5 font-mono text-[10.5px] text-muted"
      >
        <i className="block h-[5px] w-[5px] rounded-full bg-return-up" />
        {status.data === undefined ? "…" : quoteFreshnessLabel(status.data)}
      </span>

      <Link
        to={ROUTES.plan}
        data-testid="open-month"
        className="rounded-xs border border-line bg-panel-2 px-2.5 py-1.5 font-mono text-[10.5px] text-muted hover:border-panel-3 hover:text-text"
      >
        {status.data === undefined ? "…" : openMonthLabel(status.data)}
      </Link>
    </header>
  );
}
