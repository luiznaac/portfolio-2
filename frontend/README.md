# portfolio-fe

Frontend for [`portfolio-2`](../backend) — a personal Brazilian fixed-income
portfolio tracker. SPA in React 19 + Vite + TypeScript + Tailwind v4, consuming
the backend's HTTP API.

This frontend is currently reset to the `environments/react` scaffold: the only
vertical slice is the health-check dashboard calling `GET /health` through the
typed client. The product frontend will be rewritten from scratch in future
plans, so the scaffold is the starting point, not a base to extend.

## Prerequisites

- **Node.js 20+** (`winget install OpenJS.NodeJS.LTS` or <https://nodejs.org>).
- The backend running: `npm run db && npm run be:run` from the repo root
  (`http://localhost:8080`).

## Dev

```bash
npm install
npm run dev
```

Opens at `http://localhost:5273`. Calls to `/api/*` are proxied to
`http://localhost:8080` (configured in `vite.config.ts`, override with
`VITE_API_TARGET`), so no CORS changes are needed on the backend.

> On PowerShell, if `npm` is blocked by the execution policy (`npm.ps1 cannot be
> loaded`), use `npm.cmd install` / `npm.cmd run dev`, or run
> `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned`.

## Checks

```bash
npm run check      # typecheck + lint (biome) + test (vitest)
```

## Build

```bash
npm run build      # dist/ with base path /portfolio/ (for the reverse proxy)
npm run preview
```

To build at the root (`/`) instead — which is what the combined Docker image
does — set `VITE_BASE=/`.

## Architecture notes

- `src/api/` — typed HTTP client (`client.ts`), TanStack Query hooks
  (`queries.ts`), and `types.ts` (hand-maintained DTO mirror). New API call:
  typed function in `client.ts` → hook in `queries.ts` → `pages/` component.
- `src/lib/` — pure helpers, unit-tested in `lib/**/*.test.ts`; no React imports.
- `src/index.css` — Tailwind v4 theme lives here (`@theme`), not in a
  `tailwind.config.js`.
