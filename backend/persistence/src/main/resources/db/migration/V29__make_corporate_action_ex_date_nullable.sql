-- A brapi event with no ex-date waits in review instead of the engine guessing a day to apply it
-- across a trade, so the column is nullable on a parked row. An applied row always has one.
ALTER TABLE corporate_action MODIFY ex_date DATE NULL;
