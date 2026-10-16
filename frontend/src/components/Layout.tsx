import { Outlet } from "react-router-dom";
import { Sidebar } from "./Sidebar.tsx";

export function Layout() {
  return (
    <div className="flex min-h-screen">
      <Sidebar />
      <main className="flex-1 px-8 py-8">
        <Outlet />
      </main>
    </div>
  );
}
