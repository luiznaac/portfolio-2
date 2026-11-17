-- One explicit brapi sync run: the window, the counts, the failed tickers and the raw payload the
-- run parsed. Keeping the raw body means a later parser change never needs another paid month.
CREATE TABLE IF NOT EXISTS brapi_sync (id INT AUTO_INCREMENT PRIMARY KEY, started_at DATETIME(6) NOT NULL, as_of DATE NOT NULL, window_from DATE NOT NULL, window_to DATE NOT NULL, ticker_count INT NOT NULL, fetched_count INT NOT NULL, applied_count INT NOT NULL, parked_count INT NOT NULL, income_count INT NOT NULL, failed_tickers TEXT NOT NULL, raw_payload LONGTEXT NOT NULL);
