# Acceptance runbook — one-off brapi sync (issue [#454](https://github.com/luiznaac/portfolio-2/issues/454))

Bootstrap-only runbook for the `self-served-acceptance` runner. It boots `mysql` + `app` only and seeds the database a completed brapi run leaves behind, because brapi is a paid third party that is not booted here. The repo-wide runbook is [`acceptance.md`](acceptance.md); this one is the ticket-scoped variant whose `app` ready probe applies the seed.

## mysql

| | |
|---|---|
| start | `docker compose up -d mysql` |
| ready | `docker compose exec mysql mysqladmin ping -h 127.0.0.1` |
| port  | `8112` |
| stop  | `docker compose down` |
| build | — |
| env   | — |
| cwd   | `.` |

## app

| | |
|---|---|
| start | `docker compose up -d app` |
| ready | `curl -fsS http://localhost:8110/health/internal && docker compose exec -T mysql mysql --default-character-set=utf8mb4 -uroot portfolio < docs/acceptance/fixtures/brapi-sync-seed.sql` |
| port  | `8110` (API; web UI on `8111` for Browser intents) |
| stop  | `docker compose down` |
| build | `docker compose build app` |
| env   | `CHAMEIDOR_TOKEN=dev-portfolio-fixture-token` |
| cwd   | `.` |

## Setup prose

- **Clean database**: run `docker compose down -v` before the first boot. A named volume left by an earlier acceptance run carries prior imports, and re-uploading a file whose hash is already known resumes the old record instead of staging a fresh one. The app migrates an empty MySQL on boot.
- **Seed**: the `app` ready probe applies the ticket's seed once the API answers `/health/internal`, so the database holds one completed run's audit, one applied BRAPI split, one parked BRAPI amortization with no ex-date, and two BRAPI incomes (one estimated). The seed is idempotent and re-applies safely while the probe polls.
- **Token injection**: the repo-root `docker-compose.yml` `app` service does not reference `CHAMEIDOR_TOKEN`, and `docker compose` does not forward host env vars into containers. Before starting, create a gitignored `docker-compose.override.yml` at the repo root:

  ```yaml
  services:
    app:
      environment:
        - CHAMEIDOR_TOKEN=${CHAMEIDOR_TOKEN}
  ```

  The `env` row above supplies the shell value Compose substitutes. Remove the override file at teardown. `BRAPI_TOKEN` is deliberately absent: the sync POST must fail loudly (502) in this stack, which is the acceptance proof that the run is explicit and credential-gated.
- **Port contention**: ports `8112` / `8110` / `8111` must be free — a running local dev stack (`npm run db`, IntelliJ backend, `npm run dev` frontend) occupies them; stop it before booting.
- **DB readiness**: the `mysql` section's ready probe plus compose `depends_on: mysql: condition: service_healthy` covers the app; the app self-migrates an empty MySQL via Flyway before serving.
- **chameidor**: not booted; scheduling-side intents are unreachable and fine (job registration is lazy).
