# portfolio-2

A personal **investment portfolio tracker**, focused on Brazilian fixed-income investing. It keeps track of bonds and checking accounts, calculates how much they're worth today (including accrued yield and applicable taxes), and can generate reports.

## What it actually does for you

- **Tracks bonds** (renda fixa) — both fixed-rate and floating-rate (indexed to CDI/SELIC) — and the orders (purchases/contributions) that make them up.
- **Tracks checking account balances and movements.**
- **Calculates current value/yield** for everything above, pulling official index rates (CDI, SELIC) from the Brazilian Central Bank's public API.
- **Calculates Brazilian taxes** that would apply if you redeemed today — IOF (a tax that decreases the longer you hold an investment, within the first 30 days) and income tax (rate depends on how long you've held the position).
- **Generates reports** — XLSX and PDF exports of your consolidated position.
- **Schedules its own recurring jobs** (like periodic consolidation) by registering them with a companion service, [chameidor](../chameidor/README.md), instead of running its own cron loop.
- **Web UI** — a React SPA (`frontend/`) for browsing the portfolio, positions and index rates.

In short: point it at your bonds and accounts, and it tells you what they're worth today, after tax, without you doing the math by hand.

## Repository layout

This is a monorepo:

- **`backend/`** — the Ktor + Spring + Exposed service (Gradle multi-module). See [backend/AGENTS.md](backend/AGENTS.md).
- **`frontend/`** — the React + Vite + Tailwind SPA that consumes the HTTP API. See [frontend/README.md](frontend/README.md).
- repo root — combined `Dockerfile` / `docker-compose.yml`, `deploy/` templates, and a script-only `package.json` task runner (`npm run db | be:run | fe:dev | check | up`).

## Using it

### Run it locally

Requires Docker, JDK 21 and Node 20+.

```bash
npm run db          # starts a local MySQL instance (database: portfolio)
npm run be:run      # runs the backend on :8080 (needs MYSQL_HOST=localhost etc.)
npm run fe:dev      # runs the SPA on :5273, proxying /api -> :8080
```

The backend listens on port `8080`. If you want scheduled jobs to actually run, also start [chameidor](../chameidor/README.md) on port `8081` — portfolio-2 talks to it at `http://localhost:8081` by default.

Full stack in one container: `npm run up` (or `docker compose up --build`) → SPA on `http://localhost:8081`, API on `http://localhost:8080`.

### What you can do through the API

- `BondController` / `BondOrderController` — register bonds and their orders.
- `CheckingAccountController` — register checking accounts and movements.
- `IndexController` / `IndexValueController` — manage the reference indexes (CDI, SELIC) and their historical values.
- `ConsolidationController` — get your consolidated position across everything you've registered.
- `UploadController` — generate/upload XLSX or PDF reports.
- `HealthController` — service health check.

### Build & test

```bash
cd backend && ./gradlew clean build       # backend
npm --prefix frontend run build           # frontend
npm run check                             # both, from the repo root
```

## Where things live

- `backend/usecase` — the actual investment/tax logic (bonds, checking accounts, indexes, tax calculation), organized one folder per business concept.
- `backend/persistence` — how everything is stored in MySQL.
- `backend/gateway` — talks to the Central Bank of Brazil's rates API and to chameidor for scheduling.
- `backend/http-api` — the HTTP endpoints listed above, plus PDF/XLSX report generation.
- `backend/application` — the entry point and configuration.
- `frontend/src` — the SPA: `api/` (typed HTTP client mirroring the controllers), `pages/`, `components/`, `lib/`, `i18n/`.
- `deploy/` — nginx + supervisord templates for the combined Docker image.

See [AGENTS.md](AGENTS.md) for the monorepo overview and [backend/AGENTS.md](backend/AGENTS.md) for backend architecture and conventions.

## Related project

Uses [chameidor](../chameidor/README.md) as its task scheduler — run both locally if you need scheduled consolidation/reporting jobs to actually fire.
