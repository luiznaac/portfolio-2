import type { RouteObject } from "react-router-dom";
import { Layout } from "./components/Layout.tsx";
import { BondPage } from "./pages/BondPage.tsx";
import { Carteira } from "./pages/Carteira.tsx";
import { CheckingAccountPage } from "./pages/CheckingAccountPage.tsx";
import { Dashboard } from "./pages/Dashboard.tsx";
import { Estrategias } from "./pages/Estrategias.tsx";
import { Indexes } from "./pages/Indexes.tsx";
import { ListedAssetPage } from "./pages/ListedAssetPage.tsx";
import { NewBond } from "./pages/NewBond.tsx";
import { NewCheckingAccount } from "./pages/NewCheckingAccount.tsx";
import { NewListedAsset } from "./pages/NewListedAsset.tsx";
import { Ordens } from "./pages/Ordens.tsx";
import { Importar } from "./pages/Importar.tsx";
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
      { path: "listed-assets/new", element: <NewListedAsset /> },
      { path: "listed-assets/:id", element: <ListedAssetPage /> },
      { path: "carteira", element: <Carteira /> },
      { path: "estrategias", element: <Estrategias /> },
      { path: "ordens", element: <Ordens /> },
      { path: "importar", element: <Importar /> },
      { path: "indexes", element: <Indexes /> },
      { path: "upload", element: <Upload /> },
    ],
  },
];
