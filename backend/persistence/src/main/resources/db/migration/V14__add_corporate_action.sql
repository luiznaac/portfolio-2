CREATE TABLE IF NOT EXISTS corporate_action (id INT AUTO_INCREMENT PRIMARY KEY, ticker VARCHAR(12) NOT NULL, kind VARCHAR(30) NOT NULL, ex_date DATE NOT NULL, approval_date DATE NULL, ratio DECIMAL(18, 8) NULL, declared_value_per_share DECIMAL(18, 8) NULL, target_ticker VARCHAR(12) NULL, `source` VARCHAR(20) NOT NULL, process_number VARCHAR(40) NULL, raw_type VARCHAR(80) NULL, ordinal INT DEFAULT 1 NOT NULL, file_hash VARCHAR(64) NULL, status VARCHAR(20) NOT NULL, created_at DATETIME(6) NOT NULL);
ALTER TABLE corporate_action ADD CONSTRAINT corporate_action_ticker_kind_ex_date_ordinal_unique UNIQUE (ticker, kind, ex_date, ordinal);
ALTER TABLE cash_movement ADD corporate_action_id INT NULL;
ALTER TABLE cash_movement MODIFY COLUMN income_type VARCHAR(20) NULL;
