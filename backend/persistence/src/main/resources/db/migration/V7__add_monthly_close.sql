CREATE TABLE IF NOT EXISTS monthly_close (id INT AUTO_INCREMENT PRIMARY KEY, `month` DATE NOT NULL, status VARCHAR(20) NOT NULL, closed_at DATETIME(6) NULL, created_at DATETIME(6) NOT NULL);
ALTER TABLE monthly_close ADD CONSTRAINT monthly_close_month_unique UNIQUE (`month`);
