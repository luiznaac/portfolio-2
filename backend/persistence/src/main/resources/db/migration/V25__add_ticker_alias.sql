-- The ticker identity chain: an old code maps to the code that replaced it, with the event that
-- declared the rename. One row per old code keeps the chain followable (A -> B, B -> C) and makes a
-- re-import of the same TICKER_CHANGE an overwrite rather than a duplicate edge.
CREATE TABLE IF NOT EXISTS ticker_alias (old_ticker VARCHAR(12) NOT NULL, canonical_ticker VARCHAR(12) NOT NULL, effective_date DATE NOT NULL, `source` VARCHAR(20) NOT NULL, ratio DECIMAL(18, 8) NULL, corporate_action_id INT NULL, CONSTRAINT ticker_alias_pkey PRIMARY KEY (old_ticker));
