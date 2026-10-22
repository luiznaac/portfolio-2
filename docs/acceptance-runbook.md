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
- **Order-plan seed**: the order plan prices positions from the latest stored close and reads the NEUTRO flag from a strategy's active Edition; the COTAHIST feed and a report import are not reachable in acceptance. After the app is healthy, reset the order-plan tables and seed the scenario the candidate and funding intents read. The month and its chain are posted over HTTP afterwards, so this seed clears them too. The scenario: strategy `Dividendos` holds VALE3 50 / ITUB4 40 / B3SA3 10, B3SA3 is `não operar`, ITUB4 is over its target and B3SA3 carries an attribution slice:

  ```
  docker compose exec -T mysql mysql -uroot portfolio \
    -e "DELETE FROM edition_entry; DELETE FROM edition; DELETE FROM attribution; DELETE FROM strategy; \
        DELETE FROM target; DELETE FROM month; DELETE FROM position; DELETE FROM quote; \
        INSERT INTO strategy (name, asset_class, weight, created_at) VALUES ('Dividendos', 'ACOES', 100, NOW()); \
        SET @strategy := LAST_INSERT_ID(); \
        INSERT INTO edition (strategy_id, reference_month, status, diff_from_previous_stored, created_at) \
          VALUES (@strategy, '2027-04', 'ACTIVE', NULL, NOW()); \
        SET @edition := LAST_INSERT_ID(); \
        INSERT INTO edition_entry (edition_id, ticker, weight, rating, target_price, do_not_trade) VALUES \
          (@edition, 'VALE3', 50, 'BUY', NULL, FALSE), \
          (@edition, 'ITUB4', 40, 'BUY', NULL, FALSE), \
          (@edition, 'B3SA3', 10, 'NEUTRAL', NULL, TRUE); \
        INSERT INTO attribution (ticker, strategy_id, quantity) VALUES ('B3SA3', @strategy, 20); \
        REPLACE INTO position (ticker, quantity, average_price) VALUES ('ITUB4', 100, 40), ('B3SA3', 20, 15); \
        REPLACE INTO quote (ticker, date, close, source, created_at) VALUES \
          ('VALE3', '2027-02-28', 10, 'COTAHIST', NOW()), \
          ('ITUB4', '2027-02-28', 50, 'COTAHIST', NOW()), \
          ('B3SA3', '2027-02-28', 17.58, 'COTAHIST', NOW());"
  ```
