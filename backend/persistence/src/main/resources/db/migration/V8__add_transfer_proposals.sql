-- Fase 3: the transfer-proposal lifecycle (PENDING -> APPLIED | REJECTED), scoped to one month.
CREATE TABLE IF NOT EXISTS transfer_proposal (
    id                INT AUTO_INCREMENT PRIMARY KEY,
    `month`           DATE           NOT NULL,
    listed_asset_id   INT            NOT NULL,
    ticker            VARCHAR(12)    NOT NULL,
    from_strategy_id  INT            NOT NULL,
    to_strategy_id    INT            NOT NULL,
    proposed_quantity DECIMAL(18, 8) NOT NULL,
    applied_quantity  DECIMAL(18, 8) NULL,
    status            VARCHAR(20)    NOT NULL,
    decided_at        DATETIME(6)    NULL,
    created_at        DATETIME(6)    NOT NULL,
    CONSTRAINT fk_transfer_proposal_listed_asset_id__id
        FOREIGN KEY (listed_asset_id) REFERENCES listed_asset (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_transfer_proposal_from_strategy_id__id
        FOREIGN KEY (from_strategy_id) REFERENCES strategy (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_transfer_proposal_to_strategy_id__id
        FOREIGN KEY (to_strategy_id) REFERENCES strategy (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
);

-- UNIQUE, not just an index: the reconcile step looks a pairing up by exactly these four columns
-- and assumes at most one row comes back (see OrderPlanService.reconcileProposal).
CREATE UNIQUE INDEX transfer_proposal_pairing
    ON transfer_proposal (`month`, listed_asset_id, from_strategy_id, to_strategy_id);

-- Single-row settings table: exactly one TransferSettings for the whole app, at id 1.
CREATE TABLE IF NOT EXISTS transfer_settings (
    id                      INT AUTO_INCREMENT PRIMARY KEY,
    auto_approval_threshold DECIMAL(12, 2) NOT NULL
);
