# AGENTS.md — portfolio-2 frontend

React 19 + Vite + TypeScript + Tailwind v4 + TanStack Query + React Router SPA for the investment portfolio tracker. The stack and conventions are shared across all of luiznaac's frontends (see salgadinhos' `react-spa-screen` skill for the shared parts); this file only covers what's specific to portfolio-2.

The product frontend runs on the six-object shell: **Posição** (Carteira, Estratégias, Ativos), **Operações** (Importações, Plano / Mês) and **Resultados** (Análise), with sub-views as page tabs under the object head and the landing at Carteira · Visão geral. Production screens are remounted under their new object routes until their redesign slice replaces them; the scaffold's `GET /health` health-check dashboard stays reachable off-nav at `/health` as the end-to-end read example.

## Code map

- [`docs/code-map.md`](../docs/code-map.md)

## Layout

```
src/
  api/          client.ts (typed fetch wrapper), queries.ts (useQuery hooks), types.ts (DTO mirror)
  components/   Layout.tsx, TopBar.tsx, Sidebar.tsx, ObjectPage.tsx, PendingSurface.tsx — shell;
                RitualStepper/AllocationStrip/Agenda — Cockpit pieces; PageHead/PageTabs — the
                object head and sub-view tab strip; Panel/Chip/Badge/Button/Table/FilterBar/
                WizardStepper/Kpi/Num/ClassBadge — the token-based primitive layer; TickerDrawer —
                the Ativos ticker detail opened from a Posições row or its deep link; icons.tsx;
                *Chip.tsx
  hooks/        shared stateful UI logic (useClosedMonthSelection.ts, and usePageFilters.ts — a
                page's filter values, kept for the route element's lifetime and never in the URL)
  pages/        route-level components (`/portfolio` is Cockpit.tsx, the interim Visão geral;
                Positions.tsx owns the Posições table and its optional ticker drawer route;
                Dashboard.tsx is the off-nav health-check example at /health); a page that needs a
                behaviour test gets a co-located *.test.tsx render test
  lib/          pure helpers, unit-tested in lib/**/*.test.ts — no React imports here
                (routes.ts is the single six-object table: sidebar groups, page tabs, breadcrumb
                resolver and entity-at-depth rule; status.ts derives the sidebar attention counts and
                the top bar's freshness/month lines; cockpit.ts derives KPIs, ritual steps and
                attention cards; agenda.ts derives the agenda and its read set; dates.ts is the shared
                calendar arithmetic; filters.ts is the shared filter model, with
                tradeFilters.ts/incomeFilters.ts/positionFilters.ts declaring each list's fields +
                matcher; position.ts builds the Posições rows and their filtered total)
```

New API call: typed function in `api/client.ts` → hook in `api/queries.ts` → consumed from a `pages/` component. Don't call `fetch` directly from a component.

## Commands

```bash
npm run dev         # vite dev server, port 8113 (strict)
npm run typecheck    # tsc -b --noEmit
npm run lint         # biome check
npm run lint:fix     # biome check --write
npm run test          # vitest run (src/lib/**/*.test.ts + src/**/*.test.tsx)
npm run check         # typecheck + lint + test — run before considering a change done
npm run build         # tsc -b && vite build
```

### Fresh worktree setup (sandbox)

A fresh worktree has no `node_modules`, so the first `npm test` fails with `vitest: not found`. From the monorepo root (this lane's parent), install the lane's dependencies inside the salgadinhos sandbox:

```bash
node <salgadinhos>/adapters/sandbox/run.mjs --stack node --workspace <repo-root> -- npm --prefix frontend ci
```

`--stack node` is required because the monorepo root's `package.json` is a dependency-less scripts shim, so the runner's marker detection intentionally does not detect it (its error suggests pointing at `frontend/`). The command mirrors the frontend install in `npm run setup`, but the install runs in the sandbox, never on the host — the rule and setup live in `salgadinhos` at `global/AGENTS.md` → "Sandbox for third-party installs" and `docs/salgadinhos/sandbox-bootstrap.md`.

## Coding standards

- [`CODING_STANDARDS.md`](CODING_STANDARDS.md) — "the language-idiom rules the tooling does not enforce"

## Portfolio-2-specific pieces

- **Tailwind v4 config lives in CSS**: `src/index.css` has `@import "tailwindcss";` and the `@theme` block holding the A · Console palette, fixed sign semantics, type scale, density and the three IBM Plex families (`src/fonts.css` bundles them via `@fontsource`); there is no `tailwind.config.js`.
- **Design reference**: A · Console (issues #355/#366) is the committed token source in `src/index.css` — new pages consume its tokens and the primitive layer, never a raw colour or a `slate-*`/`emerald-*` default. [`design/front-prototype.html`](design/front-prototype.html) (issue #205) remains the reference for the un-migrated screens until their own slice lands.
- **Base path / dev proxy**: `vite.config.ts` defaults the production `base` to `/portfolio/` (override with `VITE_BASE`) and `/` in dev; `loadEnv` reads the lane's env file (Vite does not inject it into `process.env`), the dev server binds `FRONTEND_DEV_PORT` (default `8113`, strict so a taken port fails loudly) and proxies `/api` to `VITE_API_TARGET` (default `http://localhost:8110`) to dodge CORS.
- `index.html` ships `lang="pt-BR"` and `class="dark"`.
- **Testing scope**: `src/lib/**/*.test.ts` covers the pure helpers in a node environment; a query hook that owns a cache-invalidation contract gets a co-located `src/api/*.test.tsx` with `renderHook` over a `QueryClientProvider`, asserting the read the UI consumes rather than spy call counts; a page that needs a behaviour test adds a co-located `src/**/*.test.tsx` render test that opts into jsdom with a `// @vitest-environment jsdom` docblock (Testing Library + `user-event`, asserting user-observable results such as focus or the rendered DOM — not implementation details). `components/Layout.test.tsx` pins the six-object sidebar, attention badges, breadcrumb and topbar status lines, and `src/App.test.tsx` is the route-table smoke harness — every registered route renders its page head without a runtime error, and each page slice extends it. `lib/theme.test.ts` and `lib/dtoMirrorGuard.test.ts` additionally read source files as build-time guards (the `@theme` tokens/scaffold shades; every `api/types.ts` interface against the DTO the backend serialises — its header says where a new DTO is mapped). Don't claim UI coverage beyond what actually exists.

Git/PR conventions: see `salgadinhos/global/AGENTS.md`.
