CREATE TABLE IF NOT EXISTS fund_catalog (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    fund_type VARCHAR(16) NOT NULL,
    code CHAR(6) NOT NULL,
    name VARCHAR(128) NOT NULL,
    category VARCHAR(64),
    pinyin VARCHAR(128),
    synced_at DATETIME NOT NULL,
    UNIQUE KEY uk_fund_catalog (fund_type, code),
    KEY idx_fund_catalog_name (name, code),
    KEY idx_fund_catalog_type_code (fund_type, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
