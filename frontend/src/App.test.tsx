// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, within } from "@testing-library/react";
import { type RouteObject, RouterProvider, createMemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { routes } from "./App.tsx";
import { ROUTES, resolveCrumb } from "./lib/routes.ts";

// The route-table smoke harness: render every registered route against loading queries, so a route
// with a broken element or a missing page head fails here. Each page slice extends the table it
// walks; the harness derives the routes from App.tsx rather than keeping a parallel list.
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
  arrayHooks: new Set(["useTickerQuotesList", "useStrategyEditions"]),
}));

vi.mock(import("./api/queries.ts"), async (importOriginal) => {
  const actual = await importOriginal();
  const mockedModule: Record<string, unknown> = {};
  for (const key of Object.keys(actual)) {
    mockedModule[key] = mocked.arrayHooks.has(key) ? () => [] : () => mocked.loading;
  }
  return mockedModule as typeof actual;
});

const SAMPLE_PARAM: Record<string, string> = {
  ticker: "PETR4",
  id: "1",
  period: "2026-10",
  importId: "7",
};

// The full-screen flow owns the viewport instead of the shell's page head; the smoke walk asserts
// its own marker.
const FULL_SCREEN: Record<string, string> = {
  [ROUTES.importAssistant]: "import-assistant",
  [`${ROUTES.importAssistant}/7`]: "import-assistant",
};

// The one route whose head is not an object label: the off-nav health slice. The landing redirect
// resolves to Carteira through `resolveCrumb`.
const OFF_OBJECT_HEAD: Record<string, string> = {
  [ROUTES.health]: "Health",
};

function joinPath(parent: string, child: string): string {
  if (child.startsWith("/")) return child;
  const base = parent.endsWith("/") ? parent.slice(0, -1) : parent;
  return `${base}/${child}`;
}

function sample(path: string): string {
  const sampled = path
    .split("/")
    .map((segment) => {
      if (!segment.startsWith(":")) return segment;
      // An optional segment (`:ticker?`) samples its present form so the detail route is walked too.
      const name = segment.slice(1).replace(/\?$/, "");
      const value = SAMPLE_PARAM[name];
      if (value === undefined) throw new Error(`no smoke sample for route param ${name}`);
      return value;
    })
    .join("/");
  return sampled.length > 1 ? sampled.replace(/\/$/, "") : sampled;
}

function collect(routeObjects: RouteObject[], parent = ""): string[] {
  const paths: string[] = [];
  for (const route of routeObjects) {
    const path = route.index === true ? parent : sample(joinPath(parent, route.path ?? ""));
    paths.push(path === "" ? "/" : path);
    if (route.children !== undefined) paths.push(...collect(route.children, path));
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
      const expected = OFF_OBJECT_HEAD[path] ?? resolveCrumb(path)?.object;
      if (expected === undefined) throw new Error(`route ${path} has no expected page head`);
      expect(within(head).getByRole("heading", { level: 1 }).textContent).toBe(expected);
      view.unmount();
    }
  });
});
