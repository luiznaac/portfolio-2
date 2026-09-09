-- Aligns two columns that V1 already declares wider than what production physically has today
-- (V1 is a baseline snapshot for existing databases — see backend/CLAUDE.md §7 — so its own
-- CREATE TABLE statements never run against them): index_value.value is DECIMAL(12, 6) in
-- production but DECIMAL(12, 8) in code, and bond_order_position's three money columns are
-- DECIMAL(12, 2) in production but DECIMAL(14, 2) in code. Widening a DECIMAL's precision/scale
-- is non-destructive — existing values round-trip unchanged. A no-op on any database that
-- reached this point via V1 directly (already at the wide precision).
ALTER TABLE index_value MODIFY COLUMN value DECIMAL(12, 8) NOT NULL;
ALTER TABLE bond_order_position MODIFY COLUMN principal DECIMAL(14, 2) NOT NULL;
ALTER TABLE bond_order_position MODIFY COLUMN yield DECIMAL(14, 2) NOT NULL;
ALTER TABLE bond_order_position MODIFY COLUMN taxes DECIMAL(14, 2) NOT NULL;
