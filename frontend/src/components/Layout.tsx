import { NavLink, Outlet } from "react-router-dom";

const linkClass = ({ isActive }: { isActive: boolean }) =>
  [
    "px-3 py-1.5 rounded-md text-sm font-medium transition-colors",
    isActive ? "bg-slate-800 text-white" : "text-slate-400 hover:text-slate-200",
  ].join(" ");

export function Layout() {
  return (
    <div className="min-h-screen">
      <header className="border-b border-white/10 bg-slate-900/60 backdrop-blur">
        <div className="mx-auto flex h-14 max-w-5xl items-center gap-2 px-4">
          <NavLink to="/" className="mr-3 flex items-center gap-2">
            <span className="text-lg font-semibold tracking-tight text-yield">▚</span>
            <span className="text-sm font-semibold tracking-wide text-slate-300">portfolio</span>
          </NavLink>
          <nav className="flex items-center gap-1">
            <NavLink to="/" end className={linkClass}>
              Painel
            </NavLink>
            <NavLink to="/carteira" className={linkClass}>
              Carteira
            </NavLink>
            <NavLink to="/bonds/new" className={linkClass}>
              Novo título
            </NavLink>
            <NavLink to="/checking-accounts/new" className={linkClass}>
              Nova conta
            </NavLink>
            <NavLink to="/listed-assets/new" className={linkClass}>
              Novo ativo
            </NavLink>
            <NavLink to="/indexes" className={linkClass}>
              Índices
            </NavLink>
            <NavLink to="/upload" className={linkClass}>
              Importar
            </NavLink>
          </nav>
        </div>
      </header>
      <main className="mx-auto max-w-5xl px-4 py-8">
        <Outlet />
      </main>
    </div>
  );
}
