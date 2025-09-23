USE portfolio;

CREATE TABLE `index` (
    id VARCHAR(10) PRIMARY KEY,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE index_value (
    id INT PRIMARY KEY AUTO_INCREMENT,
    index_id VARCHAR(10) NOT NULL,
    date DATE NOT NULL,
    value DECIMAL(12, 8) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (index_id) REFERENCES `index`(id),
    UNIQUE (index_id, date)
);

CREATE TABLE bond (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    rate_type VARCHAR(10) NOT NULL,
    value DECIMAL(8, 4) NOT NULL,
    index_id VARCHAR(10),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (index_id) REFERENCES `index`(id)
);

INSERT INTO `index` (id, created_at) VALUES ('CDI', NOW());
INSERT INTO `index` (id, created_at) VALUES ('IPCA', NOW());
INSERT INTO `index` (id, created_at) VALUES ('SELIC', NOW());
