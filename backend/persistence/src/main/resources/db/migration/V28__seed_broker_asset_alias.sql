-- The known Rico descriptions, seeded as data so the first import resolves without a confirmation.
-- The descriptions are the normalized forms the parser produces (markers already stripped).
INSERT INTO broker_asset_alias (institution_id, normalized_description, ticker, created_at) VALUES
    (NULL, 'FII BCFF11', 'BCFF11', NOW()),
    (NULL, 'FII KNRI11', 'KNRI11', NOW()),
    (NULL, 'FII MXRF11', 'MXRF11', NOW()),
    (NULL, 'FII MALL11', 'MALL11', NOW()),
    (NULL, 'FII XPML11', 'XPML11', NOW()),
    (NULL, 'FII VISC11', 'VISC11', NOW()),
    (NULL, 'FII VILG11', 'VILG11', NOW()),
    (NULL, 'FII RBR PROP', 'RBRP11', NOW()),
    (NULL, 'FII RBR PROP DM 88,27', 'RBRP12', NOW()),
    (NULL, 'FII MCCI11', 'MCCI11', NOW()),
    (NULL, 'BANCO INTER PN', 'INBR32', NOW()),
    (NULL, 'BIDI11', 'BIDI11', NOW()),
    (NULL, 'ITAUUNIBANCO PN', 'ITSA4', NOW()),
    (NULL, 'ALUPAR PN', 'ALUP11', NOW()),
    (NULL, 'ENGIE BRASIL ON', 'EGIE3', NOW()),
    (NULL, 'M DIAS BRANCO ON', 'MDIA3', NOW()),
    (NULL, 'CVC BRASIL ON', 'CVCB3', NOW()),
    (NULL, 'ISHARES BOVA11', 'BOVA11', NOW()),
    (NULL, 'ISHARES IVVB11', 'IVVB11', NOW()),
    (NULL, 'FLEURY ON', 'FLRY3', NOW()),
    (NULL, 'B3 ON', 'B3SA3', NOW());
