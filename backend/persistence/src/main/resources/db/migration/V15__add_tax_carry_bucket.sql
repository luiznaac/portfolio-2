CREATE TABLE IF NOT EXISTS tax_carry_bucket (id INT AUTO_INCREMENT PRIMARY KEY, kind VARCHAR(20) NOT NULL, opening_balance DECIMAL(18, 6) NOT NULL, created_at DATETIME(6) NOT NULL);
ALTER TABLE tax_carry_bucket ADD CONSTRAINT tax_carry_bucket_kind_unique UNIQUE (kind);
