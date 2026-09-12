-- The codebase moved to English identifiers (see backend/DEVELOPMENT.md). These enum values are
-- stored as plain strings, so renaming the Kotlin enums orphans existing rows unless the data is
-- rewritten with them. `REAL_STATE` -> `REAL_ESTATE` also fixes a misspelling.
--
-- Only tables that already exist in production are touched here. The enums introduced by this same
-- stack (transfer_proposal.status, monthly_close.status) never shipped under their old names, so
-- their migrations were simply written in English instead.

UPDATE asset_class_target
SET asset_class = CASE asset_class
                      WHEN 'ACOES' THEN 'STOCKS'
                      WHEN 'REAL_STATE' THEN 'REAL_ESTATE'
                      WHEN 'RENDA_FIXA' THEN 'FIXED_INCOME'
                      WHEN 'ALTERNATIVOS' THEN 'ALTERNATIVES'
                      WHEN 'CAIXA' THEN 'CASH'
                      ELSE asset_class
    END;

UPDATE product_classification
SET asset_class = CASE asset_class
                      WHEN 'ACOES' THEN 'STOCKS'
                      WHEN 'REAL_STATE' THEN 'REAL_ESTATE'
                      WHEN 'RENDA_FIXA' THEN 'FIXED_INCOME'
                      WHEN 'ALTERNATIVOS' THEN 'ALTERNATIVES'
                      WHEN 'CAIXA' THEN 'CASH'
                      ELSE asset_class
    END;

UPDATE strategy
SET asset_class = CASE asset_class
                      WHEN 'ACOES' THEN 'STOCKS'
                      WHEN 'REAL_STATE' THEN 'REAL_ESTATE'
                      WHEN 'RENDA_FIXA' THEN 'FIXED_INCOME'
                      WHEN 'ALTERNATIVOS' THEN 'ALTERNATIVES'
                      WHEN 'CAIXA' THEN 'CASH'
                      ELSE asset_class
    END;

UPDATE fixed_income_subclass_target
SET sub_class = CASE sub_class
                    WHEN 'POS_FIXADO' THEN 'FLOATING_RATE'
                    WHEN 'PRE_FIXADO' THEN 'FIXED_RATE'
                    WHEN 'INFLACAO' THEN 'INFLATION_LINKED'
                    ELSE sub_class
    END;

UPDATE attribution_movement
SET reason = CASE reason
                 WHEN 'COMPRA' THEN 'BUY'
                 WHEN 'VENDA' THEN 'SELL'
                 WHEN 'TRANSFERENCIA' THEN 'TRANSFER'
                 WHEN 'AJUSTE' THEN 'ADJUSTMENT'
                 ELSE reason
    END;
