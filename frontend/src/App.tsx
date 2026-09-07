import type { RouteObject } from "react-router-dom";
import { Layout } from "./components/Layout.tsx";
import { Dashboard } from "./pages/Dashboard.tsx";
import { BondPage } from "./pages/BondPage.tsx";
import { NewBond } from "./pages/NewBond.tsx";
import { CheckingAccountPage } from "./pages/CheckingAccountPage.tsx";
import { NewCheckingAccount } from "./pages/NewCheckingAccount.tsx";
import { Indexes } from "./pages/Indexes.tsx";
import { Upload } from "./pages/Upload.tsx";

export const routes: RouteObject[] = [
  {
    path: "/",
    element: <Layout />,
    children: [
      { index: true, element: <Dashboard /> },
      { path: "bonds/new", element: <NewBond /> },
      { path: "bonds/:id", element: <BondPage /> },
      { path: "checking-accounts/new", element: <NewCheckingAccount /> },
      { path: "checking-accounts/:id", element: <CheckingAccountPage /> },
      { path: "indexes", element: <Indexes /> },
      { path: "upload", element: <Upload /> },
    ],
  },
];
