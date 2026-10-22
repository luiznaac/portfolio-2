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
- **Order-plan seed**: the order plan prices positions from the latest stored close and reads the NEUTRO flag from a strategy's active Edition; the COTAHIST feed and a report import are not reachable in acceptance. After the app is healthy, reset the order tables and seed the scenario the plan, lifecycle and transfer intents read. The month and its chain are seeded here too (not posted over HTTP) so the run is deterministic. The scenario: two strategies (Dividendos / Small Caps) share ITUB4, B3SA3 and CSNA3 at a R$10 close; ITUB4 sits below its target (a BUY candidate the seeded manual trade executes), B3SA3 and CSNA3 sit above theirs (funding sells), and the Dividendos attribution is off-ideal against Small Caps so the refresh proposes one auto-approved transfer (ITUB4, R$200) and two pending ones (B3SA3 and CSNA3, R$600 each):

  ```
  docker compose exec -T mysql mysql -uroot portfolio \
    -e "DELETE FROM transfer_proposal; DELETE FROM suggested_order; \
        DELETE FROM edition_entry; DELETE FROM edition; DELETE FROM attribution; DELETE FROM trade; \
        DELETE FROM target; DELETE FROM month; DELETE FROM position; DELETE FROM quote; DELETE FROM strategy; DELETE FROM institution; \
        INSERT INTO strategy (name, asset_class, weight, created_at) VALUES ('Dividendos','ACOES',50,NOW()), ('Small Caps','ACOES',50,NOW()); \
        SET @div := (SELECT id FROM strategy WHERE name='Dividendos'); \
        INSERT INTO \`month\` (period, capital, \`floor\`, status, created_at) VALUES ('2027-04', 4000, 0, 'OPEN', NOW()); \
        SET @month := LAST_INSERT_ID(); \
        INSERT INTO target (month_id, \`level\`, asset_class, strategy, sub_class, ticker, percentage, created_at) VALUES \
          (@month,'CLASS','ACOES',NULL,NULL,NULL,100,NOW()), \
          (@month,'STRATEGY','ACOES','Dividendos',NULL,NULL,50,NOW()), \
          (@month,'STRATEGY','ACOES','Small Caps',NULL,NULL,50,NOW()), \
          (@month,'TICKER','ACOES','Dividendos',NULL,'ITUB4',40,NOW()), \
          (@month,'TICKER','ACOES','Dividendos',NULL,'B3SA3',30,NOW()), \
          (@month,'TICKER','ACOES','Dividendos',NULL,'CSNA3',30,NOW()), \
          (@month,'TICKER','ACOES','Small Caps',NULL,'ITUB4',40,NOW()), \
          (@month,'TICKER','ACOES','Small Caps',NULL,'B3SA3',30,NOW()), \
          (@month,'TICKER','ACOES','Small Caps',NULL,'CSNA3',30,NOW()); \
        REPLACE INTO position (ticker, quantity, average_price) VALUES ('ITUB4',100,8), ('B3SA3',260,8), ('CSNA3',150,8); \
        INSERT INTO attribution (ticker, strategy_id, quantity) VALUES ('ITUB4',@div,120), ('B3SA3',@div,260), ('CSNA3',@div,150); \
        REPLACE INTO quote (ticker, date, close, source, created_at) VALUES \
          ('ITUB4','2027-02-28',10,'COTAHIST',NOW()), ('B3SA3','2027-02-28',10,'COTAHIST',NOW()), ('CSNA3','2027-02-28',10,'COTAHIST',NOW()); \
        INSERT INTO institution (name, created_at) VALUES ('XP', NOW()); \
        SET @inst := LAST_INSERT_ID(); \
        INSERT INTO trade (ticker, date, venue, quantity, unit_price, institution_id, fee, source, confirmed_by_b3, ordinal, created_at) VALUES \
          ('ITUB4','2027-04-05','B3', 60, 10, @inst, NULL, 'MANUAL', FALSE, 1, NOW()), \
          ('B3SA3','2027-04-06','B3',-60, 10, @inst, NULL, 'B3_IMPORT', FALSE, 1, NOW());"
  ```
