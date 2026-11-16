-- A broker note prints an asset description instead of a ticker. This table is the confirmed
-- description → ticker mapping, keyed by the normalized description. `institution_id` is null for
-- the global fallback, so a broker without an alias of its own still resolves.
CREATE TABLE IF NOT EXISTS broker_asset_alias (id INT AUTO_INCREMENT PRIMARY KEY, institution_id INT NULL, normalized_description VARCHAR(255) NOT NULL, ticker VARCHAR(12) NOT NULL, created_at DATETIME(6) NOT NULL, CONSTRAINT fk_broker_asset_alias_institution_id__id FOREIGN KEY (institution_id) REFERENCES institution(id) ON DELETE RESTRICT ON UPDATE RESTRICT);
ALTER TABLE broker_asset_alias ADD CONSTRAINT broker_asset_alias_institution_id_normalized_description_unique UNIQUE (institution_id, normalized_description);

-- The note's raw description (kept for the review) and the note number (provenance). Null for a
-- layout that already prints a ticker. The ticker itself becomes nullable because a broker-note row
-- has no ticker until the alias resolves its description.
ALTER TABLE import_row ADD COLUMN raw_description VARCHAR(255) NULL;
ALTER TABLE import_row ADD COLUMN note_number VARCHAR(40) NULL;
ALTER TABLE import_row MODIFY COLUMN ticker VARCHAR(12) NULL;
