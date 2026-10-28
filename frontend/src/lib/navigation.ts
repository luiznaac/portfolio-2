// The shell's navigation table — the sidebar groups and the topbar breadcrumb both resolve from it,
// so a route lands in both at once. Pure (no React): icons are named here and resolved to SVG in
// Sidebar.tsx, which keeps the crumb lookup unit-tested in src/lib.

export type NavIcon =
  | "grid"
  | "upload"
  | "assets"
  | "bank"
  | "calendar"
  | "target"
  | "layers"
  | "coins"
  | "event"
  | "receipt"
  | "list"
  | "lock"
  | "chart";

export interface NavItem {
  to: string;
  label: string;
  icon: NavIcon;
}

export interface NavGroup {
  label: string;
  items: NavItem[];
}

// The reference's three groups (frontend/design/front-prototype.html); items appear as their routes
// land. The Cockpit is the root route; "Capital e alvos" stays beside it because it owns the month
// opener the Cockpit points at when no month is open. Sidebar renders only groups that have items.
export const NAV_GROUPS: NavGroup[] = [
  {
    label: "Mês",
    items: [
      { to: "/", label: "Cockpit", icon: "grid" },
      { to: "/month", label: "Capital e alvos", icon: "calendar" },
      { to: "/orders", label: "Ordens", icon: "list" },
      { to: "/imports", label: "Importar extrato", icon: "upload" },
      { to: "/fechamento", label: "Fechamento", icon: "lock" },
    ],
  },
  {
    label: "Carteira",
    items: [
      { to: "/allocation", label: "Alocação", icon: "target" },
      { to: "/strategies", label: "Estratégias", icon: "layers" },
      { to: "/trades", label: "Ativos", icon: "assets" },
      { to: "/corporate-actions", label: "Eventos corporativos", icon: "event" },
      { to: "/fixed-income", label: "Renda fixa", icon: "bank" },
    ],
  },
  {
    label: "Resultados",
    items: [
      { to: "/income", label: "Proventos", icon: "coins" },
      { to: "/taxes", label: "Impostos", icon: "receipt" },
      { to: "/performance", label: "Performance", icon: "chart" },
    ],
  },
];

export interface NavCrumb {
  group: string;
  title: string;
}

// Resolves the current path to its group and item label for the topbar breadcrumb. A path matches an
// item's route exactly or as a nested child (`/imports/42`), never by bare prefix (`/tradesx`).
export function navCrumb(pathname: string): NavCrumb | null {
  for (const group of NAV_GROUPS) {
    const item = group.items.find((it) => matches(it.to, pathname));
    if (item) return { group: group.label, title: item.label };
  }
  return null;
}

function matches(to: string, pathname: string): boolean {
  return pathname === to || pathname.startsWith(`${to}/`);
}
