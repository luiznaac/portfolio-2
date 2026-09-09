CREATE TABLE IF NOT EXISTS strategy_weight (id INT AUTO_INCREMENT PRIMARY KEY, strategy_id INT NOT NULL, weight DECIMAL(7, 4) NOT NULL, effective_from DATE NOT NULL, created_at DATETIME(6) NOT NULL, CONSTRAINT fk_strategy_weight_strategy_id__id FOREIGN KEY (strategy_id) REFERENCES strategy(id) ON DELETE RESTRICT ON UPDATE RESTRICT);
CREATE INDEX strategy_weight_strategy_id_effective_from ON strategy_weight (strategy_id, effective_from);
ALTER TABLE strategy ADD asset_class VARCHAR(20) NOT NULL;
