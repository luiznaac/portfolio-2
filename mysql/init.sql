USE portfolio;

CREATE TABLE `index` (
    id VARCHAR(10) PRIMARY KEY,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE index_value (
    id INT PRIMARY KEY AUTO_INCREMENT,
    index_id VARCHAR(10) NOT NULL,
    date DATE NOT NULL,
    value DECIMAL(12, 6) NOT NULL,
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
    maturity_date DATE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,

    FOREIGN KEY (index_id) REFERENCES `index`(id)
);

CREATE TABLE bond_order (
    id INT AUTO_INCREMENT PRIMARY KEY,
    bond_id INT NOT NULL,
    type VARCHAR(20) NOT NULL,
    date DATE NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,

    FOREIGN KEY (bond_id) REFERENCES bond(id),

    INDEX (bond_id, type)
);

CREATE TABLE bond_order_statement (
    id INT AUTO_INCREMENT PRIMARY KEY,
    buy_order_id INT NOT NULL,
    sell_order_id INT,
    type VARCHAR(20) NOT NULL,
    date DATE NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,

    FOREIGN KEY (buy_order_id) REFERENCES bond_order(id),
    FOREIGN KEY (sell_order_id) REFERENCES bond_order(id),
    UNIQUE (buy_order_id, type, date),

    INDEX (buy_order_id, date)
);

CREATE TABLE checking_account (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    value DECIMAL(8, 4) NOT NULL,
    index_id VARCHAR(10) NOT NULL,
    maturity_duration VARCHAR(5) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,

    FOREIGN KEY (index_id) REFERENCES `index`(id)
);

INSERT INTO `index` (id, created_at) VALUES ('CDI', NOW());
INSERT INTO `index` (id, created_at) VALUES ('IPCA', NOW());
INSERT INTO `index` (id, created_at) VALUES ('SELIC', NOW());
