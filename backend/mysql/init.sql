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

CREATE TABLE checking_account (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    value DECIMAL(8, 4) NOT NULL,
    index_id VARCHAR(10) NOT NULL,
    maturity_duration VARCHAR(5) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,

    FOREIGN KEY (index_id) REFERENCES `index`(id)
);

CREATE TABLE bond (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    rate_type VARCHAR(10) NOT NULL,
    value DECIMAL(8, 4) NOT NULL,
    index_id VARCHAR(10),
    maturity_date DATE NOT NULL,
    checking_account_id INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,

    FOREIGN KEY (index_id) REFERENCES `index`(id),
    FOREIGN KEY (checking_account_id) REFERENCES checking_account(id)
);

CREATE TABLE bond_order (
    id INT AUTO_INCREMENT PRIMARY KEY,
    bond_id INT,
    checking_account_id INT,
    type VARCHAR(20) NOT NULL,
    date DATE NOT NULL,
    amount DECIMAL(12,2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,

    FOREIGN KEY (bond_id) REFERENCES bond(id),
    FOREIGN KEY (checking_account_id) REFERENCES checking_account(id),

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

CREATE TABLE bond_order_position (
    id INT AUTO_INCREMENT PRIMARY KEY,
    bond_order_id INT NOT NULL,
    date DATE NOT NULL,
    principal DECIMAL(12,2) NOT NULL,
    yield DECIMAL(12,2) NOT NULL,
    taxes DECIMAL(12,2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,

    FOREIGN KEY (bond_order_id) REFERENCES bond_order(id)
);

INSERT INTO `index` (id, created_at) VALUES ('CDI', NOW());
INSERT INTO `index` (id, created_at) VALUES ('IPCA', NOW());
INSERT INTO `index` (id, created_at) VALUES ('SELIC', NOW());

CREATE TABLE listed_asset (
    id INT PRIMARY KEY AUTO_INCREMENT,
    ticker VARCHAR(12) NOT NULL,
    kind VARCHAR(10) NOT NULL,
    name VARCHAR(150) NOT NULL,
    b3_identifier VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,

    UNIQUE (ticker)
);

CREATE TABLE listed_asset_ticker_history (
    id INT PRIMARY KEY AUTO_INCREMENT,
    listed_asset_id INT NOT NULL,
    ticker VARCHAR(12) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,

    FOREIGN KEY (listed_asset_id) REFERENCES listed_asset(id),

    INDEX (ticker, effective_from)
);

CREATE TABLE trade (
    id INT PRIMARY KEY AUTO_INCREMENT,
    listed_asset_id INT NOT NULL,
    date DATE NOT NULL,
    quantity DECIMAL(18, 8) NOT NULL,
    price DECIMAL(12, 4) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,

    FOREIGN KEY (listed_asset_id) REFERENCES listed_asset(id),

    INDEX (listed_asset_id, date)
);

CREATE TABLE corporate_action (
    id INT PRIMARY KEY AUTO_INCREMENT,
    listed_asset_id INT NOT NULL,
    type VARCHAR(20) NOT NULL,
    date DATE NOT NULL,
    ratio DECIMAL(18, 8),
    value_per_new_share DECIMAL(12, 4),
    new_ticker VARCHAR(12),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,

    FOREIGN KEY (listed_asset_id) REFERENCES listed_asset(id),

    INDEX (listed_asset_id, date)
);

CREATE TABLE listed_asset_position (
    id INT PRIMARY KEY AUTO_INCREMENT,
    listed_asset_id INT NOT NULL,
    date DATE NOT NULL,
    principal DECIMAL(14, 2) NOT NULL,
    yield DECIMAL(14, 2) NOT NULL,
    taxes DECIMAL(14, 2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,

    FOREIGN KEY (listed_asset_id) REFERENCES listed_asset(id),
    UNIQUE (listed_asset_id, date)
);
