USE portfolio;

CREATE TABLE `index` (
    id VARCHAR(10) PRIMARY KEY,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO `index` (id, created_at) VALUES ('CDI', NOW());
INSERT INTO `index` (id, created_at) VALUES ('IPCA', NOW());
INSERT INTO `index` (id, created_at) VALUES ('SELIC', NOW());
