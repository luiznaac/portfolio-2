-- The XP reports quote a target price in "R$" or "US$" (JBS's BDR prints a dollar target). The
-- column is nullable: rows imported before it existed, and entries without a target, have no
-- currency. Null reads as reais, the reports' default.
ALTER TABLE edition_entry ADD COLUMN target_currency VARCHAR(3) NULL;
