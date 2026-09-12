CREATE TABLE IF NOT EXISTS strategy_weight (id INT AUTO_INCREMENT PRIMARY KEY, strategy_id INT NOT NULL, weight DECIMAL(7, 4) NOT NULL, effective_from DATE NOT NULL, created_at DATETIME(6) NOT NULL, CONSTRAINT fk_strategy_weight_strategy_id__id FOREIGN KEY (strategy_id) REFERENCES strategy(id) ON DELETE RESTRICT ON UPDATE RESTRICT);
CREATE INDEX strategy_weight_strategy_id_effective_from ON strategy_weight (strategy_id, effective_from);
-- asset_class cannot be added NOT NULL in one statement on a non-empty strategy table: strict-mode
-- MySQL fails with ERROR 1138 (no implicit default) and non-strict mode backfills "" which
-- AssetClass.valueOf("") throws on at first read. Add nullable, backfill every existing strategy
-- as ACOES (product decision), then narrow.
ALTER TABLE strategy ADD asset_class VARCHAR(20) NULL;
UPDATE strategy SET asset_class = 'ACOES' WHERE asset_class IS NULL;
ALTER TABLE strategy MODIFY asset_class VARCHAR(20) NOT NULL;
