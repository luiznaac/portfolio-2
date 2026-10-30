CREATE TABLE IF NOT EXISTS benchmark_close (id INT AUTO_INCREMENT PRIMARY KEY, `index` VARCHAR(10) NOT NULL, `date` DATE NOT NULL, `level` DECIMAL(18, 6) NOT NULL, `source` VARCHAR(10) NOT NULL, fetched_at DATETIME(6) NOT NULL);
ALTER TABLE benchmark_close ADD CONSTRAINT benchmark_close_index_date_unique UNIQUE (`index`, `date`);
