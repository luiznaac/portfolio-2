-- A brapi movement whose amount came from the split-adjusted `rate` rather than the paid `rawRate`
-- is marked estimated, so the Proventos page can say the amount is an estimate.
ALTER TABLE cash_movement ADD COLUMN estimated BOOLEAN DEFAULT FALSE NOT NULL;
