# AGENTS.md — portfolio-2 frontend

React 19 + Vite + TypeScript + Tailwind v4 + TanStack Query + React Router SPA for the investment portfolio tracker. The stack and conventions are shared across all of luiznaac's frontends (see salgadinhos' `react-spa-screen` skill for the shared parts); this file only covers what's specific to portfolio-2.

The frontend is currently reset to the `environments/react` scaffold: the only vertical slice is the `GET /health` health-check dashboard. The product frontend is being rewritten from scratch in future plans, so don't treat the current pages as a base to extend — start from the scaffold layout below.

## Layout

```
src/
  api/          client.ts (typed fetch wrapper), queries.ts (useQuery hooks), types.ts (DTO mirror)
  components/   Layout.tsx, Panel.tsx — shared chrome
  pages/        route-level components (Dashboard.tsx is the health-check example)
  lib/          pure helpers, unit-tested in lib/**/*.test.ts — no React imports here
```

New API call: typed function in `api/client.ts` → hook in `api/queries.ts` → consumed from a `pages/` component. Don't call `fetch` directly from a component.

## Commands

```bash
npm run dev         # vite dev server, port 5273
npm run typecheck    # tsc -b --noEmit
npm run lint         # biome check
npm run lint:fix     # biome check --write
npm run test          # vitest run (src/lib/** and src/api/**)
npm run check         # typecheck + lint + test — run before considering a change done
npm run build         # tsc -b && vite build
```

## Portfolio-2-specific pieces

- **Tailwind v4 config lives in CSS**: `src/index.css` has `@import "tailwindcss";` and an `@theme` block; there is no `tailwind.config.js`.
- **Base path / dev proxy**: `vite.config.ts` defaults the production `base` to `/portfolio/` (override with `VITE_BASE`) and `/` in dev; the dev server proxies `/api` to `VITE_API_TARGET` (default `http://localhost:8080`) to dodge CORS.
- `index.html` ships `lang="pt-BR"` and `class="dark"`.
- **Testing scope**: only `src/lib/**` has tests (`vitest.config.ts` restricts `include` to it) — pure logic, no rendering. Don't claim UI coverage beyond what this actually checks.

Git/PR conventions: see `salgadinhos/global/AGENTS.md`.
