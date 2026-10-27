# AGENTS.md — portfolio-2 frontend

React 19 + Vite + TypeScript + Tailwind v4 + TanStack Query + React Router SPA for the investment portfolio tracker. The stack and conventions are shared across all of luiznaac's frontends (see salgadinhos' `react-spa-screen` skill for the shared parts); this file only covers what's specific to portfolio-2.

The product frontend is built out: the Cockpit at `/` is the monthly ritual's home, and the other screens hang off it (month, orders, import, close; allocation, strategies, assets, fixed income, corporate actions; income, taxes, performance). The scaffold's `GET /health` health-check dashboard stays reachable at `/health` as the end-to-end read example.

## Code map

- [`docs/code-map.md`](../docs/code-map.md)

## Layout

```
src/
  api/          client.ts (typed fetch wrapper), queries.ts (useQuery hooks), types.ts (DTO mirror)
  components/   Layout.tsx, Sidebar.tsx, MonthPill.tsx, CommandPalette.tsx — shell;
                RitualStepper/AllocationStrip/Agenda — Cockpit pieces; Panel/Chip/Badge/Button/
                Table/WizardStepper/Kpi — the token-based primitive layer; icons.tsx; *Chip.tsx
  hooks/        shared stateful UI logic (useClosedMonthSelection.ts)
  pages/        route-level components (`/` is Cockpit.tsx; Dashboard.tsx is the health-check
                example at /health); a page that needs a behaviour test gets a co-located
                *.test.tsx render test
  lib/          pure helpers, unit-tested in lib/**/*.test.ts — no React imports here
                (navigation.ts holds the sidebar groups + breadcrumb table; palette.ts flattens it
                for Ctrl/Cmd+K; cockpit.ts derives KPIs, ritual steps and attention cards; agenda.ts
                derives the agenda and its read set; dates.ts is the shared calendar arithmetic)
```

New API call: typed function in `api/client.ts` → hook in `api/queries.ts` → consumed from a `pages/` component. Don't call `fetch` directly from a component.

## Commands

```bash
npm run dev         # vite dev server, port 5273
npm run typecheck    # tsc -b --noEmit
npm run lint         # biome check
npm run lint:fix     # biome check --write
npm run test          # vitest run (src/lib/**/*.test.ts + src/**/*.test.tsx)
npm run check         # typecheck + lint + test — run before considering a change done
npm run build         # tsc -b && vite build
```

## Coding standards

- [`CODING_STANDARDS.md`](CODING_STANDARDS.md) — "the language-idiom rules the tooling does not enforce"

## Portfolio-2-specific pieces

- **Tailwind v4 config lives in CSS**: `src/index.css` has `@import "tailwindcss";` and an `@theme` block holding the reference palette, sign convention, radii scale and the three IBM Plex families (`src/fonts.css` bundles them via `@fontsource`); there is no `tailwind.config.js`.
- **Design reference**: [`design/front-prototype.html`](design/front-prototype.html) (issue #205) is the committed styling source — new pages carry its tokens (no `slate-*`/`emerald-*` defaults) and compose the primitive layer rather than hand-rolling Tailwind.
- **Base path / dev proxy**: `vite.config.ts` defaults the production `base` to `/portfolio/` (override with `VITE_BASE`) and `/` in dev; the dev server proxies `/api` to `VITE_API_TARGET` (default `http://localhost:8080`) to dodge CORS.
- `index.html` ships `lang="pt-BR"` and `class="dark"`.
- **Testing scope**: `src/lib/**/*.test.ts` covers the pure helpers in a node environment; a page that needs a behaviour test adds a co-located `src/**/*.test.tsx` render test that opts into jsdom with a `// @vitest-environment jsdom` docblock (Testing Library + `user-event`, asserting user-observable results such as focus or the rendered DOM — not implementation details). `lib/theme.test.ts` additionally reads source files as a build-time guard (the `@theme` tokens exist; no shipped component carries a scaffold-palette shade). Don't claim UI coverage beyond what actually exists.

Git/PR conventions: see `salgadinhos/global/AGENTS.md`.
