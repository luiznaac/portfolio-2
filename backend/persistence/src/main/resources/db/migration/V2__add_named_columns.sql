-- V2: add named columns alongside overloaded `value` columns (see issue #151)
ALTER TABLE bond ADD COLUMN rate DECIMAL(8, 4) NOT NULL DEFAULT 0.0000;
UPDATE bond SET rate = value;
ALTER TABLE checking_account ADD COLUMN rate DECIMAL(8, 4) NOT NULL DEFAULT 0.0000;
UPDATE checking_account SET rate = value;
ALTER TABLE index_value ADD COLUMN observation DECIMAL(12, 8) NOT NULL DEFAULT 0.00000000;
UPDATE index_value SET observation = value;
