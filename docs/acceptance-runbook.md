# Acceptance runbook — portfolio-2

Bootstrap-only runbook for the `self-served-acceptance` runner. The runner owns the browser process; this runbook boots `mysql` + `app` only.

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
| ready | `curl -fsS http://localhost:8110/health/internal` |
| port  | `8110` (API; web UI on `8111` for Browser intents) |
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

- **Port contention**: ports `8112` / `8110` / `8111` must be free — a running local dev stack (`npm run db`, IntelliJ backend, `npm run dev` frontend) occupies them; stop it before booting.

- **DB readiness**: the app section needs no runbook-level DB probe — the mysql section's ready probe plus compose `depends_on: mysql: condition: service_healthy` covers it; the app self-migrates an empty MySQL via Flyway (`bin/migrate` in `deploy/entrypoint.sh`) before serving.

- **chameidor**: not booted; scheduling-side intents are unreachable and fine (job registration is lazy — `ScheduleService` is reached only through consolidation runs).
- **Snapshot slice seed**: the monthly close freezes one slice per Strategy, the manual bucket included. This seed is self-contained: reset the close/plan tables, seed one report strategy that targets nothing and one manual bucket that owns the whole position, then the close's HTTP intents read the frozen slice. Run it alone — do **not** apply any other seed section.

  ```
  docker compose exec -T mysql mysql -uroot portfolio <<'SQL'
  DELETE FROM snapshot; DELETE FROM target; DELETE FROM transfer_proposal; DELETE FROM suggested_order;
  DELETE FROM month; DELETE FROM ticker_catalog; DELETE FROM quote; DELETE FROM trade; DELETE FROM position;
  DELETE FROM institution; DELETE FROM attribution; DELETE FROM edition_entry; DELETE FROM edition; DELETE FROM strategy;
  INSERT INTO institution (name, created_at) VALUES ('XP', NOW());
  SET @inst := LAST_INSERT_ID();
  INSERT INTO strategy (name, asset_class, kind, created_at) VALUES
    ('Top Dividendos', 'ACOES', 'REPORT_DRIVEN', NOW()),
    ('Legado', 'ACOES', 'MANUAL', NOW());
  SET @bucket := (SELECT id FROM strategy WHERE name = 'Legado');
  INSERT INTO month (id, period, capital, `floor`, aportes, status, created_at) VALUES
    (9001, '2026-10', 1000, 0, 0, 'OPEN', NOW());
  INSERT INTO target (month_id, `level`, asset_class, strategy, sub_class, ticker, percentage, created_at) VALUES
    (9001, 'CLASS', 'ACOES', NULL, NULL, NULL, 100, NOW()),
    (9001, 'STRATEGY', 'ACOES', 'Top Dividendos', NULL, NULL, 0, NOW());
  INSERT INTO ticker_catalog (ticker, short_name, bdi_type, isin, asset_kind, asset_class, first_seen, last_seen, created_at) VALUES
    ('ITUB4', 'ITAUUNIBANCO', '02', NULL, 'STOCK', 'ACOES', '2026-10-01', '2026-10-31', NOW());
  INSERT INTO quote (ticker, date, close, source, created_at) VALUES
    ('ITUB4', '2026-10-30', 10, 'COTAHIST', NOW());
  INSERT INTO trade (ticker, date, venue, quantity, unit_price, institution_id, fee, source, confirmed_by_b3, ordinal, created_at) VALUES
    ('ITUB4', '2026-10-05', 'VISTA', 100, 10, @inst, NULL, 'MANUAL', FALSE, 1, NOW());
  REPLACE INTO position (ticker, quantity, average_price) VALUES ('ITUB4', 100, 10);
  REPLACE INTO attribution (ticker, strategy_id, quantity) VALUES ('ITUB4', @bucket, 100);
  SQL
  ```

  The close is then `POST /month/current/close` (200) and the read is `GET /months/9001` (200). The
  frozen state must carry two `strategies` slices: `Legado` (`kind: MANUAL`, `value_end: 1000`,
  `flow: 0`) and `Top Dividendos` (`kind: REPORT_DRIVEN`, `value_end: 0`); the Ações class total is
  `actual: 1000` with `unattributed: 0`.
- **Order-plan seed**: the order plan prices positions from the latest stored close and reads the NEUTRO flag from a strategy's active Edition; the COTAHIST feed and a report import are not reachable in acceptance. After the app is healthy, reset the order tables and seed the scenario the plan, lifecycle and transfer intents read. The month and its chain are seeded here too (not posted over HTTP) so the run is deterministic. The scenario: two strategies (Dividendos / Small Caps) share ITUB4, B3SA3 and CSNA3 at a R$10 close; ITUB4 sits below its target (a BUY candidate the seeded manual trade executes), B3SA3 and CSNA3 sit above theirs (funding sells), and the Dividendos attribution is off-ideal against Small Caps so the refresh proposes one auto-approved transfer (ITUB4, R$200) and two pending ones (B3SA3 and CSNA3, R$600 each):

  ```
  docker compose exec -T mysql mysql -uroot portfolio \
    -e "DELETE FROM transfer_proposal; DELETE FROM suggested_order; \
        DELETE FROM edition_entry; DELETE FROM edition; DELETE FROM attribution; DELETE FROM trade; \
        DELETE FROM target; DELETE FROM month; DELETE FROM position; DELETE FROM quote; DELETE FROM ticker_catalog; DELETE FROM strategy; DELETE FROM institution; \
        INSERT INTO ticker_catalog (ticker, short_name, bdi_type, isin, asset_kind, asset_class, first_seen, last_seen, created_at) VALUES \
          ('ITUB4','ITAUUNIBANCO','02',NULL,'STOCK','ACOES','2027-01-01','2027-04-01',NOW()), \
          ('B3SA3','B3','02',NULL,'STOCK','ACOES','2027-01-01','2027-04-01',NOW()), \
          ('CSNA3','CSN','02',NULL,'STOCK','ACOES','2027-01-01','2027-04-01',NOW()); \
        INSERT INTO strategy (name, asset_class, kind, created_at) VALUES ('Dividendos','ACOES','REPORT_DRIVEN',NOW()), ('Small Caps','ACOES','REPORT_DRIVEN',NOW()); \
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

- **Placement seed**: the inside/outside/unclassified rule and the Funding ring-fence read positions, attributions, quotes and the catalog. This seed runs independently of the Order-plan seed: ACOES is 100% of Capital, Dividendos (report-driven) targets ITUB4 and B3SA3 at 50% each, B3SA3 holds 260 units against a 200-unit Target with 60 units attributed to the Legado Manual Strategy, ITUB4 holds 100 units with a stale 40-unit report attribution, and ZZZZ3 has a position and a quote but no catalog row. The plan therefore buys ITUB4's shortfall, sells no B3SA3 (its 60 surplus units are ring-fenced to Legado), leaves ZZZZ3 out of every Strategy, and reports it in `unclassified`. The catalog is seeded because classification is a precondition for attribution.

  ```
  docker compose exec -T mysql mysql -uroot portfolio \
    -e "DELETE FROM transfer_proposal; DELETE FROM suggested_order; \
        DELETE FROM edition_entry; DELETE FROM edition; DELETE FROM attribution; DELETE FROM trade; \
        DELETE FROM target; DELETE FROM month; DELETE FROM position; DELETE FROM quote; DELETE FROM ticker_catalog; DELETE FROM strategy; DELETE FROM institution; \
        INSERT INTO ticker_catalog (ticker, short_name, bdi_type, isin, asset_kind, asset_class, first_seen, last_seen, created_at) VALUES \
          ('ITUB4','ITAUUNIBANCO','02',NULL,'STOCK','ACOES','2027-01-01','2027-05-01',NOW()), \
          ('B3SA3','B3','02',NULL,'STOCK','ACOES','2027-01-01','2027-05-01',NOW()); \
        INSERT INTO strategy (name, asset_class, kind, created_at) VALUES \
          ('Dividendos','ACOES','REPORT_DRIVEN',NOW()), ('Legado','ACOES','MANUAL',NOW()); \
        SET @div := (SELECT id FROM strategy WHERE name='Dividendos'); \
        SET @legacy := (SELECT id FROM strategy WHERE name='Legado'); \
        INSERT INTO \`month\` (period, capital, \`floor\`, status, created_at) VALUES ('2027-05', 4000, 0, 'OPEN', NOW()); \
        SET @month := LAST_INSERT_ID(); \
        INSERT INTO target (month_id, \`level\`, asset_class, strategy, sub_class, ticker, percentage, created_at) VALUES \
          (@month,'CLASS','ACOES',NULL,NULL,NULL,100,NOW()), \
          (@month,'STRATEGY','ACOES','Dividendos',NULL,NULL,100,NOW()), \
          (@month,'TICKER','ACOES','Dividendos',NULL,'ITUB4',50,NOW()), \
          (@month,'TICKER','ACOES','Dividendos',NULL,'B3SA3',50,NOW()); \
        REPLACE INTO position (ticker, quantity, average_price) VALUES \
          ('ITUB4',100,8), ('B3SA3',260,8), ('ZZZZ3',10,5); \
        INSERT INTO attribution (ticker, strategy_id, quantity) VALUES ('ITUB4',@div,40), ('B3SA3',@legacy,60); \
        REPLACE INTO quote (ticker, date, close, source, created_at) VALUES \
          ('ITUB4','2027-05-30',10,'COTAHIST',NOW()), \
          ('B3SA3','2027-05-30',10,'COTAHIST',NOW()), \
          ('ZZZZ3','2027-05-30',5,'COTAHIST',NOW());"
  ```

  The reads are then deterministic: `GET /month/current/orders?phase=funding` returns an ITUB4 BUY, no SELL, `outside_strategy: []`, and one `unclassified` entry for ZZZZ3; `POST /positions/ZZZZ3/attribution` and a cross-class attribution both answer 400.

- **Allocation seed**: the Alocação tree and the manual Strategy detail read this scenario. It resets the same tables as the order-plan seed and seeds one report Strategy (Top Dividendos, id 9001), two manual buckets (Legado Ações id 9002, Legado FIIs id 9003), an open 2027-04 month, a 100% Ações class target whose report Strategy targets VALE3, three positions and quotes at R$10 (VALE3 100, ITUB4 50, ZZZZ9 10), the ledger split (VALE3: 60 to Top Dividendos + 20 to Legado Ações; ITUB4: 30 to Legado Ações), and a catalog row for ZZZZ9 with no class. The tree then reads: class Ações Actual 1.500,00 = Top Dividendos 600,00 + Legado Ações 500,00 + outside 400,00; the root adds the unclassified 100,00 for Actual 1.600,00 and unattributed 500,00; the manual detail for id 9002 totals 500,00 and id 9003 is empty. Run it independently of the other seeds.

  ```bash
  docker compose exec -T mysql mysql --default-character-set=utf8mb4 -uroot portfolio \
    -e "DELETE FROM transfer_proposal; DELETE FROM suggested_order; \
        DELETE FROM edition_entry; DELETE FROM edition; DELETE FROM attribution; DELETE FROM trade; \
        DELETE FROM target; DELETE FROM month; DELETE FROM position; DELETE FROM quote; \
        DELETE FROM ticker_catalog; DELETE FROM strategy; DELETE FROM institution; \
        INSERT INTO strategy (id, name, asset_class, kind, created_at) VALUES \
          (9001,'Top Dividendos','ACOES','REPORT_DRIVEN',NOW()), \
          (9002,'Legado Ações','ACOES','MANUAL',NOW()), \
          (9003,'Legado FIIs','FIIS','MANUAL',NOW()); \
        INSERT INTO \`month\` (id, period, capital, \`floor\`, status, created_at) VALUES (9100, '2027-04', 100000, 0, 'OPEN', NOW()); \
        INSERT INTO target (month_id, \`level\`, asset_class, strategy, sub_class, ticker, percentage, created_at) VALUES \
          (9100,'CLASS','ACOES',NULL,NULL,NULL,100,NOW()), \
          (9100,'STRATEGY','ACOES','Top Dividendos',NULL,NULL,100,NOW()), \
          (9100,'TICKER','ACOES','Top Dividendos',NULL,'VALE3',100,NOW()); \
        REPLACE INTO position (ticker, quantity, average_price) VALUES ('VALE3',100,8), ('ITUB4',50,8), ('ZZZZ9',10,8); \
        INSERT INTO attribution (ticker, strategy_id, quantity) VALUES ('VALE3',9001,60), ('VALE3',9002,20), ('ITUB4',9002,30); \
        REPLACE INTO quote (ticker, date, close, source, created_at) VALUES \
          ('VALE3','2027-04-30',10,'COTAHIST',NOW()), \
          ('ITUB4','2027-04-30',10,'COTAHIST',NOW()), \
          ('ZZZZ9','2027-04-30',10,'COTAHIST',NOW()); \
        REPLACE INTO ticker_catalog (ticker, short_name, bdi_type, isin, asset_kind, asset_class, first_seen, last_seen, created_at) VALUES \
          ('VALE3','VALE','02',NULL,'STOCK','ACOES','2027-01-01','2027-04-30',NOW()), \
          ('ITUB4','ITAUUNIBANCO','02',NULL,'STOCK','ACOES','2027-01-01','2027-04-30',NOW()), \
          ('ZZZZ9','ZZZZ','??',NULL,'UNKNOWN',NULL,'2027-01-01','2027-04-30',NOW());"
  ```

- **Month-free allocation seed**: the Alocação read no longer needs an open month. This seed clears the month and target tables but keeps the Allocation seed's custody: one report Strategy (Top Dividendos, id 9001), two manual buckets (Legado Ações id 9002, Legado FIIs id 9003), three positions and quotes at R$10 (VALE3 100, ITUB4 50, ZZZZ9 10), the ledger split (VALE3: 60 to Top Dividendos + 20 to Legado Ações; ITUB4: 30 to Legado Ações) and a catalog row for ZZZZ9 with no class. With no month the chain is empty, so the report attribution is inert: class Ações Actual 1.500,00 = Legado Ações 500,00 + outside 1.000,00 (VALE3's 60 report units and the 40 unstored remainder sit outside), while the root adds the unclassified 100,00 for Actual 1.600,00 and unattributed 1.100,00. `GET /month/current/allocation` answers 200 with no `period`; the Alocação page blanks Alvo/Ideal/Δ and the Capital panel stays empty. Run it independently of the other seeds.

  ```bash
  docker compose exec -T mysql mysql --default-character-set=utf8mb4 -uroot portfolio \
    -e "DELETE FROM transfer_proposal; DELETE FROM suggested_order; \
        DELETE FROM edition_entry; DELETE FROM edition; DELETE FROM attribution; DELETE FROM trade; \
        DELETE FROM target; DELETE FROM month; DELETE FROM position; DELETE FROM quote; \
        DELETE FROM ticker_catalog; DELETE FROM strategy; DELETE FROM institution; \
        INSERT INTO strategy (id, name, asset_class, kind, created_at) VALUES \
          (9001,'Top Dividendos','ACOES','REPORT_DRIVEN',NOW()), \
          (9002,'Legado Ações','ACOES','MANUAL',NOW()), \
          (9003,'Legado FIIs','FIIS','MANUAL',NOW()); \
        REPLACE INTO position (ticker, quantity, average_price) VALUES ('VALE3',100,8), ('ITUB4',50,8), ('ZZZZ9',10,8); \
        INSERT INTO attribution (ticker, strategy_id, quantity) VALUES ('VALE3',9001,60), ('VALE3',9002,20), ('ITUB4',9002,30); \
        REPLACE INTO quote (ticker, date, close, source, created_at) VALUES \
          ('VALE3','2027-04-30',10,'COTAHIST',NOW()), \
          ('ITUB4','2027-04-30',10,'COTAHIST',NOW()), \
          ('ZZZZ9','2027-04-30',10,'COTAHIST',NOW()); \
        REPLACE INTO ticker_catalog (ticker, short_name, bdi_type, isin, asset_kind, asset_class, first_seen, last_seen, created_at) VALUES \
          ('VALE3','VALE','02',NULL,'STOCK','ACOES','2027-01-01','2027-04-30',NOW()), \
          ('ITUB4','ITAUUNIBANCO','02',NULL,'STOCK','ACOES','2027-01-01','2027-04-30',NOW()), \
          ('ZZZZ9','ZZZZ','??',NULL,'UNKNOWN',NULL,'2027-01-01','2027-04-30',NOW());"
  ```

- **Strategy target ceiling seed**: run the Month-free allocation seed first, then add an open month whose report shares exercise the ceiling. Ações targets 80 (so the Legado Ações bucket's restante reads 20%), while Fundos imob. targets 60 + 50 = 110 — a state the write path now refuses, seeded directly so the form's inline mirror can be read. The Alvos Estratégias rung shows `Ações · restante: 20% — posições legadas, sem alvo` and `Fundos imob. · restante: 0% — posições legadas, sem alvo` (the Legado FIIs bucket has no target left), beside the Fundos imob. over-allocation error and a disabled Salvar alvos. `PUT /month/current/targets` with the seeded 110 answers 400 (`strategy-targets-exceed-class`); a replacement chain with Ações 100 and no FIIS strategy rows answers 200.

  ```bash
  docker compose exec -T mysql mysql --default-character-set=utf8mb4 -uroot portfolio \
    -e "DELETE FROM transfer_proposal; DELETE FROM suggested_order; DELETE FROM target; DELETE FROM month; \
        INSERT INTO \`month\` (id, period, capital, \`floor\`, status, created_at) VALUES (9100, '2027-07', 100000, 0, 'OPEN', NOW()); \
        INSERT INTO target (month_id, \`level\`, asset_class, strategy, sub_class, ticker, percentage, created_at) VALUES \
          (9100,'CLASS','ACOES',NULL,NULL,NULL,100,NOW()), \
          (9100,'CLASS','FIIS',NULL,NULL,NULL,0,NOW()), \
          (9100,'STRATEGY','ACOES','Top Dividendos',NULL,NULL,80,NOW()), \
          (9100,'STRATEGY','FIIS','Fundo A',NULL,NULL,60,NOW()), \
          (9100,'STRATEGY','FIIS','Fundo B',NULL,NULL,50,NOW()), \
          (9100,'TICKER','ACOES','Top Dividendos',NULL,'VALE3',100,NOW());"
  ```

- **Legacy attribution seed** (issue #260): the bulk legacy action reads outside custody, the manual slices and the catalog. Apply this seed after the app is healthy and before the bulk-legacy intents; it is independent of the other seeds. It gives one bucket Ticker with a custody delta (ITUB4: 30-unit bucket slice against 50 units of custody, so the preview grows it to 50), one Ticker with a report slice plus a bucket slice (VALE3, skipped with HAS_ATTRIBUTION), one FII with no bucket (HGLG11, skipped with NO_BUCKET) and one unclassified Ticker (ZZZZ9, skipped with UNCLASSIFIED). The Ações report Strategy targets VALE3 so that Ticker stays inside; the outside rows are ITUB4, VALE3 and HGLG11, for R$ 500,00 of outside custody.

  ```
  docker compose exec -T mysql mysql -uroot portfolio \
    -e "DELETE FROM transfer_proposal; DELETE FROM suggested_order; \
        DELETE FROM edition_entry; DELETE FROM edition; DELETE FROM attribution; DELETE FROM trade; \
        DELETE FROM target; DELETE FROM month; DELETE FROM position; DELETE FROM quote; \
        DELETE FROM ticker_catalog; DELETE FROM strategy; DELETE FROM institution; \
        INSERT INTO strategy (id, name, asset_class, kind, created_at) VALUES \
          (9001,'Top Dividendos','ACOES','REPORT_DRIVEN',NOW()), \
          (9002,'Legado Ações','ACOES','MANUAL',NOW()); \
        INSERT INTO \`month\` (id, period, capital, \`floor\`, status, created_at) VALUES (9100, '2027-04', 100000, 0, 'OPEN', NOW()); \
        INSERT INTO target (month_id, \`level\`, asset_class, strategy, sub_class, ticker, percentage, created_at) VALUES \
          (9100,'CLASS','ACOES',NULL,NULL,NULL,100,NOW()), \
          (9100,'STRATEGY','ACOES','Top Dividendos',NULL,NULL,100,NOW()), \
          (9100,'TICKER','ACOES','Top Dividendos',NULL,'VALE3',100,NOW()); \
        REPLACE INTO position (ticker, quantity, average_price) VALUES \
          ('ITUB4',50,8), ('VALE3',100,8), ('HGLG11',10,8), ('ZZZZ9',10,8); \
        INSERT INTO attribution (ticker, strategy_id, quantity) VALUES \
          ('VALE3',9001,60), ('VALE3',9002,20), ('ITUB4',9002,30); \
        REPLACE INTO quote (ticker, date, close, source, created_at) VALUES \
          ('ITUB4','2027-04-30',10,'COTAHIST',NOW()), \
          ('VALE3','2027-04-30',10,'COTAHIST',NOW()), \
          ('HGLG11','2027-04-30',10,'COTAHIST',NOW()), \
          ('ZZZZ9','2027-04-30',10,'COTAHIST',NOW()); \
        REPLACE INTO ticker_catalog (ticker, short_name, bdi_type, isin, asset_kind, asset_class, first_seen, last_seen, created_at) VALUES \
          ('ITUB4','ITAUUNIBANCO','02',NULL,'STOCK','ACOES','2027-01-01','2027-04-30',NOW()), \
          ('VALE3','VALE','02',NULL,'STOCK','ACOES','2027-01-01','2027-04-30',NOW()), \
          ('HGLG11','CSHG LOG','12',NULL,'FII','FIIS','2027-01-01','2027-04-30',NOW()), \
          ('ZZZZ9','ZZZZ','??',NULL,'UNKNOWN',NULL,'2027-01-01','2027-04-30',NOW());"
  ```

- **Manual-transfer seed**: the bucket's ticker leaf and its transfer modal read the attribution ledger, the strategies catalog and the order plan. This scenario seeds one report Strategy (Top Dividendos, id 9001) with an active 2027-04 Edition weighing ITUB4, VALE3 and PETR4, the Legado Ações bucket (id 9002), an open 2027-04 month whose class/strategy/ticker chain gives ITUB4 an 80-unit Target, four positions and quotes at R$10, and the ledger split (ITUB4: 40 and PETR4: 20 and CSNA3: 10 to Legado Ações; VALE3: 60 to Top Dividendos). On Alocação, expanding Ações and Legado Ações shows ITUB4 with a *Transferir* action; the modal offers Top Dividendos with "falta 80 cotas" and defaults the quantity to the 40-unit bucket slice. Applying it moves the attribution to Top Dividendos (ITUB4: 0 no legado, 40 na estratégia) with no inbox row and no month change. The CSNA3 bucket leaf has no destination (the Edition does not weigh it), so its modal tells the user to import an edition. The HTTP intent transfers PETR4's 20 bucket units to Top Dividendos first, leaving the Browser flow its own slice.

  ```bash
  docker compose exec -T mysql mysql --default-character-set=utf8mb4 -uroot portfolio \
    -e "DELETE FROM transfer_proposal; DELETE FROM suggested_order; \
        DELETE FROM edition_entry; DELETE FROM edition; DELETE FROM attribution; DELETE FROM trade; \
        DELETE FROM target; DELETE FROM month; DELETE FROM position; DELETE FROM quote; \
        DELETE FROM ticker_catalog; DELETE FROM strategy; DELETE FROM institution; \
        INSERT INTO strategy (id, name, asset_class, kind, created_at) VALUES \
          (9001,'Top Dividendos','ACOES','REPORT_DRIVEN',NOW()), \
          (9002,'Legado Ações','ACOES','MANUAL',NOW()); \
        INSERT INTO edition (id, strategy_id, reference_month, status, created_at) VALUES (9200, 9001, '2027-04', 'ACTIVE', NOW()); \
        INSERT INTO edition_entry (edition_id, ticker, weight, rating, target_price, do_not_trade) VALUES \
          (9200,'ITUB4',40,NULL,NULL,FALSE), (9200,'VALE3',40,NULL,NULL,FALSE), (9200,'PETR4',20,NULL,NULL,FALSE); \
        INSERT INTO \`month\` (id, period, capital, \`floor\`, status, created_at) VALUES (9100, '2027-04', 2000, 0, 'OPEN', NOW()); \
        INSERT INTO target (month_id, \`level\`, asset_class, strategy, sub_class, ticker, percentage, created_at) VALUES \
          (9100,'CLASS','ACOES',NULL,NULL,NULL,100,NOW()), \
          (9100,'STRATEGY','ACOES','Top Dividendos',NULL,NULL,100,NOW()), \
          (9100,'TICKER','ACOES','Top Dividendos',NULL,'ITUB4',40,NOW()), \
          (9100,'TICKER','ACOES','Top Dividendos',NULL,'VALE3',40,NOW()), \
          (9100,'TICKER','ACOES','Top Dividendos',NULL,'PETR4',20,NOW()); \
        REPLACE INTO position (ticker, quantity, average_price) VALUES ('ITUB4',100,8), ('VALE3',100,8), ('PETR4',20,8), ('CSNA3',10,8); \
        INSERT INTO attribution (ticker, strategy_id, quantity) VALUES ('ITUB4',9002,40), ('VALE3',9001,60), ('PETR4',9002,20), ('CSNA3',9002,10); \
        REPLACE INTO quote (ticker, date, close, source, created_at) VALUES \
          ('ITUB4','2027-04-30',10,'COTAHIST',NOW()), ('VALE3','2027-04-30',10,'COTAHIST',NOW()), \
          ('PETR4','2027-04-30',10,'COTAHIST',NOW()), ('CSNA3','2027-04-30',10,'COTAHIST',NOW()); \
        REPLACE INTO ticker_catalog (ticker, short_name, bdi_type, isin, asset_kind, asset_class, first_seen, last_seen, created_at) VALUES \
          ('ITUB4','ITAUUNIBANCO','02',NULL,'STOCK','ACOES','2027-01-01','2027-04-30',NOW()), \
          ('VALE3','VALE','02',NULL,'STOCK','ACOES','2027-01-01','2027-04-30',NOW()), \
          ('PETR4','PETROBRAS','02',NULL,'STOCK','ACOES','2027-01-01','2027-04-30',NOW()), \
          ('CSNA3','CSN','02',NULL,'STOCK','ACOES','2027-01-01','2027-04-30',NOW());"
  ```

- **Edições seed**: the strategy detail's Edições tab reads the edition timeline, the staged diff and both editions' entries. This scenario seeds one report Strategy (Top Dividendos, id 9301) with a superseded 2027-02 active edition, the current 2027-03 active edition, and a staged 2027-04 one whose stored diff enters CXSE3, exits B3SA3 and moves VALE3's weight and ITUB4's rating. The staged entries give the "alterada" rule one row per case — PETR4 unchanged, VALE3 weight-changed, ITUB4 rating-changed, EGIE3 target-price-only-changed and CXSE3 new. Run it alone.

  ```
  docker compose exec -T mysql mysql --default-character-set=utf8mb4 -uroot portfolio <<'SQL'
  DELETE FROM edition_entry; DELETE FROM edition; DELETE FROM strategy;
  INSERT INTO strategy (id, name, asset_class, kind, created_at) VALUES
    (9301, 'Top Dividendos', 'ACOES', 'REPORT_DRIVEN', NOW());
  INSERT INTO edition (id, strategy_id, reference_month, status, created_at) VALUES
    (9310, 9301, '2027-02', 'ACTIVE', NOW()),
    (9311, 9301, '2027-03', 'ACTIVE', NOW()),
    (9312, 9301, '2027-04', 'STAGED', NOW());
  UPDATE edition SET diff_from_previous_stored = '{"entered":[{"ticker":"CXSE3","weight":12.5}],"exited":[{"ticker":"B3SA3","weight":0.00}],"changed":[{"ticker":"VALE3","weight_before":25,"weight_after":22.5,"rating_before":null,"rating_after":null},{"ticker":"ITUB4","weight_before":20,"weight_after":20,"rating_before":"BUY","rating_after":"SELL"}]}' WHERE id = 9312;
  INSERT INTO edition_entry (edition_id, ticker, weight, rating, target_price, do_not_trade) VALUES
    (9310,'PETR4',50,'BUY',58,FALSE), (9310,'VALE3',50,'NEUTRAL',80,FALSE),
    (9311,'PETR4',25,'BUY',60,FALSE), (9311,'VALE3',25,'NEUTRAL',85,FALSE),
    (9311,'ITUB4',20,'BUY',30,FALSE), (9311,'EGIE3',20,'BUY',48,FALSE), (9311,'B3SA3',10,'NEUTRAL',12,FALSE),
    (9312,'PETR4',25,'BUY',60,FALSE), (9312,'VALE3',22.5,'NEUTRAL',85,FALSE),
    (9312,'ITUB4',20,'SELL',30,FALSE), (9312,'EGIE3',20,'BUY',52,FALSE), (9312,'CXSE3',12.5,'BUY',20,FALSE);
  SQL
  ```

- **Tax seed**: the stock-sale meter reads the catalog's asset kind, so the tax scenario seeds the catalog alongside trades and one corporate action. The catalog marks ITUB4/B3SA3/VALE3/CSNA3 as STOCK, HGLG11/KNRI11 as FII, SANB11 as UNIT, BOVA11 as ETF and AAPL34 as BDR. The month's metered stock sales are B3SA3 (R$600) plus VALE3 (R$20,000) for a R$20,600 meter over the cliff, while the FII, unit, ETF, BDR and the CSNA3 same-day round trip stay out. The VALE3 buy carries a fee (stored, ignored by the gross basis) and the ITUB4 2:1 split turns the R$10 average into R$5.

  The carry state seeds the three bucket openings (SWING 500, DAY_TRADE 300, FII 250) at the 2027-02 close, so the roll starts at March. Two prior-month losses bank before April: a CSNA3 swing loss of R$100 in the exempt March month (gross R$900, so it stays under the meter) and a KNRI11 FII loss of R$1,000. April therefore opens with SWING 600 and FII 1,250; the SWING carry offsets R$600 of the month's R$4,600 gain (taxable R$4,000, tax R$600), the FII carry offsets the whole R$1,000 gain (closing R$250), and the day-trade bucket stays at R$300 because April's CSNA3 round trip is flat. That round trip is imported so the day-trade warning guard sees it:

  ```
  docker compose exec -T mysql mysql -uroot portfolio \
    -e "DELETE FROM tax_carry_bucket; DELETE FROM corporate_action; DELETE FROM ticker_catalog; \
        REPLACE INTO ticker_catalog (ticker, short_name, bdi_type, isin, asset_kind, asset_class, first_seen, last_seen, created_at) VALUES \
          ('ITUB4','ITAUUNIBANCO','02',NULL,'STOCK','ACOES','2027-01-01','2027-04-01',NOW()), \
          ('B3SA3','B3','02',NULL,'STOCK','ACOES','2027-01-01','2027-04-01',NOW()), \
          ('VALE3','VALE','02',NULL,'STOCK','ACOES','2027-01-01','2027-04-01',NOW()), \
          ('CSNA3','CSN','02',NULL,'STOCK','ACOES','2027-01-01','2027-04-01',NOW()), \
          ('HGLG11','CSHG LOG','12',NULL,'FII','FIIS','2027-01-01','2027-04-01',NOW()), \
          ('KNRI11','KINEA RENDA','12',NULL,'FII','FIIS','2027-01-01','2027-04-01',NOW()), \
          ('SANB11','SANTANDER BR','02',NULL,'UNIT','ACOES','2027-01-01','2027-04-01',NOW()), \
          ('BOVA11','ISHARES BOVA','14',NULL,'ETF','ACOES','2027-01-01','2027-04-01',NOW()), \
          ('AAPL34','APPLE','34',NULL,'BDR','ACOES','2027-01-01','2027-04-01',NOW()); \
        INSERT INTO tax_carry_bucket (kind, opening_balance, as_of_period, created_at) VALUES \
          ('SWING', 500, '2027-02', NOW()), ('DAY_TRADE', 300, '2027-02', NOW()), ('FII', 250, '2027-02', NOW()); \
        SET @inst := (SELECT id FROM institution WHERE name='XP' LIMIT 1); \
        INSERT INTO corporate_action (ticker, kind, ex_date, approval_date, ratio, declared_value_per_share, target_ticker, source, process_number, raw_type, ordinal, file_hash, status, created_at) VALUES \
          ('ITUB4','SPLIT','2027-04-15',NULL,2,NULL,NULL,'B3_MOVEMENTS',NULL,NULL,1,NULL,'APPLIED',NOW()); \
        INSERT INTO trade (ticker, date, venue, quantity, unit_price, institution_id, fee, source, confirmed_by_b3, ordinal, created_at) VALUES \
          ('VALE3','2027-03-20','B3', 2000, 8, @inst, 1.25, 'MANUAL', FALSE, 1, NOW()), \
          ('VALE3','2027-04-07','B3',-2000, 10, @inst, NULL, 'MANUAL', FALSE, 1, NOW()), \
          ('CSNA3','2027-03-01','B3', 100, 10, @inst, NULL, 'MANUAL', FALSE, 1, NOW()), \
          ('CSNA3','2027-03-15','B3',-100, 9, @inst, NULL, 'MANUAL', FALSE, 1, NOW()), \
          ('HGLG11','2027-03-01','B3', 100, 150, @inst, NULL, 'MANUAL', FALSE, 1, NOW()), \
          ('HGLG11','2027-04-08','B3',-100, 160, @inst, NULL, 'MANUAL', FALSE, 1, NOW()), \
          ('KNRI11','2027-03-01','B3', 100, 100, @inst, NULL, 'MANUAL', FALSE, 1, NOW()), \
          ('KNRI11','2027-03-15','B3',-100, 90, @inst, NULL, 'MANUAL', FALSE, 1, NOW()), \
          ('SANB11','2027-03-01','B3', 100, 25, @inst, NULL, 'MANUAL', FALSE, 1, NOW()), \
          ('SANB11','2027-04-08','B3',-100, 26, @inst, NULL, 'MANUAL', FALSE, 1, NOW()), \
          ('BOVA11','2027-03-01','B3', 100, 100, @inst, NULL, 'MANUAL', FALSE, 1, NOW()), \
          ('BOVA11','2027-04-08','B3',-100, 110, @inst, NULL, 'MANUAL', FALSE, 1, NOW()), \
          ('AAPL34','2027-03-01','B3', 100, 40, @inst, NULL, 'MANUAL', FALSE, 1, NOW()), \
          ('AAPL34','2027-04-08','B3',-100, 45, @inst, NULL, 'MANUAL', FALSE, 1, NOW()), \
          ('CSNA3','2027-04-09','B3', 1000, 10, @inst, NULL, 'B3_IMPORT', FALSE, 1, NOW()), \
          ('CSNA3','2027-04-09','B3',-1000, 10, @inst, NULL, 'B3_IMPORT', FALSE, 1, NOW());"
  ```

- **Harvest seed**: the harvest pass reads the ledger, not the stored `position` table, so its scenario adds losing buys and their latest closes on top of the Order-plan seed (do **not** apply the Tax seed first — its VALE3 sale consumes the whole R$20k headroom). Catalog: ITUB4/B3SA3/CSNA3/PETR4/VALE3/ITSA4 as STOCK, HGLG11 as FII. The month's metered sales stay at the order seed's B3SA3 R$600, leaving R$19.4k of headroom. ITSA4 holds 100 units bought at R$22 with a R$10 close (a R$1,200 loss, unattributed); PETR4 holds 500 units bought at R$12 with a R$10 close (a R$1,000 loss, attributed to Dividendos, whose active edition flags it NEUTRO); VALE3 holds 300 at R$15 with a R$12 close (a R$900 loss) and is opted out, so it reports as ignored; HGLG11 holds 100 at R$100 with a R$110 close, so the FII report shows a R$1,000 gain and a R$200 estimate. The carry bucket rows seed the three openings so the Baldes panel has data:

  ```
  docker compose exec -T mysql mysql -uroot portfolio \
    -e "DELETE FROM harvest_opt_out; DELETE FROM harvest_pair; DELETE FROM tax_carry_bucket; DELETE FROM ticker_catalog; \
        DELETE FROM trade WHERE ticker IN ('PETR4','VALE3','HGLG11','ITSA4'); \
        DELETE FROM attribution WHERE ticker IN ('PETR4','ITSA4'); \
        SET @div := (SELECT id FROM strategy WHERE name='Dividendos' LIMIT 1); \
        DELETE FROM edition_entry WHERE edition_id IN (SELECT id FROM edition WHERE strategy_id=@div); \
        DELETE FROM edition WHERE strategy_id=@div; \
        INSERT INTO ticker_catalog (ticker, short_name, bdi_type, isin, asset_kind, asset_class, first_seen, last_seen, created_at) VALUES \
          ('ITUB4','ITAUUNIBANCO','02',NULL,'STOCK','ACOES','2027-01-01','2027-04-01',NOW()), \
          ('B3SA3','B3','02',NULL,'STOCK','ACOES','2027-01-01','2027-04-01',NOW()), \
          ('CSNA3','CSN','02',NULL,'STOCK','ACOES','2027-01-01','2027-04-01',NOW()), \
          ('PETR4','PETROBRAS','02',NULL,'STOCK','ACOES','2027-01-01','2027-04-01',NOW()), \
          ('VALE3','VALE','02',NULL,'STOCK','ACOES','2027-01-01','2027-04-01',NOW()), \
          ('ITSA4','ITAUSA','02',NULL,'STOCK','ACOES','2027-01-01','2027-04-01',NOW()), \
          ('HGLG11','CSHG LOG','12',NULL,'FII','FIIS','2027-01-01','2027-04-01',NOW()); \
        INSERT INTO tax_carry_bucket (kind, opening_balance, as_of_period, created_at) VALUES \
          ('SWING', 500, '2027-02', NOW()), ('DAY_TRADE', 300, '2027-02', NOW()), ('FII', 250, '2027-02', NOW()); \
        INSERT INTO edition (strategy_id, reference_month, status, created_at) VALUES (@div, '2027-04', 'ACTIVE', NOW()); \
        SET @ed := LAST_INSERT_ID(); \
        INSERT INTO edition_entry (edition_id, ticker, weight, rating, target_price, do_not_trade) VALUES \
          (@ed, 'PETR4', 100, 'NEUTRAL', NULL, TRUE), (@ed, 'ITSA4', 100, NULL, NULL, FALSE); \
        SET @inst := (SELECT id FROM institution WHERE name='XP' LIMIT 1); \
        INSERT INTO trade (ticker, date, venue, quantity, unit_price, institution_id, fee, source, confirmed_by_b3, ordinal, created_at) VALUES \
          ('PETR4','2027-04-02','B3', 500, 12, @inst, NULL, 'MANUAL', FALSE, 1, NOW()), \
          ('VALE3','2027-04-02','B3', 300, 15, @inst, NULL, 'MANUAL', FALSE, 1, NOW()), \
          ('HGLG11','2027-04-02','B3', 100, 100, @inst, NULL, 'MANUAL', FALSE, 1, NOW()), \
          ('ITSA4','2027-04-02','B3', 100, 22, @inst, NULL, 'MANUAL', FALSE, 1, NOW()); \
        REPLACE INTO position (ticker, quantity, average_price) VALUES \
          ('PETR4', 500, 12), ('VALE3', 300, 15), ('HGLG11', 100, 100), ('ITSA4', 100, 22); \
        REPLACE INTO quote (ticker, date, close, source, created_at) VALUES \
          ('PETR4','2027-04-30',10,'COTAHIST',NOW()), \
          ('VALE3','2027-04-30',12,'COTAHIST',NOW()), \
          ('ITSA4','2027-04-30',10,'COTAHIST',NOW()), \
          ('HGLG11','2027-04-30',110,'COTAHIST',NOW()); \
        INSERT INTO harvest_opt_out (ticker, created_at) VALUES ('VALE3', NOW()); \
        REPLACE INTO attribution (ticker, strategy_id, quantity) VALUES ('PETR4', @div, 500);"
  ```

  The expected read is two active suggestions in loss-descending order — ITSA4 first (100 units, R$1,200 loss, no slice) and PETR4 second (500 units, R$1,000 loss, NEUTRO, slice Dividendos) — VALE3 in the ignored list, and the HGLG11 FII row at 20%.

- **Performance + Fechamento seed**: the performance reads replay the immutable snapshots, not the live ledger, so this scenario seeds two closed months (January and February 2026) with their snapshot JSON, the target chain and one listed holding — plus an open March month (9003) with one pending Attribution Transfer, which is what the Fechamento screen's blocked close reads. January opens at R$100k; February closes at R$103.5k after a R$2k aporte and no income, so the decomposition is `(103500 − 100000 − 2000) ÷ 100000 = 1.5%`. Ações is 0% and Renda Fixa 100% (Pós-fixado), so the blended benchmark is the stored CDI return. The series is read from the `benchmark_close` table (IBOV comes from brapi/Yahoo, CDI and IPCA from BCB SGS); a target leg with no stored data makes the total absent rather than renormalising around it. Run the benchmark backfill below first if this database has never fetched the series. The February snapshot carries a R$45k caixa and two executed orders so the closed-month summary has data. Run it independently of the order/tax/harvest seeds.

  ```
  docker compose exec -T mysql mysql -uroot portfolio <<'SQL'
  DELETE FROM snapshot; DELETE FROM target; DELETE FROM transfer_proposal; DELETE FROM suggested_order; DELETE FROM month;
  DELETE FROM ticker_catalog; DELETE FROM quote; DELETE FROM cash_movement;
  DELETE FROM gap; DELETE FROM trade; DELETE FROM position; DELETE FROM institution;
  DELETE FROM attribution; DELETE FROM edition_entry; DELETE FROM edition; DELETE FROM strategy;
  INSERT INTO institution (name, created_at) VALUES ('XP', NOW());
  SET @inst := LAST_INSERT_ID();
  INSERT INTO trade (ticker, date, venue, quantity, unit_price, institution_id, fee, source, confirmed_by_b3, ordinal, created_at) VALUES
    ('PETR4', '2026-03-01', 'VISTA', 100, 10, @inst, NULL, 'OPENING', FALSE, 1, NOW()),
    ('PETR4', '2026-01-10', 'VISTA', 60, 10, @inst, NULL, 'B3_IMPORT', FALSE, 1, NOW()),
    ('PETR4', '2026-02-10', 'VISTA', 40, 10, @inst, NULL, 'B3_IMPORT', FALSE, 1, NOW());
  INSERT INTO gap (id, ticker, `date`, side, quantity, unit_price, status, trade_watermark, action_watermark, created_at) VALUES
    (9003, 'PETR4', '2026-02-20', 'SELL', 40, NULL, 'OPEN', 0, 0, NOW());
  INSERT INTO month (id, period, capital, `floor`, aportes, status, created_at) VALUES
    (9001, '2026-01', 100000, 0, 0, 'CLOSED', NOW()),
    (9002, '2026-02', 103500, 0, 2000, 'CLOSED', NOW()),
    (9003, '2026-03', 103500, 0, 0, 'OPEN', NOW());
  INSERT INTO strategy (name, asset_class, kind, created_at) VALUES
    ('Dividendos', 'ACOES', 'REPORT_DRIVEN', NOW()), ('Small Caps', 'ACOES', 'REPORT_DRIVEN', NOW());
  SET @div := (SELECT id FROM strategy WHERE name='Dividendos');
  SET @small := (SELECT id FROM strategy WHERE name='Small Caps');
  INSERT INTO transfer_proposal (ticker, month_id, from_strategy_id, to_strategy_id, quantity, amount, auto_approved, status, created_at) VALUES
    ('PETR4', 9003, @div, @small, 40, 400, FALSE, 'PENDING', NOW());
  INSERT INTO target (month_id, `level`, asset_class, strategy, sub_class, ticker, percentage, created_at) VALUES
    (9001, 'CLASS', 'ACOES', NULL, NULL, NULL, 0, NOW()),
    (9001, 'CLASS', 'RENDA_FIXA', NULL, NULL, NULL, 100, NOW()),
    (9001, 'SUB_CLASS', NULL, NULL, 'FLOATING_RATE', NULL, 100, NOW()),
    (9002, 'CLASS', 'ACOES', NULL, NULL, NULL, 0, NOW()),
    (9002, 'CLASS', 'RENDA_FIXA', NULL, NULL, NULL, 100, NOW()),
    (9002, 'SUB_CLASS', NULL, NULL, 'FLOATING_RATE', NULL, 100, NOW());
  INSERT INTO ticker_catalog (ticker, short_name, bdi_type, isin, asset_kind, asset_class, first_seen, last_seen, created_at) VALUES
    ('PETR4', 'PETROBRAS', '02', NULL, 'STOCK', 'ACOES', '2026-01-01', '2026-02-27', NOW());
  INSERT INTO quote (ticker, date, close, source, created_at) VALUES
    ('PETR4', '2026-01-30', 10, 'COTAHIST', NOW()),
    ('PETR4', '2026-02-27', 10, 'COTAHIST', NOW());
  INSERT INTO snapshot (month_id, state, created_at) VALUES
    (9001, '{"period":"2026-01","capital":100000,"floor":0,"aportes":0,"caixa":0,"holdings":[{"ticker":"PETR4","quantity":6000,"average_price":10}],"attributions":[],"classes":[{"asset_class":"ACOES","target":0,"ideal":0,"actual":60000,"deficit":0,"drift":0,"unattributed":0},{"asset_class":"RENDA_FIXA","target":100,"ideal":100000,"actual":40000,"deficit":0,"drift":0,"unattributed":0}],"income":{"gross":0,"retained":0,"net":0},"orders":{"orders":[],"transfers":[],"unplanned_trades":[]},"tax":{"carry":[],"meter":{"gross_sales":0,"exemption_limit":20000,"exceeded":false,"realized_gain":0,"taxable_gain":0,"rate":15,"tax":0}}}', NOW()),
    (9002, '{"period":"2026-02","capital":103500,"floor":0,"aportes":2000,"caixa":45000,"holdings":[{"ticker":"PETR4","quantity":6210,"average_price":10}],"attributions":[],"classes":[{"asset_class":"ACOES","target":0,"ideal":0,"actual":62100,"deficit":0,"drift":0,"unattributed":0},{"asset_class":"RENDA_FIXA","target":100,"ideal":103500,"actual":41400,"deficit":0,"drift":0,"unattributed":0}],"income":{"gross":0,"retained":0,"net":0},"orders":{"orders":[{"id":1,"ticker":"PETR4","side":"BUY","quantity":100,"suggested_price":10,"executed_quantity":100,"status":"IMPORTED"},{"id":2,"ticker":"PETR4","side":"BUY","quantity":100,"suggested_price":10,"executed_quantity":100,"status":"EXECUTED"}],"transfers":[],"unplanned_trades":[]},"tax":{"carry":[],"meter":{"gross_sales":0,"exemption_limit":20000,"exceeded":false,"realized_gain":0,"taxable_gain":0,"rate":15,"tax":0}}}', NOW());
  SQL
  ```

  The curl reads are then deterministic at ids `9001`/`9002`:

  ```
  curl -s http://localhost:8110/months/9002                   # the closed month's frozen snapshot
  curl -s http://localhost:8110/months/9002/performance   # decomposition 1.5%, xirr, benchmark
  curl -s http://localhost:8110/performance/series        # the two monthly points
  curl -s http://localhost:8110/gaps                      # the seeded missing sale, realized_pending true
  curl -s -X POST http://localhost:8110/gaps/9003/price -H 'Content-Type: application/json' -d '{"unit_price":18}'
  curl -s -X POST http://localhost:8110/history/backfill  # absorb the OPENING seed: absorbed_seeds 1
  ```

- **Computed performance era seed (#308)**: run this seed alone, immediately before the
  computed-era intents; it resets the ledger and leaves **no Snapshot and no Month**, so the
  performance series is the pre-planning computed era only. Dates are relative to `CURDATE()` so the
  window always ends at the last completed month. Five monthly points: buys in the first two months,
  an unpriced held ticker from the third, and a sell-only month before the last (negative aporte).
  `PETR4` is priced at every month end, `DELIST3` only in its first month (so it keeps that last
  close), and `ZZZZ9` never (so every month from its buy is `parcial`). One dividend lands in the
  second month. IBOV has closes for the first three month-ends only: the last two computed months
  have no priced IBOV, so the line breaks and the coverage strip names them; CDI/IPCA stay absent.
  The data panel's IBOV row shows the seeded `fetched_at`; the other indexes read "sem busca".

  ```
  docker compose exec -T mysql mysql -uroot portfolio <<'SQL'
  DELETE FROM snapshot; DELETE FROM target; DELETE FROM transfer_proposal; DELETE FROM suggested_order;
  DELETE FROM month; DELETE FROM ticker_catalog; DELETE FROM quote; DELETE FROM cash_movement;
  DELETE FROM gap; DELETE FROM trade; DELETE FROM position; DELETE FROM institution;
  DELETE FROM attribution; DELETE FROM edition_entry; DELETE FROM edition; DELETE FROM strategy;
  DELETE FROM benchmark_close;
  INSERT INTO institution (name, created_at) VALUES ('XP', NOW());
  SET @inst := LAST_INSERT_ID();
  SET @m5 := DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 5 MONTH), '%Y-%m-01');
  SET @m4 := DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 4 MONTH), '%Y-%m-01');
  SET @m3 := DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 3 MONTH), '%Y-%m-01');
  SET @m2 := DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 2 MONTH), '%Y-%m-01');
  SET @m1 := DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 1 MONTH), '%Y-%m-01');
  INSERT INTO ticker_catalog (ticker, short_name, bdi_type, isin, asset_kind, asset_class, first_seen, last_seen, created_at) VALUES
    ('PETR4', 'PETROBRAS', '02', NULL, 'STOCK', 'ACOES', @m5, @m1, NOW()),
    ('DELIST3', 'DELISTED', '02', NULL, 'STOCK', 'ACOES', @m5, @m5, NOW());
  INSERT INTO trade (ticker, date, venue, quantity, unit_price, institution_id, fee, source, confirmed_by_b3, ordinal, created_at) VALUES
    ('PETR4', @m5, 'VISTA', 100, 10, @inst, NULL, 'B3_IMPORT', FALSE, 1, NOW()),
    ('DELIST3', @m5, 'VISTA', 10, 5, @inst, NULL, 'B3_IMPORT', FALSE, 1, NOW()),
    ('PETR4', @m4, 'VISTA', 100, 12, @inst, NULL, 'B3_IMPORT', FALSE, 1, NOW()),
    ('ZZZZ9', @m3, 'VISTA', 5, 4, @inst, NULL, 'B3_IMPORT', FALSE, 1, NOW()),
    ('PETR4', @m2, 'VISTA', -50, 15, @inst, NULL, 'B3_IMPORT', FALSE, 1, NOW());
  INSERT INTO quote (ticker, date, close, source, created_at) VALUES
    ('PETR4', LAST_DAY(@m5), 10, 'COTAHIST', NOW()),
    ('PETR4', LAST_DAY(@m4), 12, 'COTAHIST', NOW()),
    ('PETR4', LAST_DAY(@m3), 13, 'COTAHIST', NOW()),
    ('PETR4', LAST_DAY(@m2), 15, 'COTAHIST', NOW()),
    ('PETR4', LAST_DAY(@m1), 16, 'COTAHIST', NOW()),
    ('DELIST3', LAST_DAY(@m5), 5, 'COTAHIST', NOW());
  INSERT INTO cash_movement (ticker, `date`, gross_amount, retained_amount, net_amount, tax_nature, provenance, status, income_type, created_at) VALUES
    ('PETR4', LAST_DAY(@m4), 50, 0, 50, 'INCOME', 'B3', 'RECEIVED', 'DIVIDEND', NOW());
  INSERT INTO benchmark_close (`index`, `date`, level, source, fetched_at) VALUES
    ('IBOV', LAST_DAY(@m5), 100000, 'SGS', NOW()),
    ('IBOV', LAST_DAY(@m4), 102000, 'SGS', NOW()),
    ('IBOV', LAST_DAY(@m3), 104000, 'SGS', NOW());
  SQL
  ```

  The series read is then `GET /performance/series`: five computed points (the first with a null
  return and null `value_start`, the sell-only month with a negative `net_aportes`), no snapshot
  point, `computed_xirr` present and `xirr` absent. The Performance screen shows "Valor investido",
  the era divider, `parcial` on the months that hold `ZZZZ9`, and the IBOV coverage strip; the
  "Detalhe do mês" panel explains it is Snapshot-only.

- **Benchmark store backfill (one-off)**: the benchmark series lives in the `benchmark_close`
  table, so a fresh database has no history and every blend reads absent. After the app is healthy,
  seed it once (there is no scheduler; freshness is lazy gap-fill plus this call):

  ```
  curl -s -X POST 'http://localhost:8110/benchmarks/backfill?from=2018-01-01'
  ```

  The response is one row per index — `{"indexes":[{"index":"IBOV","from":"2018-01-01","to":"…","points":…}]}`
  — where `points` is the stored count for the window. IBOV is filled from brapi first (its free
  plan clamps to three months, so `BRAPI_TOKEN` is optional and only lifts the clamp) and then Yahoo
  `^BVSP` for the rest; CDI and IPCA from BCB SGS. Re-running is safe: existing points are upserted,
  never duplicated, and an empty provider response leaves the stored rows untouched.

- **Legacy seed prose**: the seed paragraphs below predate the Carteira · Visão geral landing and name the retired Cockpit's panels (ritual stepper, agenda, class strip, split attention cards). Read those references as historical fixture descriptions for the screens they exercise, not as the current landing.

- **Open-month seed**: run the Performance + Fechamento seed first — its open month (9003) and pending transfer feed the landing's month line and attention queue. This seed moves 9003's period to the previous calendar month, then adds the proventos and edition rows the Análise and Estratégias screens read. Dates are relative to `CURDATE()` so the maturity stays inside the seven-day lead and the payment inside the thirty-day window whenever acceptance runs:

  ```
  docker compose exec -T mysql mysql -uroot portfolio <<'SQL'
  UPDATE `month` SET period = DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 1 MONTH), '%Y-%m') WHERE id = 9003;
  INSERT INTO bond (name, rate_type, rate, index_id, maturity_date, checking_account_id, created_at) VALUES
    ('CDB Aceite', 'FIXED', 12, NULL, DATE_ADD(CURDATE(), INTERVAL 3 DAY), NULL, NOW());
  INSERT INTO cash_movement (ticker, `date`, gross_amount, retained_amount, net_amount, tax_nature, provenance, status, income_type, created_at) VALUES
    ('PETR4', DATE_ADD(CURDATE(), INTERVAL 5 DAY), 120, 0, 120, 'INCOME', 'B3', 'ANNOUNCED', 'DIVIDEND', NOW());
  INSERT INTO edition (strategy_id, reference_month, status, created_at) VALUES
    ((SELECT id FROM strategy WHERE name='Dividendos'), (SELECT period FROM `month` WHERE id=9003), 'ACTIVE', NOW()),
    ((SELECT id FROM strategy WHERE name='Small Caps'), (SELECT period FROM `month` WHERE id=9003), 'ACTIVE', NOW());
  INSERT INTO target (month_id, `level`, asset_class, strategy, sub_class, ticker, percentage, created_at) VALUES
    (9003, 'CLASS', 'ACOES', NULL, NULL, NULL, 60, NOW()),
    (9003, 'CLASS', 'FIIS', NULL, NULL, NULL, 20, NOW()),
    (9003, 'CLASS', 'RENDA_FIXA', NULL, NULL, NULL, 20, NOW());
  REPLACE INTO position (ticker, quantity, average_price) VALUES ('PETR4', 2000, 10);
  REPLACE INTO attribution (ticker, strategy_id, quantity) VALUES
    ('PETR4', (SELECT id FROM strategy WHERE name='Dividendos'), 500);
  SQL
  ```

  Browser intents then read the shell against real data: the six-object sidebar, the top bar's quote freshness and open-month line, the open month on Plano / Mês, and `PETR4` among the próximos proventos; the retired command palette has no trigger and no shortcut. The landing's own acceptance intents are derived from its ticket, not from this seed.

- **Legacy-only seed** (issue #263): the retired report-only editions step and split attention cards read a book with no report-driven Strategy at all. This seed resets the plan, allocation and catalog tables and seeds one manual bucket (Legado Ações, id 9002) that holds 30 of ITUB4's 50 units, an open 2027-06 month with a 100% Ações class target, and an unclassified ZZZZ9 with a quote but no catalog class. The tree then reads: no report-driven Strategy, so step 1 is complete and reads `Nenhuma estratégia com relatório`; class Ações Actual 500,00 = Legado Ações 300,00 + outside 200,00; the root adds the unclassified 100,00. The status aggregate's attention queue therefore carries the unclassified ticker as an ACTION finding; outside custody stays a planning state on Alocação, and the manual bucket generates no finding. Run it independently of the other seeds.

  ```bash
  docker compose exec -T mysql mysql --default-character-set=utf8mb4 -uroot portfolio \
    -e "DELETE FROM transfer_proposal; DELETE FROM suggested_order; \
        DELETE FROM edition_entry; DELETE FROM edition; DELETE FROM attribution; DELETE FROM trade; \
        DELETE FROM target; DELETE FROM month; DELETE FROM position; DELETE FROM quote; \
        DELETE FROM ticker_catalog; DELETE FROM strategy; DELETE FROM institution; \
        INSERT INTO strategy (id, name, asset_class, kind, created_at) VALUES \
          (9002,'Legado Ações','ACOES','MANUAL',NOW()); \
        INSERT INTO \`month\` (id, period, capital, \`floor\`, status, created_at) VALUES (9200, '2027-06', 1000, 0, 'OPEN', NOW()); \
        INSERT INTO target (month_id, \`level\`, asset_class, strategy, sub_class, ticker, percentage, created_at) VALUES \
          (9200,'CLASS','ACOES',NULL,NULL,NULL,100,NOW()); \
        REPLACE INTO position (ticker, quantity, average_price) VALUES ('ITUB4',50,8), ('ZZZZ9',10,8); \
        INSERT INTO attribution (ticker, strategy_id, quantity) VALUES ('ITUB4',9002,30); \
        REPLACE INTO quote (ticker, date, close, source, created_at) VALUES \
          ('ITUB4','2027-06-30',10,'COTAHIST',NOW()), \
          ('ZZZZ9','2027-06-30',10,'COTAHIST',NOW()); \
        REPLACE INTO ticker_catalog (ticker, short_name, bdi_type, isin, asset_kind, asset_class, first_seen, last_seen, created_at) VALUES \
          ('ITUB4','ITAUUNIBANCO','02',NULL,'STOCK','ACOES','2027-01-01','2027-06-30',NOW()), \
          ('ZZZZ9','ZZZZ','??',NULL,'UNKNOWN',NULL,'2027-01-01','2027-06-30',NOW());"
  ```

- **Catalog classification seed**: the Alocação "Sem classe" line and the asset detail's classify/revert action read this scenario. It resets the allocation tables and seeds an open 2027-05 month with a 100% Ações class target, two positions and quotes at R$10, a catalog row for ZZZZ9 with no class at all (the unclassified state), and a catalog row for YYYY9 whose BDI class is FII but whose manual override is Ações (the disagreement the detail's revert action reads). The tree then reads class Ações Actual 100,00 plus the unclassified 100,00; classifying ZZZZ9 as Ações moves it into the class and removes the "Sem classe" line. Run it independently of the other seeds.

  ```bash
  docker compose exec -T mysql mysql --default-character-set=utf8mb4 -uroot portfolio \
    -e "DELETE FROM transfer_proposal; DELETE FROM suggested_order; \
        DELETE FROM edition_entry; DELETE FROM edition; DELETE FROM attribution; DELETE FROM trade; \
        DELETE FROM target; DELETE FROM month; DELETE FROM position; DELETE FROM quote; \
        DELETE FROM ticker_catalog; DELETE FROM strategy; DELETE FROM institution; \
        INSERT INTO \`month\` (id, period, capital, \`floor\`, status, created_at) VALUES (9200, '2027-05', 1000, 0, 'OPEN', NOW()); \
        INSERT INTO target (month_id, \`level\`, asset_class, strategy, sub_class, ticker, percentage, created_at) VALUES \
          (9200,'CLASS','ACOES',NULL,NULL,NULL,100,NOW()); \
        REPLACE INTO position (ticker, quantity, average_price) VALUES ('ZZZZ9',10,8), ('YYYY9',10,8); \
        REPLACE INTO quote (ticker, date, close, source, created_at) VALUES \
          ('ZZZZ9','2027-05-31',10,'COTAHIST',NOW()), \
          ('YYYY9','2027-05-31',10,'COTAHIST',NOW()); \
        REPLACE INTO ticker_catalog (ticker, short_name, bdi_type, isin, asset_kind, asset_class, asset_class_override, first_seen, last_seen, created_at) VALUES \
          ('ZZZZ9','ZZZZ','??',NULL,'UNKNOWN',NULL,NULL,'2027-01-01','2027-05-31',NOW()), \
          ('YYYY9','YYYY','12',NULL,'FII','FIIS','ACOES','2027-01-01','2027-05-31',NOW());"
  ```

- **Bucket-transfer seed**: the plan-suggested bucket transfer reads the order plan, the attribution ledger, the strategies catalog and the current month. This seed gives one report Strategy (Top Dividendos, id 9001) whose ITUB4 Target is its whole 100-unit custody, with 90 units attributed to it and 10 to the Legado bucket (id 9002) at a R$10 close. `POST /orders/plan/refresh` therefore proposes one PENDING transfer of 10 whole units, worth R$100 — below the R$500 threshold that would auto-approve a report-to-report move — from Legado to Top Dividendos; the bucket row keeps its shape and its source id resolves to the Legado Strategy. A second refresh after a rejection would drop it, and `POST /month/current/close` answers 409 while it is pending. Run it independently of the other seeds.

  ```bash
  docker compose exec -T mysql mysql --default-character-set=utf8mb4 -uroot portfolio \
    -e "DELETE FROM transfer_proposal; DELETE FROM suggested_order; \
        DELETE FROM edition_entry; DELETE FROM edition; DELETE FROM attribution; DELETE FROM trade; \
        DELETE FROM target; DELETE FROM month; DELETE FROM position; DELETE FROM quote; \
        DELETE FROM ticker_catalog; DELETE FROM strategy; DELETE FROM institution; \
        INSERT INTO institution (name, created_at) VALUES ('XP', NOW()); \
        INSERT INTO strategy (id, name, asset_class, kind, created_at) VALUES \
          (9001,'Top Dividendos','ACOES','REPORT_DRIVEN',NOW()), \
          (9002,'Legado','ACOES','MANUAL',NOW()); \
        INSERT INTO \`month\` (id, period, capital, \`floor\`, status, created_at) VALUES (9200, '2027-06', 1000, 0, 'OPEN', NOW()); \
        INSERT INTO target (month_id, \`level\`, asset_class, strategy, sub_class, ticker, percentage, created_at) VALUES \
          (9200,'CLASS','ACOES',NULL,NULL,NULL,100,NOW()), \
          (9200,'STRATEGY','ACOES','Top Dividendos',NULL,NULL,100,NOW()), \
          (9200,'TICKER','ACOES','Top Dividendos',NULL,'ITUB4',100,NOW()); \
        REPLACE INTO position (ticker, quantity, average_price) VALUES ('ITUB4',100,8); \
        INSERT INTO attribution (ticker, strategy_id, quantity) VALUES ('ITUB4',9001,90), ('ITUB4',9002,10); \
        REPLACE INTO quote (ticker, date, close, source, created_at) VALUES ('ITUB4','2027-06-30',10,'COTAHIST',NOW()); \
        REPLACE INTO ticker_catalog (ticker, short_name, bdi_type, isin, asset_kind, asset_class, first_seen, last_seen, created_at) VALUES \
          ('ITUB4','ITAUUNIBANCO','02',NULL,'STOCK','ACOES','2027-01-01','2027-06-30',NOW());"
  ```

  The reads are then deterministic: `GET /orders` (after the refresh) carries one transfer with `from_strategy: "Legado"`, `to_strategy: "Top Dividendos"`, `quantity: 10`, `amount: 100`, `auto_approved: false`, `status: "PENDING"`; the Ordens page renders its caption as `move R$ 100,00 do legado`.

- **Valuation and data-quality seed**: the honest-valuation read and the "Dados da carteira" panel need one book with a priced stale close, an unpriced holding, a negative position and an unclassified holding. This seed resets the allocation tables and seeds an open current month (id 9300, 2026-10) with Ações 100 and Fundos imob. 0; one report Strategy (Top Dividendos, id 9001) targeting VALE3 100; PETR4 10 units at a 2026-10-07 close (stale), VALE3 5 units with no close (attributed to Top Dividendos, so its leaf is `—`), BCFF11 −10 units at a 2026-10-29 close, and ZZZZ9 3 units priced but with no catalog row (unclassified). The read is then: root Actual 424,20 marked parcial, Ações Actual 489,20 marked parcial (Top Dividendos leaf unknown, outside PETR4 489,20 marked `desatualizada · 07/10/2026`), Fundos imob. Actual −80,00, unclassified 15,00; `unpriced` holds VALE3 5, `negative` holds BCFF11 −10. The panel reads Sem cotação 1, Tickers sem classe 1, Posições negativas 1, and the data-quality panel on Alocação reads the same three counts.

  ```bash
  docker compose exec -T mysql mysql --default-character-set=utf8mb4 -uroot portfolio \
    -e "DELETE FROM transfer_proposal; DELETE FROM suggested_order; \
        DELETE FROM edition_entry; DELETE FROM edition; DELETE FROM attribution; DELETE FROM trade; \
        DELETE FROM target; DELETE FROM month; DELETE FROM position; DELETE FROM quote; \
        DELETE FROM ticker_catalog; DELETE FROM strategy; DELETE FROM institution; \
        INSERT INTO strategy (id, name, asset_class, kind, created_at) VALUES \
          (9001,'Top Dividendos','ACOES','REPORT_DRIVEN',NOW()), \
          (9002,'Legado Ações','ACOES','MANUAL',NOW()); \
        INSERT INTO \`month\` (id, period, capital, \`floor\`, status, created_at) VALUES (9300, '2026-10', 100000, 0, 'OPEN', NOW()); \
        INSERT INTO target (month_id, \`level\`, asset_class, strategy, sub_class, ticker, percentage, created_at) VALUES \
          (9300,'CLASS','ACOES',NULL,NULL,NULL,100,NOW()), \
          (9300,'CLASS','FIIS',NULL,NULL,NULL,0,NOW()), \
          (9300,'STRATEGY','ACOES','Top Dividendos',NULL,NULL,100,NOW()), \
          (9300,'TICKER','ACOES','Top Dividendos',NULL,'VALE3',100,NOW()); \
        REPLACE INTO position (ticker, quantity, average_price) VALUES \
          ('PETR4',10,30), ('VALE3',5,60), ('BCFF11',-10,8), ('ZZZZ9',3,5); \
        INSERT INTO attribution (ticker, strategy_id, quantity) VALUES ('VALE3',9001,5); \
        REPLACE INTO quote (ticker, date, close, source, created_at) VALUES \
          ('PETR4','2026-10-07',48.92,'COTAHIST',NOW()), \
          ('BCFF11','2026-10-29',8.00,'COTAHIST',NOW()), \
          ('ZZZZ9','2026-10-29',5.00,'COTAHIST',NOW()); \
        REPLACE INTO ticker_catalog (ticker, short_name, bdi_type, isin, asset_kind, asset_class, first_seen, last_seen, created_at) VALUES \
          ('PETR4','PETROBRAS','02',NULL,'STOCK','ACOES','2026-01-01','2026-10-30',NOW()), \
          ('VALE3','VALE','02',NULL,'STOCK','ACOES','2026-01-01','2026-10-30',NOW()), \
          ('BCFF11','BCFF','12',NULL,'FII','FIIS','2026-01-01','2026-10-30',NOW());"
  ```

  Run it independently of the other seeds, after the app has migrated and is healthy. The panel's refresh button is visible in its header and beside the unpriced VALE3 row; the import result carries the same refresh action and the negative deep-link once the browser can upload a file (see #311).

- **Histórico seed** (issue #307): adoption, replay, gaps and custody read this scenario. It resets the ledger tables and seeds four negative held positions (the mid-history export's unexplained custody), one imported trade that sets the default adoption date, a latest close per ticker, and two open GAPs — a missing sale whose proceeds are pending and a missing buy entered at a market placeholder. The browser reads `/history`: Adoção lists PETR4 10, VALE3 5, ITUB4 3 and BCFF11 4 with the default date 09/01/2020 and their latest closes; saving creates one opening trade per row and raises every stored position to non-negative custody; Reconciliar histórico unlocks only after the save or an explicit skip; Lacunas shows VALE3 as approximate and PETR4 as "proventos pendentes"; Conferir custódia stays read-only and takes a Posição upload; the committed custody fixture and its drift rows are the [#338](https://github.com/luiznaac/portfolio-2/issues/338) scenario below.

  ```bash
  docker compose exec -T mysql mysql --default-character-set=utf8mb4 -uroot portfolio \
    -e "DELETE FROM gap; DELETE FROM transfer_proposal; DELETE FROM suggested_order; \
        DELETE FROM edition_entry; DELETE FROM edition; DELETE FROM attribution; DELETE FROM trade; \
        DELETE FROM target; DELETE FROM month; DELETE FROM position; DELETE FROM quote; \
        DELETE FROM ticker_catalog; DELETE FROM strategy; DELETE FROM institution; \
        INSERT INTO institution (id, name, created_at) VALUES (9001, 'XP', NOW()); \
        INSERT INTO \`month\` (id, period, capital, \`floor\`, aportes, status, created_at) VALUES (9400, '2026-11', 100000, 0, 0, 'OPEN', NOW()); \
        INSERT INTO trade (ticker, date, venue, quantity, unit_price, institution_id, fee, source, confirmed_by_b3, ordinal, created_at) VALUES \
          ('PETR4', '2020-01-10', 'VISTA', 10, 28.50, 9001, NULL, 'B3_IMPORT', FALSE, 1, NOW()); \
        REPLACE INTO position (ticker, quantity, average_price) VALUES \
          ('PETR4', -10, 0), ('VALE3', -5, 0), ('ITUB4', -3, 0), ('BCFF11', -4, 0); \
        REPLACE INTO quote (ticker, date, close, source, created_at) VALUES \
          ('PETR4', '2020-12-30', 28.50, 'COTAHIST', NOW()), \
          ('VALE3', '2020-12-30', 60.00, 'COTAHIST', NOW()), \
          ('ITUB4', '2020-12-30', 30.00, 'COTAHIST', NOW()), \
          ('BCFF11', '2020-12-30', 8.00, 'COTAHIST', NOW()); \
        REPLACE INTO ticker_catalog (ticker, short_name, bdi_type, isin, asset_kind, asset_class, first_seen, last_seen, created_at) VALUES \
          ('PETR4','PETROBRAS','02',NULL,'STOCK','ACOES','2020-01-01','2020-12-30',NOW()), \
          ('VALE3','VALE','02',NULL,'STOCK','ACOES','2020-01-01','2020-12-30',NOW()), \
          ('ITUB4','ITAUUNIBANCO','02',NULL,'STOCK','ACOES','2020-01-01','2020-12-30',NOW()), \
          ('BCFF11','BCFF','12',NULL,'FII','FIIS','2020-01-01','2020-12-30',NOW()); \
        INSERT INTO gap (id, ticker, date, side, quantity, unit_price, status, trade_watermark, action_watermark, created_at) VALUES \
          (9401, 'VALE3', '2020-12-30', 'BUY', 2, 60.00, 'OPEN', 0, 0, NOW()), \
          (9402, 'PETR4', '2020-12-30', 'SELL', 3, NULL, 'OPEN', 0, 0, NOW());"
  ```

  Run it independently of the other seeds. After the adoption is saved, the replay's `positions_reprojected` is 4 — every ledger ticker, not only the ones the run touched. Skipping the adoption leaves one imported ticker in the ledger, so the same replay reports 1; either way the count is greater than zero.

- **Classification authority and plan-gate seed** (issue #306): the bulk classification modal, the legacy preview's effective class and the plan's refusal (read on Plano / Mês · Ordens) read this scenario. It resets the plan, allocation and catalog tables and seeds an open 2027-06 month with an Ações class and a Top Dividendos strategy/ticker target, a manual Legado Ações bucket, and four held tickers at a R$10 close except one left unpriced: **OVR9** (10 un) has a placeholder BDI row with a manual `ACOES` override (the override-only ticker), **UNCL1** (5 un) has a placeholder BDI row with no class (queue reason `ABSENT_FROM_LATEST_FILE`), **UNCL2** (3 un) has an unmapped BDI `99` with no class (queue reason `UNHANDLED_BDI_CODE`), and **NOPR1** (4 un) is a classified Ações ticker with no close (the unpriced gate). **NOPOS3** is named only by a ticker target — no position, no close. The book then reads: the queue is UNCL1 + UNCL2 with their reasons; the preview groups OVR9 (override Ações) and NOPR1 under Ações and skips UNCL1/UNCL2 (`UNCLASSIFIED`); the plan refuses with `unpriced-positions` naming NOPR1.

  ```bash
  docker compose exec -T mysql mysql --default-character-set=utf8mb4 -uroot portfolio \
    -e "DELETE FROM transfer_proposal; DELETE FROM suggested_order; \
        DELETE FROM edition_entry; DELETE FROM edition; DELETE FROM attribution; DELETE FROM trade; \
        DELETE FROM target; DELETE FROM month; DELETE FROM position; DELETE FROM quote; \
        DELETE FROM ticker_catalog; DELETE FROM strategy; DELETE FROM institution; \
        INSERT INTO institution (name, created_at) VALUES ('XP', NOW()); \
        INSERT INTO strategy (id, name, asset_class, kind, created_at) VALUES \
          (9001,'Top Dividendos','ACOES','REPORT_DRIVEN',NOW()), \
          (9002,'Legado Ações','ACOES','MANUAL',NOW()); \
        INSERT INTO \`month\` (id, period, capital, \`floor\`, status, created_at) VALUES (9200, '2027-06', 1000, 0, 'OPEN', NOW()); \
        INSERT INTO target (month_id, \`level\`, asset_class, strategy, sub_class, ticker, percentage, created_at) VALUES \
          (9200,'CLASS','ACOES',NULL,NULL,NULL,100,NOW()), \
          (9200,'STRATEGY','ACOES','Top Dividendos',NULL,NULL,100,NOW()), \
          (9200,'TICKER','ACOES','Top Dividendos',NULL,'NOPOS3',100,NOW()); \
        REPLACE INTO position (ticker, quantity, average_price) VALUES \
          ('OVR9',10,8), ('UNCL1',5,8), ('UNCL2',3,8), ('NOPR1',4,8); \
        REPLACE INTO quote (ticker, date, close, source, created_at) VALUES \
          ('OVR9','2027-06-30',10,'COTAHIST',NOW()), \
          ('UNCL1','2027-06-30',10,'COTAHIST',NOW()), \
          ('UNCL2','2027-06-30',10,'COTAHIST',NOW()); \
        REPLACE INTO ticker_catalog (ticker, short_name, bdi_type, isin, asset_kind, asset_class, asset_class_override, first_seen, last_seen, created_at) VALUES \
          ('OVR9','OVERRIDE','??',NULL,'UNKNOWN',NULL,'ACOES','2027-01-01','2027-06-30',NOW()), \
          ('UNCL1','UNCL1','??',NULL,'UNKNOWN',NULL,NULL,'2027-01-01','2027-06-30',NOW()), \
          ('UNCL2','UNCL2','99',NULL,'UNKNOWN',NULL,NULL,'2027-01-01','2027-06-30',NOW()), \
          ('NOPR1','NOPR','02',NULL,'STOCK','ACOES',NULL,'2027-01-01','2027-06-30',NOW());"
  ```

  Run it independently of the other seeds, after the app has migrated and is healthy. The browser reads `/allocation` (Dados da carteira panel: Sem classe **2**, Sem cotação **1**; "Classificar pendentes" opens the bulk modal listing UNCL1 and UNCL2 with their reasons), the legacy banner (the preview lists OVR9 under Ações with Confirmar enabled), `/trades/OVR9` (the "classe manual: Ações" line with Reverter), and `/plan/orders` (the plan refusing on the unpriced NOPR1).

## Acceptance scenario — imported 2020 export (issue #309)

**Scenario**: [`acceptance/fixtures/b3-negociacao-2020.xlsx`](acceptance/fixtures/b3-negociacao-2020.xlsx) is the curated 11-row, 6-ticker B3 Negociação export (institution `XP INVESTIMENTOS`, venue `VISTA`, dates `2020-03-10` → `2020-12-01`) the acceptance run uploads into a stack booted from `docker compose down -v` with **no seed section applied**; in order it imports the file (`POST /imports` → 201 `STAGED`; apply → 6 positions with the four mid-history negatives HGLG11/KNCR11/VISC11/XPML11 at −10, every row `new` with no error), runs the one combined **Atualizar catálogo e cotações** refresh (all six held tickers priced at `asOf` and classified — ALUP11 Ações/UNIT, PETR4 Ações, the four FIIs — no R$ 0,00 Actual, and the panel at Sem cotação 0 / Sem classe 0 / Posições negativas 4), adopts the four negatives at `2020-03-09` with opening custody HGLG11 60 / KNCR11 40 / VISC11 25 / XPML11 20 (every position non-negative, and the badge and panel drop to zero), backfills with no file (`absorbed_seeds`/`adjusted_seeds`/`gaps_created`/`gaps_replaced` all 0, `positions_reprojected > 0`, `GET /gaps` empty), creates `Legado Ações` and `Legado FIIs`, proves the effective-class rule on HGLG11 (an override to Ações moves it into the Ações group, the revert puts it back) and runs the bulk legacy attribution before opening the month (two groups, six adds, `skipped: []`), reads `GET /month/period-range` (`min 2020-03`, `default 2020-12`) and opens the backdated `2020-12` month at Capital = 2 × the month-free Actual rounded up to the next R$ 1.000,00 with targets Ações 60 / FIIs 40 and one report Strategy Dividendos at ALUP11 50 / PETR4 50 (the header shows `histórico — sem fechamento`; the full plan answers 200 with BUY candidates, not `unpriced-positions` or `unclassified-positions`), gets `POST /month/current/close` refused with `400 month-close-not-current` (`GET /months` `[]`, no Snapshot), and re-uploads the same file (200, the same APPLIED id, the read-only Resultado with the import date and `Ver negócios`, every row `duplicate_in_ledger`). The fixture's net positions are ALUP11 150, PETR4 200 and the four FIIs at −10 before adoption. Any held ticker still unclassified or unpriced after the refresh, any R$ 0,00 Actual, any negative position after adoption, a non-empty GAP list, a plan gate after the month opens, or a Snapshot/closed month after the refused close is an automatic red; the evidence names the first failing step.

## Acceptance scenario — import resume states and movements re-upload (issue [#311](https://github.com/luiznaac/portfolio-2/issues/311))

**Scenario**: the run drives the four B3 wizard resume states and the Movimentações all-zero re-upload on the combined image, staging every file through the browser capability's `upload` action. [`b3-negociacao.xlsx`](../backend/http-api/src/test/resources/b3-negociacao.xlsx) uploads once as a fresh file (no banner), then re-uploads while it is still STAGED, again after review and again after apply, so STAGED, REVIEWED and APPLIED each resume on the step [#302](https://github.com/luiznaac/portfolio-2/issues/302) specifies. [`b3-negociacao-failed.xlsx`](../backend/http-api/src/test/resources/b3-negociacao-failed.xlsx) is seeded as a FAILED import because no staging path writes FAILED, and its upload resumes that record. [`b3-movimentacao.xlsx`](../backend/http-api/src/test/resources/b3-movimentacao.xlsx) uploads twice through the Eventos corporativos "Importar movimentações" input: the first applies its VALE3 buy, the second is the idempotent all-zero re-upload that reports `Arquivo já importado em <date> — nenhum lançamento novo.`

Apply this seed after the app is healthy and before the browser intents:

```bash
docker compose exec -T mysql mysql --default-character-set=utf8mb4 -uroot portfolio \
  -e "DELETE FROM import_row; DELETE FROM import; \
      INSERT INTO import (\`source\`, file_name, file_hash, status, created_at) VALUES \
        ('B3_TRADES', 'b3-negociacao-failed.xlsx', '7f3249811ced02ff21eeb4be457681f7a8e82499524fa34f4f6cc6f82d742451', 'FAILED', '2026-10-20 09:00:00');"
```

The seed resets the import tables so `b3-negociacao.xlsx` stages fresh, then stages the failed fixture as a FAILED import under its own SHA-256, so that fixture's upload resumes the record instead of creating a new import. Nothing else is seeded: the wizard's apply writes the negotiation rows, and the movements upload writes its own VALE3 buy. The run leaves the ledger with the fixtures' trades and the imported records; it resets nothing on teardown. Run it independently of the other seeds.

## Acceptance scenario — custody check and import negative deep-link (issue [#338](https://github.com/luiznaac/portfolio-2/issues/338))

**Scenario**: the run drives the two acceptance lines the [#307](https://github.com/luiznaac/portfolio-2/issues/307) run deferred, on a stack booted from `docker compose down -v` with **no seed section applied**, staging both files through the browser capability's `upload` action. On `/imports` the run uploads [`b3-negociacao-2020.xlsx`](acceptance/fixtures/b3-negociacao-2020.xlsx) (`Enviar arquivo` → `Continuar` → `Importar definitivamente`); the apply invalidates the import, trade, custody and allocation reads, so the Resultado immediately renders `11 entradas importadas · 6 posições na carteira · 4 negativas · 6 sem classe · 6 sem cotação`. The run then presses **Atualizar catálogo e cotações**, which classes and prices the six tickers and updates the line to `11 entradas importadas · 6 posições na carteira · 4 negativas · 0 sem classe · 0 sem cotação`, and its `negativas` link opens `/history`. There, `Conferir custódia` uploads [`posicao-custodia-2020.xlsx`](acceptance/fixtures/posicao-custodia-2020.xlsx) — the window's custody ALUP11 150, PETR4 200, HGLG11 60, KNCR11 40, VISC11 25 and XPML11 20 — and the read-only drift table renders six rows: ALUP11 and PETR4 `ok` (150/150 and 200/200), and HGLG11 60, KNCR11 40, VISC11 25 and XPML11 20 each `divergente` against the stored ledger's −10. The check persists nothing: `GET /positions` afterwards still returns the four negatives the import wrote. A run that cannot attach either file, a result line missing the negative count or the `/history` link, a drift table with a different row set, or a ledger that changes after the check is an automatic red; the evidence names the first failing step.

## Acceptance scenario — import result data quality (issue [#323](https://github.com/luiznaac/portfolio-2/issues/323))

**Scenario**: the run drives the import result's data-quality summary, its `/history` negative deep-link, the combined refresh report and the 0-imported re-upload on the combined image, all from the wizard's own file input through the browser capability's `upload` action. Boot from `docker compose down -v` and apply **no seed section**: the applied workbook itself is the book this scenario reads. [`b3-negociacao.xlsx`](../backend/http-api/src/test/resources/b3-negociacao.xlsx) is the two-row 2026-10-14 negotiation workbook (ALUP11 BUY 100 @ 48.92, PETR4 SELL 50 @ 35.10), so applying it leaves ALUP11 +100 and PETR4 −50 with both tickers auto-entered but still unclassified and unpriced — one negative, two unclassified and two unpriced holdings, enough to make every summary count distinguishable from an empty book.

In order: upload the workbook, submit it, review its two `nova(s)` rows and apply. The read-only **Resultado** must read `2 entradas importadas · 2 posições na carteira · 1 negativa · 2 sem classe · 2 sem cotação`, with the negative count rendered as the `/history` link and the combined **Atualizar catálogo e cotações** action with its report visible beside the counts. Run that refresh: its report reads a positive `Catálogo: <n>`, a positive `Cotações: <m>` and a `Fechamento: <date>` with no failure lines, and the summary becomes `2 entradas importadas · 2 posições na carteira · 1 negativa · 0 sem classe · 0 sem cotação` because the COTAHIST file classes and prices both tickers. Click the negative count: the browser lands on `/history` with the Histórico screen open. Return to `/imports`, upload the same workbook again: the read-only Resultado shows the `Este arquivo já foi importado em <date>.` resume banner and `0 entradas importadas`, with the same two positions and one negative.

The refresh walks back to the latest published COTAHIST daily file and reads the ledger's 2026 annual file, so its intent needs a generous timeout rather than a short one. A count that differs from the enumerated summary, a missing `/history` link, a refresh report carrying failure lines or a zero catalog/quote count, a re-upload without its resume banner or with a non-zero imported count, or an upload that does not reach the result is an automatic red; the evidence names the first failing intent.

## Acceptance scenario — Análise · Proventos (issue [#393](https://github.com/luiznaac/portfolio-2/issues/393))

**Scenario**: the run drives the windowed Proventos tab, its filter and its attention deep link on the combined image. Boot from `docker compose down -v` and apply this seed after the app is healthy; it leaves five income movements with explicit ids: three past payments (PETR4, VALE3, ITUB4), one receivable later in the current month (PETR4) and one receivable in the next month (VALE3). With no closed month the rail opens on 12 months ending the newest income month; a one-month window on that anchor leaves the earlier receivable outside the window and flagged "fora do recorte". The deep link `?focus=9001` anchors the rail on the received PETR4's month and highlights that ledger row. Run it independently of the other seeds.

```bash
docker compose exec -T mysql mysql --default-character-set=utf8mb4 -uroot portfolio \
  -e "DELETE FROM cash_movement; \
      INSERT INTO cash_movement (id, ticker, \`date\`, gross_amount, retained_amount, net_amount, tax_nature, provenance, status, income_type, created_at) VALUES \
        (9001, 'PETR4', DATE_SUB(CURDATE(), INTERVAL 40 DAY), 120, 0, 120, 'INCOME', 'B3', 'ANNOUNCED', 'DIVIDEND', NOW()), \
        (9002, 'VALE3', DATE_SUB(CURDATE(), INTERVAL 25 DAY), 80, 0, 80, 'INCOME', 'B3', 'ANNOUNCED', 'JCP', NOW()), \
        (9003, 'ITUB4', DATE_SUB(CURDATE(), INTERVAL 15 DAY), 60, 6, 54, 'INCOME', 'B3', 'ANNOUNCED', 'DIVIDEND', NOW()), \
        (9004, 'PETR4', DATE_ADD(CURDATE(), INTERVAL 5 DAY), 90, 0, 90, 'INCOME', 'B3', 'ANNOUNCED', 'DIVIDEND', NOW()), \
        (9005, 'VALE3', DATE_ADD(CURDATE(), INTERVAL 40 DAY), 70, 0, 70, 'INCOME', 'B3', 'ANNOUNCED', 'JCP', NOW());"
```

With a run in November 2026 the reads are deterministic: the window is `jan/2026 – dez/2026`, the KPIs read Recebidos R$ 254,00 / A receber R$ 160,00 / Próximo PETR4 / Média mensal R$ 21,17, the chart plots October's 254,00, and the ledger reads `5 de 5` with PETR4 and VALE3's receivables first and then the three October payments. Typing `petr` narrows the ledger to `2 de 5` while the KPIs and the rail stay put. Selecting the `Mês` preset moves the window to `dez/2026 – dez/2026`, leaves the November receivable in the ledger flagged "fora do recorte", and still reports the same KPIs. Opening `/portfolio/analysis/income?focus=9001` anchors the rail on `nov/2025 – out/2026` and shows the amber "do alerta" chip on the received PETR4 row. A missing KPI or chart, a ledger row order that puts a received payment before a receivable, a filter that changes the KPIs or the rail, a receivable that disappears when it falls outside the window, or a deep link that lands on another window without the highlight is an automatic red; the evidence names the first failing intent.

## Acceptance scenario — Análise · Impostos monthly read (issue [#394](https://github.com/luiznaac/portfolio-2/issues/394))

**Scenario**: the run drives the monthly Impostos tab over the shared rail. Boot from `docker compose down -v` and apply this seed after the app is healthy; it leaves three closed months (January–March 2027) and one open month (April 2027), so the rail opens on 12 months ending the last close and the open month stays reachable through the harvest panel. The ledger marks CSNA3 and KNRI11 as STOCK and FII, so the March carry banks a R$ 100 swing loss and a R$ 1,000 FII loss; April then sells R$ 20,600 of stocks (B3SA3 R$ 600 + VALE3 R$ 20,000) over the R$ 20,000 cliff, offsets R$ 600 of swing loss, and leaves a R$ 3,400 taxable gain / R$ 510,00 DARF. The same-day CSNA3 round trip is the day-trade pendência, and the HGLG11 position gives the harvest pass its FII report (R$ 1,000 gain / R$ 200 estimate); April's headroom is zero, so the pass proposes no pair. Run it independently of the other seeds.

```bash
docker compose exec -T mysql mysql --default-character-set=utf8mb4 -uroot portfolio \
  -e "DELETE FROM snapshot; DELETE FROM harvest_pair; DELETE FROM harvest_opt_out; \
      DELETE FROM transfer_proposal; DELETE FROM suggested_order; DELETE FROM target; DELETE FROM month; \
      DELETE FROM gap; DELETE FROM attribution; DELETE FROM trade; DELETE FROM position; DELETE FROM quote; \
      DELETE FROM edition_entry; DELETE FROM edition; DELETE FROM strategy; DELETE FROM ticker_catalog; \
      DELETE FROM tax_carry_bucket; DELETE FROM corporate_action; DELETE FROM institution; \
      INSERT INTO institution (name, created_at) VALUES ('XP', NOW()); \
      SET @inst := LAST_INSERT_ID(); \
      INSERT INTO ticker_catalog (ticker, short_name, bdi_type, isin, asset_kind, asset_class, first_seen, last_seen, created_at) VALUES \
        ('ITUB4','ITAUUNIBANCO','02',NULL,'STOCK','ACOES','2027-01-01','2027-04-30',NOW()), \
        ('B3SA3','B3','02',NULL,'STOCK','ACOES','2027-01-01','2027-04-30',NOW()), \
        ('VALE3','VALE','02',NULL,'STOCK','ACOES','2027-01-01','2027-04-30',NOW()), \
        ('CSNA3','CSN','02',NULL,'STOCK','ACOES','2027-01-01','2027-04-30',NOW()), \
        ('KNRI11','KINEA RENDA','12',NULL,'FII','FIIS','2027-01-01','2027-04-30',NOW()), \
        ('HGLG11','CSHG LOG','12',NULL,'FII','FIIS','2027-01-01','2027-04-30',NOW()); \
      INSERT INTO tax_carry_bucket (kind, opening_balance, as_of_period, created_at) VALUES \
        ('SWING', 500, '2027-02', NOW()), ('DAY_TRADE', 300, '2027-02', NOW()), ('FII', 250, '2027-02', NOW()); \
      INSERT INTO month (id, period, capital, \`floor\`, status, created_at) VALUES \
        (9401,'2027-01',0,0,'CLOSED',NOW()), (9402,'2027-02',0,0,'CLOSED',NOW()), \
        (9403,'2027-03',0,0,'CLOSED',NOW()), (9404,'2027-04',0,0,'OPEN',NOW()); \
      INSERT INTO trade (ticker, date, venue, quantity, unit_price, institution_id, fee, source, confirmed_by_b3, ordinal, created_at) VALUES \
        ('ITUB4','2027-01-05','B3', 100, 10, @inst, NULL, 'MANUAL', FALSE, 1, NOW()), \
        ('VALE3','2027-02-10','B3', 2000, 8, @inst, NULL, 'MANUAL', FALSE, 1, NOW()), \
        ('KNRI11','2027-02-10','B3', 100, 100, @inst, NULL, 'MANUAL', FALSE, 1, NOW()), \
        ('CSNA3','2027-03-01','B3', 100, 10, @inst, NULL, 'MANUAL', FALSE, 1, NOW()), \
        ('CSNA3','2027-03-15','B3',-100, 9, @inst, NULL, 'MANUAL', FALSE, 1, NOW()), \
        ('KNRI11','2027-03-15','B3',-100, 90, @inst, NULL, 'MANUAL', FALSE, 1, NOW()), \
        ('B3SA3','2027-04-01','B3', 60, 10, @inst, NULL, 'MANUAL', FALSE, 1, NOW()), \
        ('HGLG11','2027-04-02','B3', 100, 100, @inst, NULL, 'MANUAL', FALSE, 1, NOW()), \
        ('VALE3','2027-04-07','B3',-2000, 10, @inst, NULL, 'MANUAL', FALSE, 1, NOW()), \
        ('B3SA3','2027-04-08','B3',-60, 10, @inst, NULL, 'MANUAL', FALSE, 1, NOW()), \
        ('CSNA3','2027-04-09','B3', 1000, 10, @inst, NULL, 'B3_IMPORT', FALSE, 1, NOW()), \
        ('CSNA3','2027-04-09','B3',-1000, 10, @inst, NULL, 'B3_IMPORT', FALSE, 1, NOW()); \
      REPLACE INTO position (ticker, quantity, average_price) VALUES ('ITUB4', 100, 10), ('HGLG11', 100, 100); \
      REPLACE INTO quote (ticker, date, close, source, created_at) VALUES \
        ('ITUB4','2027-04-30',10,'COTAHIST',NOW()), ('HGLG11','2027-04-30',110,'COTAHIST',NOW());"
```

With a run whose calendar month is before 2027-04, `/analysis/tax` opens on the anchored `mar/2027` apuração: the rail reads `abr/2026 – mar/2027` and states `mês-âncora mar/2027 · apuração mensal; a janela filtra a lista`, the side panel lists three apurações (jan/2027, fev/2027, mar/2027) all chipped `fechado`, and the anchored panel reads `Apuração de mar/2027` with the R$ 900,00 stock meter, the swing loss, and the carry KPIs at R$ 600,00 (swing) and R$ 1.250,00 (FII). Selecting the `Mês` preset moves the window to `mar/2027 – mar/2027`, leaves the anchored apuração on mar/2027, and narrows the side panel to one row. **Ir para o mês aberto · abr/2027** anchors April: the panel reads `Apuração de abr/2027`, the meter is `Teto estourado` at R$ 20.600,00, the DARF KPI reads R$ 510,00, the Pendências panel carries the CSNA3 day-trade warning, and the harvest pass renders with zero headroom and the HGLG11 FII report. A rail without the monthly grain note, a side list that changes with the preset while the anchored apuração changes too, a meter/DARF/carry that disagrees with the seed, a missing pendência, or a harvest pass unreachable from the anchored historical month is an automatic red; the evidence names the first failing intent.

## Acceptance scenario — shared filter bar and page-scoped filter state (issue [#367](https://github.com/luiznaac/portfolio-2/issues/367))

> The Proventos half of this scenario predates the Análise · Proventos slice ([#393](https://github.com/luiznaac/portfolio-2/issues/393)): the forecast panel is now the window KPIs, the status control is labelled Estado, and a settled status narrows the ledger rather than emptying it. Use the Análise · Proventos scenario above for Proventos acceptance; the Ativos · Negócios half stands.

**Scenario**: the run drives the shared filter bar on the two unbounded lists that exist today — Ativos · Negócios (`/trades`) and Análise · Proventos (`/income`) — on the combined image. Boot from `docker compose down -v` and apply this seed after the app is healthy; it leaves three trades (PETR4 buy, VALE3 buy, PETR4 sell) and three income movements (PETR4 announced, VALE3 payable today, ITUB4 received), plus a R$ 200,00 forecast. Run it independently of the other seeds.

```bash
docker compose exec -T mysql mysql --default-character-set=utf8mb4 -uroot portfolio \
  -e "DELETE FROM cash_movement; DELETE FROM trade; DELETE FROM institution; \
      INSERT INTO institution (name, created_at) VALUES ('XP', NOW()); \
      SET @inst := LAST_INSERT_ID(); \
      INSERT INTO trade (ticker, date, venue, quantity, unit_price, institution_id, fee, source, confirmed_by_b3, ordinal, created_at) VALUES \
        ('PETR4', DATE_SUB(CURDATE(), INTERVAL 20 DAY), 'VISTA', 100, 30, @inst, NULL, 'B3_IMPORT', FALSE, 1, NOW()), \
        ('VALE3', DATE_SUB(CURDATE(), INTERVAL 10 DAY), 'VISTA', 50, 60, @inst, NULL, 'MANUAL', FALSE, 1, NOW()), \
        ('PETR4', DATE_SUB(CURDATE(), INTERVAL 2 DAY), 'VISTA', -40, 35, @inst, NULL, 'B3_IMPORT', FALSE, 1, NOW()); \
      INSERT INTO cash_movement (ticker, \`date\`, gross_amount, retained_amount, net_amount, tax_nature, provenance, status, income_type, created_at) VALUES \
        ('PETR4', DATE_ADD(CURDATE(), INTERVAL 5 DAY), 120, 0, 120, 'INCOME', 'B3', 'ANNOUNCED', 'DIVIDEND', NOW()), \
        ('VALE3', CURDATE(), 80, 0, 80, 'INCOME', 'B3', 'ANNOUNCED', 'JCP', NOW()), \
        ('ITUB4', DATE_SUB(CURDATE(), INTERVAL 15 DAY), 60, 6, 54, 'INCOME', 'B3', 'ANNOUNCED', 'DIVIDEND', NOW());"
```

On `/trades` the Negócios panel reads `3 de 3` with no `limpar`; typing `petr` in Ticker narrows it to `2 de 3`, selecting Lado `Venda` narrows to `1 de 3`, and a ticker that matches nothing (`zzz`) reaches `Nenhuma linha com esse filtro.`; its own `limpar` restores `3 de 3` and empty fields. Leaving the page and returning keeps `3 de 3`. On `/income` the Proventos ledger reads `3 de 3`; the forecast panel above it stays `R$ 200,00` / `2 pagamentos a receber` while the ledger narrows to `1 de 3` on `petr`; adding Situação `Recebido` reaches the narrowing empty state, whose `limpar` restores `3 de 3`; leaving and returning keeps `3 de 3` with empty fields. A filter that changes the count without narrowing the table, that survives leaving the page, that writes a query string into the URL, that changes the forecast/KPI read, or a narrowing empty state without `limpar` is an automatic red; the evidence names the first failing intent. The caret staying in the field while typing is pinned by `Trades.test.tsx`/`Income.test.tsx` (the browser schema has no focus assertion).

## Acceptance scenario — Ativos · Posições (issue [#378](https://github.com/luiznaac/portfolio-2/issues/378))

**Scenario**: the run drives the custody table, its shared filter bar, the filtered total, the ticker drawer and the classification surface on the combined image. Boot from `docker compose down -v` and apply this seed after the app is healthy; it leaves five positions, one stale close with an open gap, one ticker split across two attribution slices plus an outside remainder, and two held tickers with no class. Run it independently of the other seeds.

```bash
docker compose exec -T mysql mysql --default-character-set=utf8mb4 -uroot portfolio \
  -e "DELETE FROM gap; DELETE FROM transfer_proposal; DELETE FROM suggested_order; \
      DELETE FROM edition_entry; DELETE FROM edition; DELETE FROM attribution; DELETE FROM trade; \
      DELETE FROM target; DELETE FROM month; DELETE FROM position; DELETE FROM quote; \
      DELETE FROM ticker_catalog; DELETE FROM strategy; DELETE FROM institution; \
      INSERT INTO institution (id, name, created_at) VALUES (9001, 'XP', NOW()); \
      INSERT INTO strategy (id, name, asset_class, kind, created_at) VALUES \
        (9001,'Top Dividendos','ACOES','REPORT_DRIVEN',NOW()), \
        (9002,'Legado Ações','ACOES','MANUAL',NOW()); \
      REPLACE INTO position (ticker, quantity, average_price) VALUES \
        ('VALE3',100,8), ('ITUB4',50,8), ('HGLG11',10,8), ('ZZZZ9',10,8), ('UNCL1',5,8); \
      INSERT INTO attribution (ticker, strategy_id, quantity) VALUES \
        ('VALE3',9001,60), ('VALE3',9002,20), ('ITUB4',9002,30); \
      REPLACE INTO quote (ticker, date, close, source, created_at) VALUES \
        ('VALE3', DATE_SUB(CURDATE(), INTERVAL 40 DAY), 10, 'COTAHIST', NOW()), \
        ('ITUB4', CURDATE(), 10, 'COTAHIST', NOW()), \
        ('HGLG11', CURDATE(), 10, 'COTAHIST', NOW()), \
        ('ZZZZ9', CURDATE(), 10, 'COTAHIST', NOW()), \
        ('UNCL1', CURDATE(), 10, 'COTAHIST', NOW()); \
      REPLACE INTO ticker_catalog (ticker, short_name, bdi_type, isin, asset_kind, asset_class, first_seen, last_seen, created_at) VALUES \
        ('VALE3','VALE','02',NULL,'STOCK','ACOES',DATE_SUB(CURDATE(), INTERVAL 60 DAY),CURDATE(),NOW()), \
        ('ITUB4','ITAUUNIBANCO','02',NULL,'STOCK','ACOES',DATE_SUB(CURDATE(), INTERVAL 60 DAY),CURDATE(),NOW()), \
        ('HGLG11','CSHG LOG','12',NULL,'FII','FIIS',DATE_SUB(CURDATE(), INTERVAL 60 DAY),CURDATE(),NOW()), \
        ('ZZZZ9','ZZZZ','??',NULL,'UNKNOWN',NULL,DATE_SUB(CURDATE(), INTERVAL 60 DAY),CURDATE(),NOW()), \
        ('UNCL1','UNCL1','??',NULL,'UNKNOWN',NULL,DATE_SUB(CURDATE(), INTERVAL 60 DAY),CURDATE(),NOW()); \
      INSERT INTO trade (ticker, date, venue, quantity, unit_price, institution_id, fee, source, confirmed_by_b3, ordinal, created_at) VALUES \
        ('VALE3', DATE_SUB(CURDATE(), INTERVAL 60 DAY), 'VISTA', 100, 8, 9001, NULL, 'MANUAL', FALSE, 1, NOW()); \
      INSERT INTO gap (id, ticker, \`date\`, side, quantity, unit_price, status, trade_watermark, action_watermark, created_at) VALUES \
        (9401, 'VALE3', DATE_SUB(CURDATE(), INTERVAL 30 DAY), 'BUY', 2, 10, 'OPEN', 0, 0, NOW());"
```

On `/ativos/positions` the table reads `5 de 5` and total `R$ 1.750,00`: VALE3 (Ações, 100, R$ 8,00 average, R$ 10,00 close, R$ 1.000,00 value, `desatualizada · <date>` and `1 lacuna` chips), ITUB4 (Ações, R$ 500,00), HGLG11 (Fundos imob., R$ 100,00), ZZZZ9 and UNCL1 (Sem classe, R$ 100,00 and R$ 50,00). Typing `vale` in Ticker narrows to `1 de 5` with total `R$ 1.000,00`; `limpar` restores `5 de 5`. Selecting Classe `Sem classe` narrows to `2 de 5` with total `R$ 150,00` and shows the `Classificar sem classe` bulk action. Clicking a row opens the ticker drawer with the position, average price, attribution, recent trades, quotes, gap and classification; VALE3's drawer shows the split attribution (Top Dividendos 60, Legado 20, 20 unattributed), the one trade, the stale quote and the open gap. In UNCL1's drawer, Classificar → FIIs moves it to Fundos imob.; the Sem classe filter then reads `1 de 5`. The bulk action opens the queue with ZZZZ9; classifying it as FIIs empties the filtered table (`Nenhuma linha com esse filtro.`) and removes the bulk action. A table/total that disagrees with the seed, a missing stale or gap chip, a value that uses one attribution slice instead of the whole custody (VALE3 R$ 600,00 instead of R$ 1.000,00), a drawer missing a section, a classification that leaves the row/or action unchanged, a filter that changes the URL or a count without narrowing the table, or an empty state without `limpar` is an automatic red; the evidence names the first failing intent.
## Acceptance scenario — Estratégias · Todas comparison list (issue [#373](https://github.com/luiznaac/portfolio-2/issues/373))

**Scenario**: the run drives the new comparison table on the combined image. Boot from `docker compose down -v` and apply this seed after the app is healthy and before the Browser intents; it resets the plan and allocation tables and seeds four strategies (Top Dividendos and Small Caps report-driven, the Legado Ações bucket, and Fundamentalista FII with no edition), two editions — Top Dividendos' latest is **staged** for 2027-06, Small Caps' is **active** for 2027-05 — and an open 2027-06 month at Capital R$ 100.000,00 whose chain gives Ações 60 / Fundos imob. 40 and one ticker target per report strategy. Custody and quotes at R$ 10 make every priced value exact: Top Dividendos R$ 10.000,00 (drift −26,0 p.p.), Small Caps R$ 20.000,00 (−4,0 p.p.), Fundamentalista FII R$ 5.000,00 (−35,0 p.p.), Legado Ações R$ 1.000,00 with no drift (the bucket has no target). Run it independently of the other seeds.

```bash
docker compose exec -T mysql mysql --default-character-set=utf8mb4 -uroot portfolio \
  -e "DELETE FROM transfer_proposal; DELETE FROM suggested_order; \
      DELETE FROM edition_entry; DELETE FROM edition; DELETE FROM attribution; DELETE FROM trade; \
      DELETE FROM target; DELETE FROM month; DELETE FROM position; DELETE FROM quote; \
      DELETE FROM ticker_catalog; DELETE FROM strategy; DELETE FROM institution; \
      INSERT INTO institution (name, created_at) VALUES ('XP', NOW()); \
      INSERT INTO strategy (id, name, asset_class, kind, created_at) VALUES \
        (9001,'Top Dividendos','ACOES','REPORT_DRIVEN',NOW()), \
        (9002,'Small Caps','ACOES','REPORT_DRIVEN',NOW()), \
        (9003,'Legado Ações','ACOES','MANUAL',NOW()), \
        (9004,'Fundamentalista FII','FIIS','REPORT_DRIVEN',NOW()); \
      INSERT INTO \`month\` (id, period, capital, \`floor\`, status, created_at) VALUES (9300, '2027-06', 100000, 0, 'OPEN', NOW()); \
      INSERT INTO target (month_id, \`level\`, asset_class, strategy, sub_class, ticker, percentage, created_at) VALUES \
        (9300,'CLASS','ACOES',NULL,NULL,NULL,60,NOW()), \
        (9300,'CLASS','FIIS',NULL,NULL,NULL,40,NOW()), \
        (9300,'STRATEGY','ACOES','Top Dividendos',NULL,NULL,60,NOW()), \
        (9300,'STRATEGY','ACOES','Small Caps',NULL,NULL,40,NOW()), \
        (9300,'STRATEGY','FIIS','Fundamentalista FII',NULL,NULL,100,NOW()), \
        (9300,'TICKER','ACOES','Top Dividendos',NULL,'VALE3',100,NOW()), \
        (9300,'TICKER','ACOES','Small Caps',NULL,'PETR4',100,NOW()), \
        (9300,'TICKER','FIIS','Fundamentalista FII',NULL,'HGLG11',100,NOW()); \
      INSERT INTO edition (id, strategy_id, reference_month, status, created_at) VALUES \
        (9401, 9001, '2027-06', 'STAGED', NOW()), \
        (9402, 9002, '2027-05', 'ACTIVE', NOW()); \
      INSERT INTO edition_entry (edition_id, ticker, weight, rating, target_price, do_not_trade) VALUES \
        (9401,'VALE3',100,NULL,NULL,FALSE), (9402,'PETR4',100,NULL,NULL,FALSE); \
      REPLACE INTO position (ticker, quantity, average_price) VALUES \
        ('VALE3',1000,8), ('PETR4',2000,8), ('HGLG11',500,8), ('ITUB4',100,8); \
      INSERT INTO attribution (ticker, strategy_id, quantity) VALUES \
        ('VALE3',9001,1000), ('PETR4',9002,2000), ('HGLG11',9004,500), ('ITUB4',9003,100); \
      REPLACE INTO quote (ticker, date, close, source, created_at) VALUES \
        ('VALE3','2027-06-30',10,'COTAHIST',NOW()), \
        ('PETR4','2027-06-30',10,'COTAHIST',NOW()), \
        ('HGLG11','2027-06-30',10,'COTAHIST',NOW()), \
        ('ITUB4','2027-06-30',10,'COTAHIST',NOW()); \
      REPLACE INTO ticker_catalog (ticker, short_name, bdi_type, isin, asset_kind, asset_class, first_seen, last_seen, created_at) VALUES \
        ('VALE3','VALE','02',NULL,'STOCK','ACOES','2027-01-01','2027-06-30',NOW()), \
        ('PETR4','PETROBRAS','02',NULL,'STOCK','ACOES','2027-01-01','2027-06-30',NOW()), \
        ('ITUB4','ITAUUNIBANCO','02',NULL,'STOCK','ACOES','2027-01-01','2027-06-30',NOW()), \
        ('HGLG11','CSHG LOG','12',NULL,'FII','FIIS','2027-01-01','2027-06-30',NOW());"
```

On `/strategies` the single table lists four rows in class order with the bucket last: `Small Caps` (Ações · Com relatório · `mai/27 ✓` · R$ 20.000,00 · -4,0 p.p.), `Top Dividendos` (Ações · Com relatório · `jun/27 · revisão` · R$ 10.000,00 · -26,0 p.p.), `Legado Ações` (Ações · Manual (legado) · — · R$ 1.000,00 · —) and `Fundamentalista FII` (Fundos imob. · Com relatório · — · R$ 5.000,00 · -35,0 p.p.). The Fundamentalista FII row proves `kind`, not the presence of an edition, separates the two types: it reads `Com relatório` while its Edição cell is `—`. The Legado Ações row's Drift cell is `—`. Clicking the `Top Dividendos` link opens `/strategies/9001` (the detail shell landed in [#374](https://github.com/luiznaac/portfolio-2/issues/374)). `GET /strategies` answers 200 with the additive `latest_edition` (`{id, reference_month, status}`) on each row — Top Dividendos `2027-06` `STAGED`, Small Caps `2027-05` `ACTIVE` — and omits it for the two editionless strategies. A table missing a strategy, a manual row with a drift value, a wrong value or drift, a report strategy hidden because it has no edition, a dead detail link, or a `latest_edition` absent from the HTTP read is an automatic red; the evidence names the first failing intent. The list issuing no per-strategy editions request is pinned by `Strategies.test.tsx`, and the DTO-mirror guard by `dtoMirrorGuard.test.ts`.

## Acceptance scenario — Estratégias detail shell (issue #374)

**Scenario**: the run drives the detail shell the Estratégias list opens into — the KPI row, the Carteira tab with its staged-edition strip, the Fatia tab, and the unknown-id not-found state — on the combined image. Boot from `docker compose down -v` and apply this seed after the app is healthy and before the Browser intents; it resets the plan, close and allocation tables and seeds two closed months (April and May 2027) whose frozen strategy slices give Top Dividendos a return, plus an open June month whose chain prices the KPI and the Carteira table. Top Dividendos holds 1.000 VALE3 at R$ 10,00 (R$ 10.000,00) against a 60% class target inside an Ações 60% / FIIs 40% chain at R$ 100.000,00 capital; Legado Ações is the Ações bucket holding 100 ITUB4, and HGLG11 is outside custody in FIIs. A 2027-05 active edition and a 2027-06 staged edition drive the edition strip. Run it independently of the other seeds.

```bash
docker compose exec -T mysql mysql --default-character-set=utf8mb4 -uroot portfolio <<'SQL'
DELETE FROM snapshot; DELETE FROM target; DELETE FROM transfer_proposal; DELETE FROM suggested_order;
DELETE FROM month; DELETE FROM ticker_catalog; DELETE FROM quote; DELETE FROM cash_movement;
DELETE FROM gap; DELETE FROM trade; DELETE FROM position; DELETE FROM institution;
DELETE FROM attribution; DELETE FROM edition_entry; DELETE FROM edition; DELETE FROM strategy;
INSERT INTO institution (name, created_at) VALUES ('XP', NOW());
INSERT INTO strategy (id, name, asset_class, kind, created_at) VALUES
  (9001,'Top Dividendos','ACOES','REPORT_DRIVEN',NOW()),
  (9003,'Legado Ações','ACOES','MANUAL',NOW());
INSERT INTO `month` (id, period, capital, `floor`, aportes, status, created_at) VALUES
  (9300,'2027-06',100000,0,0,'OPEN',NOW()),
  (9401,'2027-04',90000,0,0,'CLOSED',NOW()),
  (9402,'2027-05',95000,0,0,'CLOSED',NOW());
INSERT INTO target (month_id, `level`, asset_class, strategy, sub_class, ticker, percentage, created_at) VALUES
  (9300,'CLASS','ACOES',NULL,NULL,NULL,60,NOW()),
  (9300,'CLASS','FIIS',NULL,NULL,NULL,40,NOW()),
  (9300,'STRATEGY','ACOES','Top Dividendos',NULL,NULL,60,NOW()),
  (9300,'TICKER','ACOES','Top Dividendos',NULL,'VALE3',100,NOW());
INSERT INTO edition (id, strategy_id, reference_month, status, created_at) VALUES
  (9501,9001,'2027-05','ACTIVE',NOW()),
  (9502,9001,'2027-06','STAGED',NOW());
INSERT INTO edition_entry (edition_id, ticker, weight, rating, target_price, do_not_trade) VALUES
  (9501,'VALE3',100,NULL,NULL,FALSE),
  (9502,'VALE3',100,NULL,NULL,FALSE);
REPLACE INTO position (ticker, quantity, average_price) VALUES
  ('VALE3',1000,8), ('ITUB4',100,8), ('HGLG11',500,8);
INSERT INTO attribution (ticker, strategy_id, quantity) VALUES
  ('VALE3',9001,1000), ('ITUB4',9003,100);
REPLACE INTO quote (ticker, date, close, source, created_at) VALUES
  ('VALE3','2027-06-30',10,'COTAHIST',NOW()),
  ('ITUB4','2027-06-30',10,'COTAHIST',NOW()),
  ('HGLG11','2027-06-30',10,'COTAHIST',NOW());
REPLACE INTO ticker_catalog (ticker, short_name, bdi_type, isin, asset_kind, asset_class, first_seen, last_seen, created_at) VALUES
  ('VALE3','VALE','02',NULL,'STOCK','ACOES','2027-01-01','2027-06-30',NOW()),
  ('ITUB4','ITAUUNIBANCO','02',NULL,'STOCK','ACOES','2027-01-01','2027-06-30',NOW()),
  ('HGLG11','CSHG LOG','12',NULL,'FII','FIIS','2027-01-01','2027-06-30',NOW());
INSERT INTO snapshot (month_id, state, created_at) VALUES
  (9401, '{"period":"2027-04","capital":90000,"floor":0,"aportes":0,"caixa":80000,"holdings":[{"ticker":"VALE3","quantity":900,"average_price":8},{"ticker":"ITUB4","quantity":100,"average_price":8}],"attributions":[{"ticker":"VALE3","strategy_id":9001,"strategy":"Top Dividendos","quantity":900},{"ticker":"ITUB4","strategy_id":9003,"strategy":"Legado Ações","quantity":100}],"classes":[{"asset_class":"ACOES","target":60,"ideal":54000,"actual":10000,"deficit":44000,"drift":-48.8889,"unattributed":0}],"strategies":[{"strategy_id":9001,"strategy":"Top Dividendos","kind":"REPORT_DRIVEN","value_end":9000,"flow":0},{"strategy_id":9003,"strategy":"Legado Ações","kind":"MANUAL","value_end":1000,"flow":0}],"income":{"gross":0,"retained":0,"net":0},"orders":{"orders":[],"transfers":[],"unplanned_trades":[]},"tax":{"carry":[],"meter":{"gross_sales":0,"exemption_limit":20000,"exceeded":false,"realized_gain":0,"taxable_gain":0,"rate":15,"tax":0}}}', NOW()),
  (9402, '{"period":"2027-05","capital":95000,"floor":0,"aportes":0,"caixa":84000,"holdings":[{"ticker":"VALE3","quantity":1000,"average_price":8},{"ticker":"ITUB4","quantity":100,"average_price":8}],"attributions":[{"ticker":"VALE3","strategy_id":9001,"strategy":"Top Dividendos","quantity":1000},{"ticker":"ITUB4","strategy_id":9003,"strategy":"Legado Ações","quantity":100}],"classes":[{"asset_class":"ACOES","target":60,"ideal":57000,"actual":11000,"deficit":46000,"drift":-48.4211,"unattributed":0}],"strategies":[{"strategy_id":9001,"strategy":"Top Dividendos","kind":"REPORT_DRIVEN","value_end":10000,"flow":0},{"strategy_id":9003,"strategy":"Legado Ações","kind":"MANUAL","value_end":1000,"flow":0}],"income":{"gross":0,"retained":0,"net":0},"orders":{"orders":[],"transfers":[],"unplanned_trades":[]},"tax":{"carry":[],"meter":{"gross_sales":0,"exemption_limit":20000,"exceeded":false,"realized_gain":0,"taxable_gain":0,"rate":15,"tax":0}}}', NOW());
SQL
```

The reads are then deterministic: `GET /strategies` opens the list; `GET /strategies/9001/editions` carries the 2027-06 `STAGED` edition; `GET /months` lists 2027-05 first; `GET /months/9402/performance` carries Top Dividendos' decomposition (`value_start` 9.000, `value_end` 10.000, `return_percentage` 11,1111). On `/strategies` clicking **Top Dividendos** opens `/strategies/9001`: the page head reads `Top Dividendos`, the KPI row reads Fatia R$ 10.000,00 (62,5% da carteira), Alvo 60,0% da classe Ações, Ideal R$ 36.000,00 (faltam R$ 26.000,00), Drift -26,0 p.p. and Retorno mai/27 +11,11%; the tab strip reads `Carteira` and `Fatia no portfólio`. Carteira lists VALE3 with 1.000 units, R$ 10,00, R$ 10.000,00, 100,0% target, R$ 36.000,00 Ideal and -26,0 p.p. drift, and the `Edição em revisão · jun/27` strip sits above the table. Switching to Fatia reads Fatia na carteira 62,5%, Fatia na classe 90,9%, Alvo na classe 60,0%, Drift -26,0 p.p. and the return decomposition Início R$ 9.000,00 / Fim R$ 10.000,00 / Aportes R$ 0,00 / Proventos R$ 0,00 / Retorno +11,11%, with no edition strip. `/strategies/999999` renders `Estratégia não encontrada` instead of crashing. A missing KPI, a Carteira row without its target weight or units, a missing stage strip, a Fatia without the class and portfolio shares or the decomposition, a repeated edition strip on Fatia, a detail that crashes on an unknown id, or a value that disagrees with the seed is an automatic red; the evidence names the first failing intent.

## Acceptance scenario — import assistant core (issue [#382](https://github.com/luiznaac/portfolio-2/issues/382))

**Scenario**: the run drives the full-screen four-step import assistant on the combined image. Boot from `docker compose down -v` and apply **no seed section**: the workbook the scenario uploads is the whole book. [`b3-negociacao.xlsx`](../backend/http-api/src/test/resources/b3-negociacao.xlsx) — the two-row 2026-10-14 B3 Negociação workbook (ALUP11 BUY 100 @ 48.92, PETR4 SELL 50 @ 35.10) — is staged through the assistant's own file input via the browser capability's `upload` action. In order: `/imports` reads `Caixa de entrada` with the upload entry and the parked-session area; `Enviar arquivo →` opens the assistant on step 1 `Arquivo` with Ativos · Negócios and the B3 negociação parser pre-filled; choosing the workbook enables `Enviar e revisar`, which stages the file and opens step 2 `Destino` without applying anything; `Continuar →` reaches step 3 `Linhas`; `Sair` parks the staged session and the inbox shows the `Sessão em revisão` strip with the workbook name and a `Retomar →` link; Ativos · Negócios shows the same strip above the trade ledger and its `Retomar →` reopens the assistant on `Linhas`; `Continuar →` reaches step 4, whose "O que esta importação muda" card names Ativos · Negócios and Ativos · Posições above the apply action; `Aplicar 2 linha(s) válida(s)` writes both rows and lands on `/ativos/trades` with the applied-import banner and provenance.

Both clauses stay terminal and single-per-intent. The step 3 clause (`material-rows`) names the copy the screen renders: the header reads `2 aceitas` / `0 com atenção`, and each of the two rows carries the `Novo` chip (the `new` situation) — never "accepted"/`aceito`, and no rejected row. The Ativos · Negócios strip check (`strip-on-object`) is two intents, not one: a navigation intent whose after gate asserts `Sessão em revisão` and declares no visual, then a **static** intent anchored on the strip that carries the single terminal vision clause. A static intent asserts before its after-capture, so the trade ledger's `Carregando…` placeholder can never be the screenshot the vision verifier reads.

A clause whose copy disagrees with the screen (a row asserted as "accepted"/`aceito`), a strip screenshot taken while `Carregando…` is still up, more than one vision clause on an intent, or an apply that does not land on `/ativos/trades` is an automatic red; the evidence names the first failing intent.

## Acceptance scenario — Importações · Fontes failed-beside-accepted (issue [#417](https://github.com/luiznaac/portfolio-2/issues/417))

**Scenario**: the run drives the Fontes card's failed-file-beside-accepted state — the one state no staging path can produce, because ADR 0003 makes an unreadable file a 4xx that stages nothing. Boot from `docker compose down -v` and apply this seed after the app is healthy and before the Browser intents; it resets the import tables and seeds one `B3_TRADES` source with an older `APPLIED` record (id 9501, `b3-negociacao.xlsx`) and a newer `FAILED` record (id 9502, `b3-negociacao-failed.xlsx`) under the committed fixtures' own SHA-256s, so the failed attempt is the latest and the applied one is the last success. Run it independently of the other seeds.

```bash
docker compose exec -T mysql mysql --default-character-set=utf8mb4 -uroot portfolio \
  -e "DELETE FROM import_file; DELETE FROM import_row; DELETE FROM import; \
      INSERT INTO import (id, \`source\`, file_name, file_hash, status, created_at) VALUES \
        (9501, 'B3_TRADES', 'b3-negociacao.xlsx', '89a0cdb524e8fe4258e2d570b94a01393df808f010913b8d3f55f73fae62d03e', 'APPLIED', '2026-10-14 10:00:00'), \
        (9502, 'B3_TRADES', 'b3-negociacao-failed.xlsx', '7f3249811ced02ff21eeb4be457681f7a8e82499524fa34f4f6cc6f82d742451', 'FAILED', '2026-10-20 09:00:00');"
```

The read is then deterministic: `GET /imports/sources` → 200 with one `B3_TRADES` entry, `last_attempt` = `b3-negociacao-failed.xlsx` / `FAILED` / `2026-10-20T09:00`, `last_success` = `b3-negociacao.xlsx` / `APPLIED` / `2026-10-14T10:00`, `pending: 0` and `failure` = the failed attempt. On `/imports/sources` the `B3 · Negócios` card carries the `Falhou` chip, the failed file on **Última tentativa**, the accepted file on **Última aplicação**, `Nenhuma sessão em revisão`, and the failure block with `Falhou` beside `Aplicada` — never `Sem falhas na última tentativa.`. Each file name is its own link: the failed file opens `/imports/assistant/9502` on the read-only `Falhou` panel (`Esta tentativa falhou e não pode ser aplicada.`, no apply affordance), the accepted file opens `/imports/assistant/9501` on the `Aplicada` outcome. A card missing either file or the failure block, a healthy-state line, a link that lands on the other record, or a failed record that offers an apply is an automatic red; the evidence names the first failing intent.

## Acceptance scenario — Carteira · Alocação (issue [#371](https://github.com/luiznaac/portfolio-2/issues/371))

**Scenario**: the run drives the full allocation chain on the combined image. Boot from `docker compose down -v` and apply this seed after the app is healthy and before the Browser intents. It resets the allocation tables and seeds an open 2027-05 month (id 9300, Capital R$ 100.000,00) with Ações 40 / Renda fixa 60, one report Strategy (Top Dividendos, id 9001) at 100% of Ações targeting ITUB4 60 / VALE3 40, one manual bucket (Legado Ações, id 9002), a 100% Pós-fixado RF sub-class target, and four R$ 10,00 closes. ITUB4 (100 units) is split 60/40 between the report Strategy and the bucket, VALE3 (40 units) is fully attributed, PETR4 (10 units) is the outside remainder, and ZZZZ9 (5 units) has a quote but no catalog row. Run it independently of the other seeds.

```bash
docker compose exec -T mysql mysql --default-character-set=utf8mb4 -uroot portfolio \
  -e "DELETE FROM gap; DELETE FROM transfer_proposal; DELETE FROM suggested_order; \
      DELETE FROM edition_entry; DELETE FROM edition; DELETE FROM attribution; DELETE FROM trade; \
      DELETE FROM target; DELETE FROM month; DELETE FROM position; DELETE FROM quote; \
      DELETE FROM ticker_catalog; DELETE FROM strategy; DELETE FROM institution; \
      INSERT INTO strategy (id, name, asset_class, kind, created_at) VALUES \
        (9001,'Top Dividendos','ACOES','REPORT_DRIVEN',NOW()), \
        (9002,'Legado Ações','ACOES','MANUAL',NOW()); \
      INSERT INTO \`month\` (id, period, capital, \`floor\`, status, created_at) VALUES (9300, '2027-05', 100000, 0, 'OPEN', NOW()); \
      INSERT INTO target (month_id, \`level\`, asset_class, strategy, sub_class, ticker, percentage, created_at) VALUES \
        (9300,'CLASS','ACOES',NULL,NULL,NULL,40,NOW()), \
        (9300,'CLASS','RENDA_FIXA',NULL,NULL,NULL,60,NOW()), \
        (9300,'STRATEGY','ACOES','Top Dividendos',NULL,NULL,100,NOW()), \
        (9300,'SUB_CLASS','RENDA_FIXA',NULL,'FLOATING_RATE',NULL,100,NOW()), \
        (9300,'TICKER','ACOES','Top Dividendos',NULL,'ITUB4',60,NOW()), \
        (9300,'TICKER','ACOES','Top Dividendos',NULL,'VALE3',40,NOW()); \
      REPLACE INTO position (ticker, quantity, average_price) VALUES \
        ('ITUB4',100,8), ('VALE3',40,8), ('PETR4',10,8), ('ZZZZ9',5,8); \
      INSERT INTO attribution (ticker, strategy_id, quantity) VALUES \
        ('ITUB4',9001,60), ('ITUB4',9002,40), ('VALE3',9001,40); \
      REPLACE INTO quote (ticker, date, close, source, created_at) VALUES \
        ('ITUB4','2027-05-31',10,'COTAHIST',NOW()), \
        ('VALE3','2027-05-31',10,'COTAHIST',NOW()), \
        ('PETR4','2027-05-31',10,'COTAHIST',NOW()), \
        ('ZZZZ9','2027-05-31',10,'COTAHIST',NOW()); \
      REPLACE INTO ticker_catalog (ticker, short_name, bdi_type, isin, asset_kind, asset_class, first_seen, last_seen, created_at) VALUES \
        ('ITUB4','ITAUUNIBANCO','02',NULL,'STOCK','ACOES','2027-01-01','2027-05-31',NOW()), \
        ('VALE3','VALE','02',NULL,'STOCK','ACOES','2027-01-01','2027-05-31',NOW()), \
        ('PETR4','PETROBRAS','02',NULL,'STOCK','ACOES','2027-01-01','2027-05-31',NOW());"
```

On `/portfolio/allocation` the tree reads: Capital Actual **R$ 1.550,00** against the R$ 100.000,00 Ideal (worded **faltam R$ 98.450,00**); Ações target 40%, Ideal R$ 40.000,00, Actual R$ 1.500,00, distance **faltam R$ 38.500,00**, split into Top Dividendos R$ 1.000,00 (**faltam R$ 39.000,00**), the Legado Ações bucket R$ 400,00 and Fora da estratégia R$ 100,00 (1.000 + 400 + 100 = 1.500); Fundos imob. with no target and Actual R$ 0,00 (**no alvo**); Renda fixa target 60%, Ideal R$ 60.000,00, Actual R$ 0,00 (**faltam R$ 60.000,00**), whose Pós-fixado sub-class holds the whole R$ 60.000,00 Ideal and the same worded shortfall. The Legado Ações bucket shows **—** in Alvo, Ideal, Déficit / distância and Δ p.p. and no bar in the Atual × Ideal column. Sem classe follows the class rows at R$ 50,00. Top Dividendos and Legado Ações link to `/strategies/9001` and `/strategies/9002`. The legacy banner reads R$ 100,00 outside; its preview lists PETR4 under Ações and skips ZZZZ9, and Confirmar reports **1 posição atribuída ao legado.** (the bucket then holds R$ 500,00 and the banner disappears). Expanding Sem classe exposes ZZZZ9's **Classificar** link to `/ativos/positions/ZZZZ9`. A tree missing the RF sub-class rung, a bucket target/bar, a signed Δ R$ instead of the worded distance, a class whose rows do not sum to its Actual, a dead strategy link, a bulk action that does not preview or apply, or an unreachable classification link is an automatic red; the evidence names the first failing intent.

## Acceptance scenario — Carteira · Histórico (issue [#372](https://github.com/luiznaac/portfolio-2/issues/372))

**Scenario**: the run drives the closed-month records list and the frozen detail on the combined image. Boot from `docker compose down -v` and apply this seed after the app is healthy and before the Browser intents; it resets the close and allocation tables and seeds two closed months (April and May 2027) with their immutable snapshots, while the live book quotes VALE3 at R$ 12,00 instead of the snapshot's R$ 10,00. May's detail must therefore render the frozen R$ 11.000,00 Actual, never the live allocation's R$ 13.000,00. May's snapshot carries one partially executed order (ITUB4, PARCIAL), one executed order (B3SA3), one unplanned trade (VALE3), the two frozen strategy slices (Top Dividendos and the Legado Ações bucket), R$ 85,00 of income and a R$ 250,00 DARF. Run it independently of the other seeds.

```bash
docker compose exec -T mysql mysql --default-character-set=utf8mb4 -uroot portfolio <<'SQL'
DELETE FROM snapshot; DELETE FROM target; DELETE FROM transfer_proposal; DELETE FROM suggested_order;
DELETE FROM month; DELETE FROM ticker_catalog; DELETE FROM quote; DELETE FROM cash_movement;
DELETE FROM gap; DELETE FROM trade; DELETE FROM position; DELETE FROM institution;
DELETE FROM attribution; DELETE FROM edition_entry; DELETE FROM edition; DELETE FROM strategy;
INSERT INTO strategy (id, name, asset_class, kind, created_at) VALUES
  (9001,'Top Dividendos','ACOES','REPORT_DRIVEN',NOW()),
  (9002,'Legado Ações','ACOES','MANUAL',NOW());
INSERT INTO `month` (id, period, capital, `floor`, aportes, status, created_at) VALUES
  (9401,'2027-04',90000,20000,0,'CLOSED',NOW()),
  (9402,'2027-05',95000,20000,0,'CLOSED',NOW());
REPLACE INTO position (ticker, quantity, average_price) VALUES
  ('VALE3',1000,8), ('ITUB4',100,8);
INSERT INTO attribution (ticker, strategy_id, quantity) VALUES
  ('VALE3',9001,1000), ('ITUB4',9002,100);
REPLACE INTO quote (ticker, date, close, source, created_at) VALUES
  ('VALE3','2027-06-30',12,'COTAHIST',NOW()),
  ('ITUB4','2027-06-30',10,'COTAHIST',NOW());
REPLACE INTO ticker_catalog (ticker, short_name, bdi_type, isin, asset_kind, asset_class, first_seen, last_seen, created_at) VALUES
  ('VALE3','VALE','02',NULL,'STOCK','ACOES','2027-01-01','2027-06-30',NOW()),
  ('ITUB4','ITAUUNIBANCO','02',NULL,'STOCK','ACOES','2027-01-01','2027-06-30',NOW());
INSERT INTO snapshot (month_id, state, created_at) VALUES
  (9401, '{"period":"2027-04","capital":90000,"floor":20000,"aportes":0,"caixa":80000,"holdings":[],"attributions":[],"classes":[],"strategies":[],"income":{"gross":0,"retained":0,"net":0},"orders":{"orders":[],"transfers":[],"unplanned_trades":[]},"tax":{"carry":[],"meter":{"gross_sales":0,"exemption_limit":20000,"exceeded":false,"realized_gain":0,"taxable_gain":0,"rate":15,"tax":0}}}', NOW()),
  (9402, '{"period":"2027-05","capital":95000,"floor":20000,"aportes":0,"caixa":84000,"holdings":[{"ticker":"ITUB4","quantity":100,"average_price":8},{"ticker":"VALE3","quantity":1000,"average_price":8}],"attributions":[{"ticker":"VALE3","strategy_id":9001,"strategy":"Top Dividendos","quantity":1000},{"ticker":"ITUB4","strategy_id":9002,"strategy":"Legado Ações","quantity":100}],"classes":[{"asset_class":"ACOES","target":60,"ideal":57000,"actual":11000,"deficit":46000,"drift":-48.4211,"unattributed":0}],"strategies":[{"strategy_id":9001,"strategy":"Top Dividendos","kind":"REPORT_DRIVEN","value_end":10000,"flow":200},{"strategy_id":9002,"strategy":"Legado Ações","kind":"MANUAL","value_end":1000,"flow":-100}],"income":{"gross":100,"retained":15,"net":85},"orders":{"orders":[{"id":9601,"ticker":"ITUB4","side":"BUY","quantity":10,"suggested_price":30,"executed_quantity":5,"status":"SUGGESTED","matched_trade_id":null},{"id":9602,"ticker":"B3SA3","side":"SELL","quantity":10,"suggested_price":null,"executed_quantity":10,"status":"EXECUTED","matched_trade_id":null}],"transfers":[],"unplanned_trades":[{"id":9603,"ticker":"VALE3","side":"SELL","quantity":3}]},"tax":{"carry":[],"meter":{"gross_sales":0,"exemption_limit":20000,"exceeded":false,"realized_gain":0,"taxable_gain":0,"rate":15,"tax":250}}}', NOW());
SQL
```

The reads are then deterministic: `GET /months` lists 2027-05 (id 9402) first; `GET /months/9402` carries the snapshot above. On `/portfolio/history` the page head reads `Carteira`, the Histórico tab is active, and the table has exactly two rows — `mai/2027` with Capital R$ 95.000,00 and `abr/2027` with R$ 90.000,00. Clicking `mai/2027` opens `/portfolio/history/2027-05`: the head reads `Histórico · mai/2027` and the KPI row reads Capital R$ 95.000,00 (piso R$ 20.000,00), Aportes R$ 0,00, Caixa R$ 84.000,00, Proventos R$ 85,00 and DARF R$ 250,00. Classes congeladas reads Ações, Alvo 60,0%, Ideal R$ 57.000,00, Atual R$ 11.000,00, Déficit +R$ 46.000,00 and Drift −48,4 p.p. — the live allocation's R$ 13.000,00 never appears. Ordens e negócios do mês reads ITUB4 · Compra · 10 · R$ 30,00 · PARCIAL, B3SA3 · Venda · 10 · — · Executado and VALE3 · Venda · 3 · — · Não planejada. Estratégias no fechamento reads Top Dividendos · Com relatório · R$ 10.000,00 · R$ 200,00 and Legado Ações · Manual (legado) · R$ 1.000,00 · -R$ 100,00, and the `O que este snapshot congela` caption states that nothing is recomputed. The surface offers no button. A list with a row per live month instead of per closed month, a detail that renders R$ 13.000,00 or any other live value, a missing PARCIAL/unplanned row, a missing strategy slice or caption, or any write action is an automatic red; the evidence names the first failing intent.

## Acceptance scenario — Ativos · Negócios ledger and Reconstruir histórico (issue #379)

**Scenario**: the run drives the paged trade ledger, its server-side filters and the reconstruction section on the combined image. Boot from `docker compose down -v` and apply this seed after the app is healthy and before the HTTP/Browser intents. It resets the ledger tables and seeds one institution, four named executions and 51 generated PETR4 buys so the first page ends with a next cursor. Run it independently of the other seeds.

```bash
docker compose exec -T mysql mysql -uroot portfolio <<'SQL'
DELETE FROM trade;
DELETE FROM institution;
INSERT INTO institution (name, created_at) VALUES ('XP', NOW());
SET @inst := LAST_INSERT_ID();
INSERT INTO trade (ticker, date, venue, quantity, unit_price, institution_id, fee, source, confirmed_by_b3, ordinal, created_at) VALUES
  ('PETR4', '2026-10-01', 'VISTA',  100, 10, @inst, NULL, 'MANUAL',    FALSE, 1, NOW()),
  ('VALE3', '2026-10-02', 'VISTA', -100, 20, @inst, NULL, 'B3_IMPORT', FALSE, 1, NOW()),
  ('PETR4', '2026-10-03', 'VISTA',  -50, 12, @inst, NULL, 'B3_IMPORT', FALSE, 1, NOW()),
  ('ITUB4', '2026-10-04', 'VISTA',   30, 30, @inst, NULL, 'OPENING',   FALSE, 1, NOW());
INSERT INTO trade (ticker, date, venue, quantity, unit_price, institution_id, fee, source, confirmed_by_b3, ordinal, created_at)
SELECT 'PETR4', DATE_ADD('2026-08-01', INTERVAL seq.day DAY), 'VISTA', seq.day, 1, @inst, NULL, 'MANUAL', FALSE, 1, NOW()
FROM (SELECT ROW_NUMBER() OVER () AS day FROM information_schema.columns LIMIT 51) seq;
SQL
```

The ledger holds 55 executions: the first page reads `50 de 55` with a next cursor, and the total row covers all 55 (R$ 5.826,00 — the four named rows total R$ 4.500,00 and the generated quantities sum to R$ 1.326,00). `ticker=VALE3` narrows to one row (R$ 2.000,00); `side=SELL` and the window `2026-10-02..2026-10-03` each narrow to the two named rows (R$ 2.600,00). Clearing a filter returns to the first page (`50 de 55`), never the pages loaded before the filter. Ativos · Negócios also hosts **Reconstruir histórico**: skipping adoption unlocks the replay; the backfill renders its report; the custody cross-check accepts `posicao-custodia-2020.xlsx` and renders the drift table.
