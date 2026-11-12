import type { ComponentType, SVGProps } from "react";
import { NavLink } from "react-router-dom";
import { usePortfolioStatus } from "../api/queries.ts";
import { cn } from "../lib/cn.ts";
import { NAV_GROUPS, type NavIcon } from "../lib/routes.ts";
import { attentionBadges } from "../lib/status.ts";
import {
  BrandMark,
  IconAssets,
  IconCalendar,
  IconChart,
  IconLayers,
  IconTarget,
  IconUpload,
} from "./icons.tsx";

// The reference icons are named in the route table and resolved here.
const ICONS: Record<NavIcon, ComponentType<SVGProps<SVGSVGElement>>> = {
  target: IconTarget,
  layers: IconLayers,
  assets: IconAssets,
  upload: IconUpload,
  calendar: IconCalendar,
  chart: IconChart,
};

// The shell's primary navigation: exactly the six objects in their three groups, never a sub-view.
// Each object links to its first sub-view and stays active across its own tabs. The counts mirror
// the status aggregate's attention queue; an amber badge marks an object holding an action, a muted
// one an object with only watches.
export function Sidebar({ collapsed }: { collapsed: boolean }) {
  const status = usePortfolioStatus();
  const badges = attentionBadges(status.data?.attention ?? []);

  return (
    <aside
      data-testid="sidebar"
      className={cn(
        "flex shrink-0 flex-col overflow-hidden border-r border-line bg-panel",
        collapsed ? "w-[52px]" : "w-[var(--width-sidebar)]",
      )}
    >
      <div className="flex h-[var(--height-topbar)] flex-none items-center gap-2.5 border-b border-line-soft px-3.5">
        <BrandMark />
        {!collapsed && (
          <b className="font-cond text-[14px] font-bold tracking-[.01em] whitespace-nowrap">
            Carteira Ideal
          </b>
        )}
      </div>
      <nav className="flex-1 overflow-y-auto py-2">
        {NAV_GROUPS.map((group) => (
          <div key={group.label} className="py-1.5">
            <h5
              className={cn(
                "px-3.5 pb-1 font-mono text-label font-semibold tracking-[.14em] text-faint uppercase",
                collapsed && "invisible",
              )}
            >
              {group.label}
            </h5>
            {group.objects.map((object) => {
              const Icon = ICONS[object.icon];
              const badge = badges[object.key];
              return (
                <NavLink
                  key={object.key}
                  to={object.root}
                  data-testid={`nav-${object.key}`}
                  className={({ isActive }) =>
                    cn(
                      "relative my-px flex items-center gap-2 rounded-sm text-[12px] whitespace-nowrap",
                      collapsed ? "justify-center px-0 py-2" : "px-2.5 py-[6px]",
                      isActive
                        ? "bg-action-bg font-semibold text-action"
                        : "text-muted hover:bg-panel-2 hover:text-text",
                    )
                  }
                >
                  {({ isActive }) => (
                    <>
                      <Icon className={isActive ? "text-action" : "opacity-80"} />
                      {!collapsed && <span>{object.label}</span>}
                      {badge !== undefined && badge.count > 0 && (
                        <span
                          data-testid={`nav-badge-${object.key}`}
                          data-severity={badge.action ? "ACTION" : "WATCH"}
                          aria-label={`${badge.count} atenção em ${object.label}${
                            badge.action ? ", com ação pendente" : ""
                          }`}
                          className={cn(
                            "rounded-full font-mono text-label font-semibold",
                            collapsed ? "absolute px-1" : "ml-auto min-w-[16px] px-1 text-center",
                            badge.action ? "bg-attention text-bg" : "bg-panel-3 text-muted",
                          )}
                        >
                          {badge.count}
                        </span>
                      )}
                    </>
                  )}
                </NavLink>
              );
            })}
          </div>
        ))}
      </nav>
    </aside>
  );
}
