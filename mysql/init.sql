USE portfolio;

CREATE TABLE `index` (
    id VARCHAR(10) PRIMARY KEY,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE index_value (
    id INT PRIMARY KEY AUTO_INCREMENT,
    index_id VARCHAR(10),
    date DATE NOT NULL,
    value DECIMAL(12, 8) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (index_id) REFERENCES `index`(id)
);

INSERT INTO `index` (id, created_at) VALUES ('CDI', NOW());
INSERT INTO `index` (id, created_at) VALUES ('IPCA', NOW());
INSERT INTO `index` (id, created_at) VALUES ('SELIC', NOW());
