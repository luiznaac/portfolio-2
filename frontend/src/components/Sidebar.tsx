import { NavLink } from "react-router-dom";

// The shell's primary navigation. `Mês` is the only group shipped so far — Cockpit, Ordens and
// Fechamento join it as their pages land, alongside the Carteira and Resultados groups.
const groups = [
  {
    label: "Mês",
    items: [
      { to: "/imports", label: "Importar extrato" },
      { to: "/trades", label: "Ativos" },
    ],
  },
];

export function Sidebar() {
  return (
    <aside className="flex w-[236px] shrink-0 flex-col border-r border-slate-800 bg-slate-900/50 px-3 py-6">
      <div className="mb-8 px-2 text-sm font-semibold tracking-wide text-slate-100">
        Carteira Ideal
      </div>
      <nav className="space-y-6">
        {groups.map((group) => (
          <div key={group.label}>
            <p className="px-2 pb-1 text-xs font-medium tracking-wide text-slate-500 uppercase">
              {group.label}
            </p>
            <div className="space-y-1">
              {group.items.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  className={({ isActive }) =>
                    [
                      "block rounded-md px-2 py-1.5 text-sm transition-colors",
                      isActive
                        ? "bg-slate-800 text-slate-100"
                        : "text-slate-400 hover:bg-slate-800/60 hover:text-slate-200",
                    ].join(" ")
                  }
                >
                  {item.label}
                </NavLink>
              ))}
            </div>
          </div>
        ))}
      </nav>
    </aside>
  );
}
