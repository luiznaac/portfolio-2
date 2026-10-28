# AGENTS.md — portfolio-2 monorepo

Development guidelines for anyone (human, agent, or tool) working in this repository.

Two projects, one repo:

- **`backend/`** — the Ktor + Spring + Exposed service (Gradle multi-module, root project name `portfolio`). All backend commands run from `backend/` (`cd backend && ./gradlew <task>`); the root `npm run be:*`/`db:*` scripts make that `cd` for you. Architecture, conventions and rules for evolving it are in [backend/AGENTS.md](backend/AGENTS.md) — read that before touching `backend/`.
- **`frontend/`** — the React 19 + Vite + TypeScript + Tailwind v4 SPA. Commands run from `frontend/` (`npm --prefix frontend run <script>`). Details in [frontend/README.md](frontend/README.md). The product slices are built out — Cockpit, month decision screens, portfolio and results screens — with the scaffold's health-check slice kept reachable at `/health`; its hand-mirrored `src/api/types.ts` tracks every backend edge DTO.

## Code map

- [`docs/code-map.md`](docs/code-map.md)

## Shared contracts

Repo-root `contracts/` holds JSON fixtures that are the cross-cutting source of truth for the backend tests. `contracts/error-response.json` is the canonical domain-exception body: the backend's `DomainExceptionContractTest` asserts the API serialises exactly it, so a shape change edits the fixture once.

## Tooling

Root `package.json` holds script shims only (`npm run be:unit`, `npm run be:integration`, `npm run be:check`, `npm run fe:build`, `npm run check`, `npm run db`, `npm run db:migrate`, `npm run db:generate -- -Pname=V5__x`, `npm run up`). It has no dependencies and is not a real package; the `be:*` and `db:*` scripts run Gradle through `scripts/backend-gradle.mjs`, which picks `gradlew.bat` or `./gradlew` for the host, so they work under cmd/PowerShell as well as a POSIX shell. `be:unit` is the Docker-free `unitTest` lane the pre-commit hook also runs; `be:integration` is the Docker-backed one, and `be:check` runs both plus `detekt` with the daemon off. `.pre-commit-config.yaml` lives at the root and scopes hooks by path (`^backend/`, `^frontend/`), and carries `no-commit-to-branch` — the git/PR conventions are enforced there, not merely stated (see `salgadinhos/global/AGENTS.md`).

## Config & secrets

The env-driven settings are `mysql.*` and `gateways.chameidor.token`, wired into `application.yaml`; the full list with per-variable notes is in [backend/AGENTS.md](backend/AGENTS.md) §Configuration. Locally the IntelliJ run config `backend/.run/BootKt.run.xml` and `docker compose up` supply them — the app does not read `.env` yet. The versioned [`.env.example`](.env.example) is the reference for the vars and their dev fixtures; `.env` is gitignored so real values stay out of git.

`MYSQL_*` and `CHAMEIDOR_TOKEN` are required (no default), so a missing value fails the boot. The fixtures are `MYSQL_*` = `localhost` / `root` / empty and `CHAMEIDOR_TOKEN=dev-portfolio-fixture-token`; the single secrets doc is `docs/salgadinhos/secrets.md` in the salgadinhos repo.

## Docker

One image (repo-root `Dockerfile`, multi-stage) ships backend + frontend together: `supervisord` runs the JVM app (`API_PORT`/8080) and `nginx` (`deploy/nginx.conf.template` — serves the built SPA on `WEB_PORT`/8081 and reverse-proxies `/api` → the app). No DB in the image. Repo-root `docker-compose.yml` adds MySQL for full-stack / DB-only local runs; `backend/docker-compose.yml` is the MySQL-only file consumed by the `integrationTest` module via Testcontainers — keep the two MySQL definitions in sync. The schema comes from `backend/persistence/src/main/resources/db/migration/V*.sql`, applied by Flyway (`bin/migrate` in the image) from `deploy/entrypoint.sh` before the app starts — see [backend/AGENTS.md](backend/AGENTS.md).

`.github/workflows/ci.yml` pins every action by full commit SHA with a version comment (Dependabot proposes pin bumps) and runs `backend` (Gradle `clean build`) and `frontend` (`npm ci && npm run check && npm run build`) jobs on every push to master and every PR. Its `dependency-submission` job (push-to-master only, `contents: write`) submits the Gradle dependency graph from `backend/`; its `publish` job (`needs: [backend, frontend]`, push-to-master only) then builds the repo-root `Dockerfile` and pushes `luiznaac/portfolio` with tags `latest` and `v<run-number>` (a sequential build number, `github.run_number`) — so the image is published only after the backend and frontend jobs are green.

## User-facing surface declaration

Surfaces: **HTTP + Browser**.

Run story: combined Docker image — `docker compose up --build` at the repo root (`mysql` + `app`), run in the acceptance session; `CHAMEIDOR_TOKEN` injected as the dev fixture via a gitignored `docker-compose.override.yml`; chameidor is not booted.

Runbook: [`docs/acceptance-runbook.md`](docs/acceptance-runbook.md).
