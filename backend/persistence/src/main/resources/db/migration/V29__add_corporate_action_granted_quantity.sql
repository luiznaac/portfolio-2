-- A RIGHTS_ALLOTMENT's holder-specific grant: the underlying units held before the ex-date times
-- the issuance factor. Null on every other kind, so the column is additive.
ALTER TABLE corporate_action ADD COLUMN granted_quantity DECIMAL(18,6) NULL;
