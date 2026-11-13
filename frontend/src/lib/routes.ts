// The six-object information architecture: the sidebar groups, each object's page tabs and the
// topbar breadcrumb all resolve from this one table. Pure (no React) so the route rules stay
// unit-tested in src/lib; the sidebar resolves the named icon to an SVG in Sidebar.tsx.
//
// Ativos uses `/ativos` rather than `/assets`: the production nginx serves the built bundle's
// `/assets/` static directory, which would shadow an SPA route of that name on a deep link.

export type ObjectKey = "portfolio" | "strategies" | "assets" | "imports" | "plan" | "analysis";

export type NavGroup = "Posição" | "Operações" | "Resultados";

export type NavIcon = "target" | "layers" | "assets" | "upload" | "calendar" | "chart";

// The canonical path strings. Cross-object links import these, so renaming a route is one edit in
// the object table rather than a sweep across pages.
export const ROUTES = {
  portfolio: "/portfolio",
  allocation: "/portfolio/allocation",
  portfolioHistory: "/portfolio/history",
  strategies: "/strategies",
  strategiesImport: "/strategies/import",
  assets: "/ativos",
  positions: "/ativos/positions",
  trades: "/ativos/trades",
  fixedIncome: "/ativos/fixed",
  quotes: "/ativos/quotes",
  gaps: "/ativos/gaps",
  imports: "/imports",
  importAssistant: "/imports/assistant",
  importSources: "/imports/sources",
  corporateActions: "/imports/corporate",
  importHistory: "/imports/history",
  plan: "/plan",
  planOrders: "/plan/orders",
  planTransfers: "/plan/transfers",
  planClose: "/plan/close",
  analysis: "/analysis",
  income: "/analysis/income",
  taxes: "/analysis/tax",
  health: "/health",
} as const;

// The ticker detail nests under Ativos · Posições; the entity name is the path segment.
export function positionDetail(ticker: string): string {
  return `${ROUTES.positions}/${encodeURIComponent(ticker)}`;
}

// The frozen month nests under Carteira · Histórico; the period is the path segment and the entity
// name the breadcrumb names at depth.
export function portfolioHistoryDetail(period: string): string {
  return `${ROUTES.portfolioHistory}/${encodeURIComponent(period)}`;
}

// The strategy detail nests under Estratégias · Todas; the id is the path segment.
export function strategyDetail(strategyId: number): string {
  return `${ROUTES.strategies}/${strategyId}`;
}

// The detail's own tabs are routes under the id; Carteira is the detail's landing tab.
export type StrategyDetailTab = "carteira" | "edicoes" | "atribuicao" | "fatia";

export function strategyDetailTab(strategyId: number, tab: StrategyDetailTab): string {
  return tab === "carteira" ? strategyDetail(strategyId) : `${strategyDetail(strategyId)}/${tab}`;
}

// An attention ref carries the target income row's id; Proventos reads it and anchors the shared
// window on that row's month with the row highlighted.
export function incomeRow(incomeId: number): string {
  return `${ROUTES.income}?focus=${incomeId}`;
}

export interface SubView {
  key: string;
  label: string;
  path: string;
  // Always reserved under the page title so the tab strip never jumps between sub-views.
  subtitle: string;
  // The object's landing tab is active only on its own route, so a detail route does not light it.
  end?: boolean;
}

export interface AppObject {
  key: ObjectKey;
  label: string;
  group: NavGroup;
  icon: NavIcon;
  // The object's route prefix; every sub-view path sits under it.
  root: string;
  subs: SubView[];
}

export const OBJECTS: AppObject[] = [
  {
    key: "portfolio",
    label: "Carteira",
    group: "Posição",
    icon: "target",
    root: ROUTES.portfolio,
    subs: [
      {
        key: "overview",
        label: "Visão geral",
        path: ROUTES.portfolio,
        subtitle: "Posição consolidada, alocação por classe e o que precisa de você.",
        end: true,
      },
      {
        key: "allocation",
        label: "Alocação",
        path: ROUTES.allocation,
        subtitle: "A árvore completa: classes, estratégias, buckets manuais e sub-classes.",
      },
      {
        key: "history",
        label: "Histórico",
        path: ROUTES.portfolioHistory,
        subtitle: "Os registros mensais fechados e o que cada snapshot congelou.",
      },
    ],
  },
  {
    key: "strategies",
    label: "Estratégias",
    group: "Posição",
    icon: "layers",
    root: ROUTES.strategies,
    subs: [
      {
        key: "list",
        label: "Todas",
        path: ROUTES.strategies,
        subtitle: "As estratégias com relatório e os buckets manuais.",
      },
    ],
  },
  {
    key: "assets",
    label: "Ativos",
    group: "Posição",
    icon: "assets",
    root: ROUTES.assets,
    subs: [
      {
        key: "positions",
        label: "Posições",
        path: ROUTES.positions,
        subtitle: "Custódia real, preço médio fiscal e classificação.",
      },
      {
        key: "trades",
        label: "Negócios",
        path: ROUTES.trades,
        subtitle: "O livro de negócios: o lastro do imposto.",
      },
      {
        key: "fixed",
        label: "Renda fixa",
        path: ROUTES.fixedIncome,
        subtitle: "Títulos e contas na mesma tabela; o valor por accrual.",
      },
      {
        key: "quotes",
        label: "Cotações",
        path: ROUTES.quotes,
        subtitle: "Último fechamento por ticker e a idade da cotação.",
      },
      {
        key: "gaps",
        label: "Lacunas",
        path: ROUTES.gaps,
        subtitle: "Deltas de custódia que o livro não explica.",
      },
    ],
  },
  {
    key: "imports",
    label: "Importações",
    group: "Operações",
    icon: "upload",
    root: ROUTES.imports,
    subs: [
      {
        key: "inbox",
        label: "Caixa de entrada",
        path: ROUTES.imports,
        subtitle: "Arquivos em revisão, num só lugar.",
        end: true,
      },
      {
        key: "sources",
        label: "Fontes",
        path: ROUTES.importSources,
        subtitle: "Estado por fonte; a atenção mora aqui.",
      },
      {
        key: "corporate",
        label: "Ações corporativas",
        path: ROUTES.corporateActions,
        subtitle: "Eventos que mudam quantidade ou preço médio.",
      },
      {
        key: "history",
        label: "Histórico",
        path: ROUTES.importHistory,
        subtitle: "Tudo que já foi staged, revisado, aplicado ou falhou.",
      },
    ],
  },
  {
    key: "plan",
    label: "Plano / Mês",
    group: "Operações",
    icon: "calendar",
    root: ROUTES.plan,
    subs: [
      {
        key: "month",
        label: "Mês atual",
        path: ROUTES.plan,
        subtitle: "O mês aberto em seis etapas.",
        end: true,
      },
      {
        key: "orders",
        label: "Ordens",
        path: ROUTES.planOrders,
        subtitle: "A lista persistida do mês e o ciclo de cada ordem.",
      },
      {
        key: "transfers",
        label: "Transferências",
        path: ROUTES.planTransfers,
        subtitle: "Mover atribuição sem tocar custódia.",
      },
      {
        key: "close",
        label: "Fechamentos",
        path: ROUTES.planClose,
        subtitle: "Congela o snapshot e trava a edição do mês.",
      },
    ],
  },
  {
    key: "analysis",
    label: "Análise",
    group: "Resultados",
    icon: "chart",
    root: ROUTES.analysis,
    subs: [
      {
        key: "performance",
        label: "Performance",
        path: ROUTES.analysis,
        subtitle: "Retorno, benchmark e a decomposição mensal.",
        end: true,
      },
      {
        key: "income",
        label: "Proventos",
        path: ROUTES.income,
        subtitle: "Proventos previstos e recebidos.",
      },
      {
        key: "tax",
        label: "Impostos",
        path: ROUTES.taxes,
        subtitle: "Leitura mensal do ganho de capital.",
      },
    ],
  },
];

export const NAV_GROUPS: { label: NavGroup; objects: AppObject[] }[] = [
  { label: "Posição", objects: OBJECTS.filter((object) => object.group === "Posição") },
  { label: "Operações", objects: OBJECTS.filter((object) => object.group === "Operações") },
  { label: "Resultados", objects: OBJECTS.filter((object) => object.group === "Resultados") },
];

// A nested route that is not an entity (a child screen of a tab) and so keeps a stable crumb label.
const STATIC_CHILDREN: Record<string, string> = {
  [ROUTES.strategiesImport]: "Importar relatórios",
};

export interface Crumb {
  group: NavGroup;
  object: string;
  view: string;
}

function normalize(pathname: string): string {
  return pathname.length > 1 ? pathname.replace(/\/+$/, "") : pathname;
}

export function activeObject(pathname: string): AppObject | null {
  const path = normalize(pathname);
  if (path === "/") return OBJECTS[0] ?? null;
  return (
    OBJECTS.find((object) => path === object.root || path.startsWith(`${object.root}/`)) ?? null
  );
}

export function activeSub(object: AppObject, pathname: string): SubView {
  const path = normalize(pathname);
  let active: SubView | undefined;
  for (const sub of object.subs) {
    if (path === sub.path || path.startsWith(`${sub.path}/`)) {
      if (active === undefined || sub.path.length > active.path.length) active = sub;
    }
  }
  const resolved = active ?? object.subs[0];
  if (resolved === undefined) throw new Error(`object ${object.key} has no sub-views`);
  return resolved;
}

// Resolves the topbar crumb. At depth the third slot is the entity name (a ticker, a frozen month)
// or a nested screen's label — never the parent sub-view.
export function resolveCrumb(pathname: string): Crumb | null {
  const object = activeObject(pathname);
  if (object === null) return null;
  const sub = activeSub(object, pathname);
  const path = normalize(pathname);
  const rest = path.slice(sub.path.length).replace(/^\//, "");
  const view =
    rest.length === 0
      ? sub.label
      : (STATIC_CHILDREN[`${sub.path}/${rest}`] ?? decodeURIComponent(rest.split("/")[0] ?? ""));
  return { group: object.group, object: object.label, view };
}
