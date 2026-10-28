-- A Strategy is either report-driven or manual. The column is NOT NULL: "manual" is never inferred
-- from the absence of an Edition, because a Report Strategy is edition-less until its first import.
-- The default exists only to backfill the existing rows; new inserts always carry a kind.
ALTER TABLE strategy ADD COLUMN kind VARCHAR(20) NOT NULL DEFAULT 'REPORT_DRIVEN';
ALTER TABLE strategy ALTER COLUMN kind DROP DEFAULT;

-- The strategy's share lives in the month's Target rows, never on the strategy itself.
ALTER TABLE strategy DROP COLUMN weight;

-- The catalog row's manual class override lands in the same schema change as the Strategy kind, so
-- classification has its storage the moment the manual bucket exists.
ALTER TABLE ticker_catalog ADD COLUMN asset_class_override VARCHAR(20) NULL;
