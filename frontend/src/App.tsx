import type { RouteObject } from "react-router-dom";
import { Layout } from "./components/Layout.tsx";
import { Allocation } from "./pages/Allocation.tsx";
import { AssetDetail } from "./pages/AssetDetail.tsx";
import { CorporateActions } from "./pages/CorporateActions.tsx";
import { Dashboard } from "./pages/Dashboard.tsx";
import { FixedIncome } from "./pages/FixedIncome.tsx";
import { ImportWizard } from "./pages/ImportWizard.tsx";
import { Income } from "./pages/Income.tsx";
import { Month } from "./pages/Month.tsx";
import { Orders } from "./pages/Orders.tsx";
import { Strategies } from "./pages/Strategies.tsx";
import { Taxes } from "./pages/Taxes.tsx";
import { Trades } from "./pages/Trades.tsx";

export const routes: RouteObject[] = [
  {
    path: "/",
    element: <Layout />,
    children: [
      { index: true, element: <Dashboard /> },
      { path: "month", element: <Month /> },
      { path: "orders", element: <Orders /> },
      { path: "allocation", element: <Allocation /> },
      { path: "strategies", element: <Strategies /> },
      { path: "strategies/import", element: <Strategies /> },
      { path: "imports", element: <ImportWizard /> },
      // Not `/assets`: the production nginx serves the built bundle's `/assets/` static
      // directory, which would shadow a sibling SPA route of that name on a deep link.
      { path: "trades", element: <Trades /> },
      { path: "trades/:ticker", element: <AssetDetail /> },
      { path: "fixed-income", element: <FixedIncome /> },
      { path: "corporate-actions", element: <CorporateActions /> },
      { path: "income", element: <Income /> },
      { path: "taxes", element: <Taxes /> },
    ],
  },
];
