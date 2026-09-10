-- Fase 2: a broker model-portfolio report imported as an immutable, dated edition.
CREATE TABLE IF NOT EXISTS strategy_edition (
    id             INT AUTO_INCREMENT PRIMARY KEY,
    strategy_id    INT         NOT NULL,
    reference_date DATE        NOT NULL,
    changes_text   TEXT        NULL,
    created_at     DATETIME(6) NOT NULL,
    CONSTRAINT fk_strategy_edition_strategy_id__id
        FOREIGN KEY (strategy_id) REFERENCES strategy (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
);

ALTER TABLE strategy_edition
    ADD CONSTRAINT strategy_edition_strategy_id_reference_date_unique
        UNIQUE (strategy_id, reference_date);

CREATE TABLE IF NOT EXISTS strategy_target (
    id                  INT AUTO_INCREMENT PRIMARY KEY,
    strategy_edition_id INT            NOT NULL,
    ticker              VARCHAR(12)    NOT NULL,
    weight              DECIMAL(7, 4)  NOT NULL,
    rating              VARCHAR(20)    NULL,
    target_price        DECIMAL(12, 4) NULL,
    CONSTRAINT fk_strategy_target_strategy_edition_id__id
        FOREIGN KEY (strategy_edition_id) REFERENCES strategy_edition (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX strategy_target_strategy_edition_id
    ON strategy_target (strategy_edition_id);
