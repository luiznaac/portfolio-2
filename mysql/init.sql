USE portfolio;

CREATE TABLE `index` (
    id VARCHAR(10) PRIMARY KEY,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE index_value (
    id INT PRIMARY KEY AUTO_INCREMENT,
    index_id VARCHAR(10) NOT NULL,
    date DATE NOT NULL,
    value DECIMAL(12, 8) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,

    FOREIGN KEY (index_id) REFERENCES `index`(id),
    UNIQUE (index_id, date)
);

CREATE TABLE bond (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    rate_type VARCHAR(10) NOT NULL,
    value DECIMAL(8, 4) NOT NULL,
    index_id VARCHAR(10),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,

    FOREIGN KEY (index_id) REFERENCES `index`(id)
);

CREATE TABLE bond_oder (
    id INT AUTO_INCREMENT PRIMARY KEY,
    bond_id INT NOT NULL,
    type VARCHAR(10) NOT NULL,
    date DATE NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,

    FOREIGN KEY (bond_id) REFERENCES bond(id)
);

CREATE TABLE bond_oder_statement (
    id INT AUTO_INCREMENT PRIMARY KEY,
    bond_order_id INT NOT NULL,
    type VARCHAR(20) NOT NULL,
    date DATE NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,

    FOREIGN KEY (bond_order_id) REFERENCES bond_oder(id),
    UNIQUE (bond_order_id, type, date)
);

INSERT INTO `index` (id, created_at) VALUES ('CDI', NOW());
INSERT INTO `index` (id, created_at) VALUES ('IPCA', NOW());
INSERT INTO `index` (id, created_at) VALUES ('SELIC', NOW());
