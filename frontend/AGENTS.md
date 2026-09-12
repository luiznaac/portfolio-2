# AGENTS.md — portfolio-2 frontend

React 19 + Vite + TypeScript + Tailwind v4 + TanStack Query + React Router SPA for the investment
portfolio tracker — same stack and conventions across all of luiznaac's frontends, see
salgadinhos' `react-spa-screen` skill for the shared parts (Tailwind v4-in-CSS, TanStack Query v5
object syntax, the `api/`→`components/`→`pages/`→`lib/` layout, the dev proxy/base-path setup).
This file only covers what's specific to portfolio-2.

## Commands

```bash
npm run dev         # vite dev server
npm run typecheck    # tsc -b --noEmit — the only check that exists today, no lint/test yet
npm run build         # tsc -b && vite build
```

## Portfolio-2-specific pieces

- **`api/types.ts` is the largest DTO mirror in this family** — it covers bonds, checking
  accounts, listed assets, indexes, uploads, allocation targets, transfer proposals, and tax/
  income types. Update it in the same commit as any backend DTO change (see root
  [`AGENTS.md`](../AGENTS.md)'s cross-cutting rule) — with this many types, it's easy to miss one;
  double-check the specific fields you touched, not just that the file compiles.
- **`lib/money.ts`** — money formatting/arithmetic helpers matching the backend's `BigDecimal`
  discipline (see `backend/AGENTS.md`'s "Design principles"). Don't do currency math with plain
  JS numbers; route it through here.
- **Pages are one-per-product-type** (`BondPage`, `CheckingAccountPage`, `ListedAssetPage`) plus
  matching `New*` creation forms — follow this pairing when adding a new consolidated product
  type on the backend (see `backend/AGENTS.md`'s "How to implement a new feature").
- **`components/DivergentBar.tsx`/`PositionChart.tsx`** are the allocation/position visualizations
  — check `lib/positions.ts` for the shared data-shaping logic before adding a new chart.

Git/PR conventions: see `salgadinhos/global/AGENTS.md`.
