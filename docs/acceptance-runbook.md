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

- **Performance + Fechamento seed**: the performance reads replay the immutable snapshots, not the live ledger, so this scenario seeds two closed months (January and February 2026) with their snapshot JSON, the target chain and one listed holding — plus an open March month (9003) with one pending Attribution Transfer, which is what the Fechamento screen's blocked close reads. January opens at R$100k; February closes at R$103.5k after a R$2k aporte and no income, so the decomposition is `(103500 − 100000 − 2000) ÷ 100000 = 1.5%`. Ações is 0% and Renda Fixa 100% (Pós-fixado), so the blended benchmark is the live CDI return: IBOV series 7 is currently absent from SGS, and the blend keeps the policy denominator rather than silently renormalising around the missing leg (a targeted class with no benchmark would make the total unknown). The February snapshot carries a R$45k caixa and two executed orders so the closed-month summary has data. Run it independently of the order/tax/harvest seeds.

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
  curl -s http://localhost:8080/months/9002                   # the closed month's frozen snapshot
  curl -s http://localhost:8080/months/9002/performance   # decomposition 1.5%, xirr, benchmark
  curl -s http://localhost:8080/performance/series        # the two monthly points
  curl -s http://localhost:8080/gaps                      # the seeded missing sale, realized_pending true
  curl -s -X POST http://localhost:8080/gaps/9003/price -H 'Content-Type: application/json' -d '{"unit_price":18}'
  curl -s -X POST http://localhost:8080/history/backfill  # absorb the OPENING seed: absorbed_seeds 1
  ```

- **Cockpit seed**: run the Performance + Fechamento seed first — its open month (9003) and pending transfer are the Cockpit's close reminder, blocked close and transfer attention. This seed moves 9003's period to the previous calendar month (so the close reminder is due), then adds the agenda/proventos/edition rows its panels read. Dates are relative to `CURDATE()` so the maturity stays inside the seven-day lead and the payment inside the thirty-day window whenever acceptance runs:

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

  Browser intents then read the shell against real data: `/` shows the four KPIs, the seven-step stepper (step 3 pending transfers, step 7 blocked), the unattributed-cotas attention card (2.000 cotas held, 500 attributed), the class strip with a 19,3% Ações bar and the 60/80/100 target markers, `Vencimento de CDB Aceite` and a `Fechamento de … pendente` agenda item, and `PETR4` among the próximos proventos; Ctrl+K opens the command palette; the agenda's `Marcar como lida` removes the item, and after a close the same item turns into a `concluído` notice that also waits for the mark-as-read before it leaves the list.
