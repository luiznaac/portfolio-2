// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, within } from "@testing-library/react";
import { type RouteObject, RouterProvider, createMemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { routes } from "./App.tsx";
import { monthPeriodLabel } from "./lib/month.ts";
import { ROUTES, STRATEGY_DETAIL_TABS, resolveCrumb } from "./lib/routes.ts";

// The route-table smoke harness: render every registered route against loading queries, so a route
// with a broken element or a missing page head fails here. The harness derives the paths from
// App.tsx rather than keeping a parallel list, expanding each route param to its samples (and each
// optional segment to its absent form) so the detail pages and their tabs are walked too.
const mocked = vi.hoisted(() => ({
  loading: {
    data: undefined,
    isLoading: true,
    isPending: false,
    isError: false,
    error: null,
    refetch: () => Promise.resolve(),
    mutate: () => undefined,
    mutateAsync: () => Promise.resolve(),
    reset: () => undefined,
  },
  // The catalog resolves, so the strategy detail's head is the entity name — the head the real
  // stack renders. Every other read stays in its loading state.
  strategy: { id: 1, name: "Dividendos", asset_class: "ACOES", kind: "REPORT_DRIVEN" },
  arrayHooks: new Set([
    "useTickerQuotesList",
    "useStrategyEditions",
    "useFixedIncomePositions",
    "useAttributions",
  ]),
}));

vi.mock(import("./api/queries.ts"), async (importOriginal) => {
  const actual = await importOriginal();
  const mockedModule: Record<string, unknown> = {};
  for (const key of Object.keys(actual)) {
    if (key === "useStrategies") {
      mockedModule[key] = () => ({
        ...mocked.loading,
        data: [mocked.strategy],
        isLoading: false,
      });
    } else {
      mockedModule[key] = mocked.arrayHooks.has(key) ? () => [] : () => mocked.loading;
    }
  }
  return mockedModule as typeof actual;
});

const SAMPLE_PARAM: Record<string, string[]> = {
  ticker: ["PETR4"],
  id: ["1"],
  // Carteira is the detail's segment-less landing route, so the optional segment's absent form
  // covers it; the other three are the tabs the detail links to.
  tab: STRATEGY_DETAIL_TABS.filter((tab) => tab !== "carteira"),
  period: ["2026-02"],
  importId: ["7"],
};

// The full-screen flow owns the viewport instead of the shell's page head; the smoke walk asserts
// its own marker.
const FULL_SCREEN: Record<string, string> = {
  [ROUTES.importAssistant]: "import-assistant",
  [`${ROUTES.importAssistant}/7`]: "import-assistant",
};

// The routes whose head is not an object label: the off-nav health slice and the two detail pages
// (the strategy and the frozen month), whose titles are the entity's, not the object's. The landing
// redirect resolves to Carteira through `resolveCrumb`.
const OFF_OBJECT_HEAD: Record<string, string> = {
  [ROUTES.health]: "Health",
};

function expectedHead(path: string): string | undefined {
  if (path.startsWith(`${ROUTES.portfolioHistory}/`)) {
    return `Histórico · ${monthPeriodLabel(path.slice(ROUTES.portfolioHistory.length + 1))}`;
  }
  const detail = path.startsWith(`${ROUTES.strategies}/`);
  return detail ? mocked.strategy.name : (OFF_OBJECT_HEAD[path] ?? resolveCrumb(path)?.object);
}

function joinPath(parent: string, child: string): string {
  if (child.startsWith("/")) return child;
  const base = parent.endsWith("/") ? parent.slice(0, -1) : parent;
  return `${base}/${child}`;
}

// Expands a route template into the paths the walk visits: a required param takes each of its
// samples, an optional param (`:tab?`) also visits the segment-absent form.
function expand(path: string): string[] {
  let paths = [""];
  for (const segment of path.split("/")) {
    if (!segment.startsWith(":")) {
      paths = paths.map((prefix) => joinPath(prefix, segment));
      continue;
    }
    const optional = segment.endsWith("?");
    const name = segment.slice(1).replace(/\?$/, "");
    const values = SAMPLE_PARAM[name];
    if (values === undefined) throw new Error(`no smoke sample for route param ${name}`);
    paths = paths.flatMap((prefix) => [
      ...(optional ? [prefix] : []),
      ...values.map((value) => joinPath(prefix, value)),
    ]);
  }
  return paths;
}

function collect(routeObjects: RouteObject[], parent = ""): string[] {
  const paths: string[] = [];
  for (const route of routeObjects) {
    const expanded = route.index === true ? [parent] : expand(joinPath(parent, route.path ?? ""));
    for (const path of expanded) {
      paths.push(path === "" ? "/" : path);
      if (route.children !== undefined) paths.push(...collect(route.children, path));
    }
  }
  return [...new Set(paths)].sort();
}

afterEach(cleanup);

describe("route table", () => {
  it("renders every registered route without a runtime error and asserts its page head", async () => {
    for (const path of collect(routes)) {
      const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
      const router = createMemoryRouter(routes, { initialEntries: [path] });
      const view = render(
        <QueryClientProvider client={client}>
          <RouterProvider router={router} />
        </QueryClientProvider>,
      );

      const marker = FULL_SCREEN[path];
      if (marker !== undefined) {
        await screen.findByTestId(marker);
        view.unmount();
        continue;
      }

      const head = await screen.findByTestId("page-head");
      const expected = expectedHead(path);
      if (expected === undefined) throw new Error(`route ${path} has no expected page head`);
      expect(within(head).getByRole("heading", { level: 1 }).textContent).toBe(expected);
      view.unmount();
    }
  });
});
