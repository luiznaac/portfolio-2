import type { ReactNode } from "react";
import { NavLink } from "react-router-dom";
import { cn } from "../lib/cn.ts";

export interface PageTab {
  to: string;
  label: string;
  // Set on the object's root tab so it is active only on its own route, not on every sub-view.
  end?: boolean;
}

// The page-tab strip: an object's sub-views as routes, sitting under the PageHead. The strip is
// one hairline with an active underline in the action blue; the optional trailing context sits on
// the same line so the strip never grows a second row.
export function PageTabs({ tabs, trailing }: { tabs: PageTab[]; trailing?: ReactNode }) {
  return (
    <nav
      aria-label="Sub-views"
      data-testid="page-tabs"
      className="mb-3.5 flex items-stretch gap-px border-b border-line"
    >
      {tabs.map((tab) => (
        <NavLink
          key={tab.to}
          to={tab.to}
          end={tab.end}
          className={({ isActive }) =>
            cn(
              "-mb-px border-b-2 px-[11px] py-2 font-mono text-num whitespace-nowrap",
              isActive
                ? "border-action text-action"
                : "border-transparent text-faint hover:text-text",
            )
          }
        >
          {tab.label}
        </NavLink>
      ))}
      <span className="flex-1" />
      {trailing !== undefined && (
        <span className="self-center font-mono text-label text-faint">{trailing}</span>
      )}
    </nav>
  );
}
