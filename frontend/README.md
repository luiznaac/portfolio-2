# portfolio-fe

Frontend for [`portfolio-2`](../backend) — a personal Brazilian fixed-income
portfolio tracker. SPA in React 19 + Vite + TypeScript + Tailwind v4, consuming
the backend's HTTP API.

Same stack and conventions as [`shougong/frontend`](../../shougong/frontend):
typed hand-written API client mirroring the backend DTOs, TanStack Query for
data, hand-rolled SVG charts (no chart lib), dark-only theme, pt-BR copy.

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

## Build

```bash
npm run build      # dist/ with base path /portfolio/ (for the reverse proxy)
npm run preview
```

To build at the root (`/`) instead — which is what the combined Docker image
does — set `VITE_BASE=/`.

## Screens

| Route                    | What                                                            |
| ------------------------ | -------------------------------------------------------------- |
| `/`                      | Painel: net-worth tiles, portfolio value chart, bond & account lists, "Consolidar tudo" |
| `/bonds/new`             | Register a fixed- or floating-rate bond                         |
| `/bonds/:id`             | Bond detail: positions chart + table, consolidate, new order    |
| `/checking-accounts/new` | Register a checking account                                     |
| `/checking-accounts/:id` | Account detail: balance chart + table, deposit/withdraw, consolidate |
| `/indexes`               | CDI / SELIC / IPCA — latest value, record count, hydrate, sparkline |
| `/upload`                | Import a broker's `.xlsx` export (Kinvo / PicPay) into a bond or account |

## Architecture notes

- `src/api/` — typed HTTP client (`client.ts`), TanStack Query hooks
  (`queries.ts`), and `types.ts`. **`types.ts` is a hand-maintained mirror of the
  DTOs the backend controllers serialize** (`backend/http-api/.../controller/`,
  Jackson `SNAKE_CASE` — see `backend/usecase/.../configuration/JsonMapper.kt`).
  Change a DTO on either side and update the other in the same commit.
- The backend has no whole-portfolio endpoint, so the dashboard **aggregates
  client-side**: one `GET /{bonds|checking-accounts}/{id}/positions` per product
  (via `useQueries`), merged by date in `src/lib/positions.ts`.
- `src/components/PositionChart.tsx` — hand-rolled SVG area chart of
  `principal + yield - taxes` over time (same technique as shougong's
  `ItemsLearnedChart`). No chart library.
- Money & rates are `number` in TS but `BigDecimal` on the backend — the frontend
  only formats them (`src/lib/money.ts`), never does money arithmetic.
- `src/i18n/` — pt-BR labels for backend enums (`IndexId`, `BondOrderType`),
  which stay English/acronym on the wire.
- The `Bond` DTO has no type discriminator: floating-rate bonds are the ones with
  an `index_id`.
