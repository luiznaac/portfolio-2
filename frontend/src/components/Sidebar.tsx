import type { ComponentType, SVGProps } from "react";
import { NavLink } from "react-router-dom";
import { cn } from "../lib/cn.ts";
import { NAV_GROUPS, type NavIcon } from "../lib/navigation.ts";
import {
  BrandMark,
  IconAssets,
  IconBank,
  IconCalendar,
  IconCoins,
  IconEvent,
  IconLayers,
  IconTarget,
  IconUpload,
} from "./icons.tsx";

// The reference icons are named in the nav table and resolved here.
const ICONS: Record<NavIcon, ComponentType<SVGProps<SVGSVGElement>>> = {
  upload: IconUpload,
  assets: IconAssets,
  bank: IconBank,
  calendar: IconCalendar,
  target: IconTarget,
  layers: IconLayers,
  coins: IconCoins,
  event: IconEvent,
};

// The shell's primary navigation: brand mark, grouped items, 236px wide collapsing to 60px. Groups
// with no shipped routes yet ("Resultados") are skipped until their pages land.
export function Sidebar({ collapsed }: { collapsed: boolean }) {
  return (
    <aside
      className={cn(
        "flex shrink-0 flex-col overflow-hidden border-r border-line bg-surface",
        collapsed ? "w-[60px]" : "w-[236px]",
      )}
    >
      <div className="flex h-[52px] flex-none items-center gap-2.5 border-b border-line-soft px-4">
        <BrandMark />
        {!collapsed && (
          <b className="font-cond text-[15px] font-bold tracking-[.01em] whitespace-nowrap">
            Carteira Ideal
          </b>
        )}
      </div>
      <nav className="flex-1 overflow-y-auto py-2.5">
        {NAV_GROUPS.filter((group) => group.items.length > 0).map((group) => (
          <div key={group.label} className="py-1.5">
            <h5
              className={cn(
                "px-[18px] pb-1 font-mono text-[10px] font-medium tracking-[.12em] text-ink-faint uppercase",
                collapsed && "invisible",
              )}
            >
              {group.label}
            </h5>
            {group.items.map((item) => {
              const Icon = ICONS[item.icon];
              return (
                <NavLink
                  key={item.to}
                  to={item.to}
                  className={({ isActive }) =>
                    cn(
                      "my-px flex items-center gap-2.5 border-l-2 text-[13.5px] whitespace-nowrap",
                      collapsed ? "justify-center px-0 py-2.5" : "px-[18px] py-[7px]",
                      isActive
                        ? "border-accent bg-surface-2 text-ink"
                        : "border-transparent text-ink-dim hover:bg-surface-2 hover:text-ink",
                    )
                  }
                >
                  {({ isActive }) => (
                    <>
                      <Icon className={isActive ? "text-accent" : "opacity-80"} />
                      {!collapsed && <span>{item.label}</span>}
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
