-- V3: drop old overloaded `value` columns (see issue #151)
ALTER TABLE bond DROP COLUMN value;
ALTER TABLE checking_account DROP COLUMN value;
ALTER TABLE index_value DROP COLUMN value;

-- Remove DEFAULT added by V2 so column matches Exposed Table definition
ALTER TABLE bond MODIFY COLUMN rate DECIMAL(8, 4) NOT NULL;
ALTER TABLE checking_account MODIFY COLUMN rate DECIMAL(8, 4) NOT NULL;
ALTER TABLE index_value MODIFY COLUMN observation DECIMAL(12, 8) NOT NULL;
