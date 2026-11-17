-- The database a completed brapi run leaves behind, standing in for the paid provider during
-- acceptance. Idempotent: the runbook's ready probe re-applies it on every poll until the app is
-- healthy, so each statement starts from a clean set. One trade gives the sync a ticker, so the
-- credential-less POST reaches the provider gate and fails loudly instead of folding over an empty
-- ticker set.
DELETE FROM brapi_sync;
DELETE FROM cash_movement;
DELETE FROM corporate_action;
DELETE FROM trade WHERE institution_id = 9001;
DELETE FROM institution WHERE id = 9001;

INSERT INTO brapi_sync (id, started_at, as_of, window_from, window_to, ticker_count, fetched_count, applied_count, parked_count, income_count, failed_tickers, raw_payload) VALUES
  (9001, NOW(6), '2026-11-17', '2018-03-05', '2026-11-17', 2, 4, 1, 1, 2, '', '[]');

INSERT INTO corporate_action (id, ticker, kind, ex_date, approval_date, ratio, declared_value_per_share, target_ticker, source, process_number, raw_type, ordinal, file_hash, status, created_at) VALUES
  (9001, 'PETR4',  'SPLIT',        '2020-01-10', NULL, 2, NULL, NULL, 'BRAPI', NULL, 'DESDOBRAMENTO', 1, NULL, 'APPLIED',      NOW()),
  (9002, 'MXRF11', 'AMORTIZATION', NULL,         NULL, NULL, 0.20, NULL, 'BRAPI', NULL, 'AMORTIZAÇÃO',   1, NULL, 'NEEDS_REVIEW', NOW());

INSERT INTO cash_movement (id, ticker, `date`, gross_amount, retained_amount, net_amount, tax_nature, provenance, status, income_type, corporate_action_id, estimated, created_at) VALUES
  (9001, 'PETR4',  '2026-12-15', 50.00, 0, 50.00, 'INCOME', 'BRAPI', 'ANNOUNCED', 'DIVIDEND',    NULL, TRUE,  NOW()),
  (9002, 'MXRF11', '2026-12-20', 10.00, 0, 10.00, 'INCOME', 'BRAPI', 'ANNOUNCED', 'FUND_INCOME', NULL, FALSE, NOW());

INSERT INTO institution (id, name, created_at) VALUES (9001, 'BRAPI ACCEPTANCE', NOW());
INSERT INTO trade (ticker, date, venue, quantity, unit_price, institution_id, fee, source, confirmed_by_b3, ordinal, created_at) VALUES
  ('PETR4', '2018-03-05', 'VISTA', 100, 20, 9001, NULL, 'MANUAL', FALSE, 1, NOW());
