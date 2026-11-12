import { Navigate, type RouteObject } from "react-router-dom";
import { Layout } from "./components/Layout.tsx";
import { ObjectPage } from "./components/ObjectPage.tsx";
import { PendingSurface } from "./components/PendingSurface.tsx";
import { AnalysisWindowProvider } from "./hooks/useAnalysisWindow.tsx";
import { ROUTES } from "./lib/routes.ts";
import { Allocation } from "./pages/Allocation.tsx";
import { CorporateActions } from "./pages/CorporateActions.tsx";
import { Dashboard } from "./pages/Dashboard.tsx";
import { Fechamento } from "./pages/Fechamento.tsx";
import { FixedIncome } from "./pages/FixedIncome.tsx";
import { History } from "./pages/History.tsx";
import { ImportAssistant } from "./pages/ImportAssistant.tsx";
import { ImportHistory } from "./pages/ImportHistory.tsx";
import { ImportInbox } from "./pages/ImportInbox.tsx";
import { Income } from "./pages/Income.tsx";
import { Month } from "./pages/Month.tsx";
import { Orders } from "./pages/Orders.tsx";
import { Overview } from "./pages/Overview.tsx";
import { Performance } from "./pages/Performance.tsx";
import { PortfolioHistory } from "./pages/PortfolioHistory.tsx";
import { PortfolioHistoryDetail } from "./pages/PortfolioHistoryDetail.tsx";
import { Positions } from "./pages/Positions.tsx";
import { Strategies } from "./pages/Strategies.tsx";
import { StrategyDetail } from "./pages/StrategyDetail.tsx";
import { Taxes } from "./pages/Taxes.tsx";
import { Trades } from "./pages/Trades.tsx";

// The six-object route table (issue #369). Every object's sub-views are page tabs under an
// ObjectPage frame; sub-views whose own slice has not landed yet render PendingSurface so no
// surface is stranded. Production screens are remounted under their new homes until their slice
// replaces them. Detail routes (strategy detail, frozen month, the import assistant) land with
// their own slices and extend the smoke harness beside their page. The landing is Carteira ·
// Visão geral; health stays off-nav.
export const routes: RouteObject[] = [
  {
    path: "/",
    element: <Layout />,
    children: [
      { index: true, element: <Navigate to={ROUTES.portfolio} replace /> },
      // The scaffold health-check slice stays reachable (and keeps its end-to-end example) off-nav.
      { path: "health", element: <Dashboard /> },
      {
        path: "portfolio",
        element: <ObjectPage objectKey="portfolio" />,
        children: [
          { index: true, element: <Overview /> },
          { path: "allocation", element: <Allocation /> },
          { path: "history", element: <PortfolioHistory /> },
        ],
      },
      // The frozen month is a detail with its own page head and the object's tab strip, so it sits
      // beside the object frame instead of inside its tabbed children (#372).
      { path: "portfolio/history/:period", element: <PortfolioHistoryDetail /> },
      {
        path: "strategies",
        element: <ObjectPage objectKey="strategies" />,
        children: [
          { index: true, element: <Strategies /> },
          { path: "import", element: <Strategies /> },
        ],
      },
      // The row target is a depth page with its own head and its own tab strip, so it sits beside
      // the object frame instead of inside its tabbed children (#374).
      { path: "strategies/:id/:tab?", element: <StrategyDetail /> },
      {
        path: "ativos",
        element: <ObjectPage objectKey="assets" />,
        children: [
          { index: true, element: <Navigate to="positions" replace /> },
          // One page for the list and its ticker drawer: the optional segment is the deep link
          // Alocação and attention use, and the same route element keeps the page filters alive
          // while the drawer opens.
          { path: "positions/:ticker?", element: <Positions /> },
          { path: "trades", element: <Trades /> },
          { path: "fixed", element: <FixedIncome /> },
          { path: "quotes", element: <PendingSurface /> },
          { path: "gaps", element: <History /> },
        ],
      },
      {
        path: "imports",
        element: <ObjectPage objectKey="imports" />,
        children: [
          { index: true, element: <ImportInbox /> },
          { path: "sources", element: <PendingSurface /> },
          { path: "corporate", element: <CorporateActions /> },
          { path: "history", element: <ImportHistory /> },
        ],
      },
      {
        path: "plan",
        element: <ObjectPage objectKey="plan" />,
        children: [
          { index: true, element: <Month /> },
          // The production Orders screen carries both the order list and the transfer inbox until
          // the dedicated Transferências slice lands.
          { path: "orders", element: <Orders /> },
          { path: "transfers", element: <Orders /> },
          { path: "close", element: <Fechamento /> },
        ],
      },
      {
        path: "analysis",
        // The provider is mounted on the object route, so the shared rail survives a switch between
        // Performance, Proventos and Impostos but resets when the owner leaves the area.
        element: (
          <AnalysisWindowProvider>
            <ObjectPage objectKey="analysis" />
          </AnalysisWindowProvider>
        ),
        children: [
          { index: true, element: <Performance /> },
          { path: "income", element: <Income /> },
          { path: "tax", element: <Taxes /> },
        ],
      },
    ],
  },
  // The import assistant is a full-screen flow, not a page under the shell: it owns the viewport
  // while a session is open and lands on its destination when the apply finishes.
  { path: "imports/assistant", element: <ImportAssistant /> },
  { path: "imports/assistant/:importId", element: <ImportAssistant /> },
];
