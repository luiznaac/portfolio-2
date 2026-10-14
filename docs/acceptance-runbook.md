# Acceptance runbook — portfolio-2

Bootstrap-only runbook for the `self-served-acceptance` runner. The runner owns the browser process; this runbook boots `mysql` + `app` only.

## mysql

| | |
|---|---|
| start | `docker compose up -d mysql` |
| ready | `docker compose exec mysql mysqladmin ping -h 127.0.0.1` |
| port  | `3306` |
| stop  | `docker compose down` |
| build | — |
| env   | — |
| cwd   | `.` |

## app

| | |
|---|---|
| start | `docker compose up -d app` |
| ready | `curl -fsS http://localhost:8080/health/internal` |
| port  | `8080` (API; web UI on `8081` for Browser intents) |
| stop  | `docker compose down` |
| build | `docker compose build app` |
| env   | `CHAMEIDOR_TOKEN=dev-portfolio-fixture-token` |
| cwd   | `.` |

## Setup prose

- **Token injection**: the repo-root `docker-compose.yml` `app` service does not reference `CHAMEIDOR_TOKEN`, and `docker compose` does not forward host env vars into containers (verified empirically). Before starting, create a gitignored `docker-compose.override.yml` at the repo root:

  ```yaml
  services:
    app:
      environment:
        - CHAMEIDOR_TOKEN=${CHAMEIDOR_TOKEN}
  ```

  The `env` row above supplies the shell value Compose substitutes. Remove the override file at teardown.

- **Port contention**: ports `3306` / `8080` / `8081` must be free — a running local dev stack (`npm run db`, IntelliJ backend, `npm run dev` frontend) occupies them; stop it before booting.

- **DB readiness**: the app section needs no runbook-level DB probe — the mysql section's ready probe plus compose `depends_on: mysql: condition: service_healthy` covers it; the app self-migrates an empty MySQL via Flyway (`bin/migrate` in `deploy/entrypoint.sh`) before serving.

- **chameidor**: not booted; scheduling-side intents are unreachable and fine (job registration is lazy — `ScheduleService` is reached only through consolidation runs).
