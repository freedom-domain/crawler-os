CREATE TABLE IF NOT EXISTS paper_investment_account (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    initial_cash DECIMAL(18,2) NOT NULL,
    cash_balance DECIMAL(18,2) NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME,
    update_time DATETIME,
    UNIQUE KEY uk_paper_investment_user (user_id),
    KEY idx_paper_investment_account_deleted (deleted, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS paper_investment_holding (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    code CHAR(6) NOT NULL,
    name VARCHAR(128) NOT NULL,
    market VARCHAR(16) NOT NULL,
    instrument_type VARCHAR(16) NOT NULL,
    quantity INT NOT NULL DEFAULT 0,
    available_quantity INT NOT NULL DEFAULT 0,
    average_cost DECIMAL(18,6) NOT NULL DEFAULT 0,
    settled_date DATE,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME,
    update_time DATETIME,
    UNIQUE KEY uk_paper_investment_holding (user_id, code),
    KEY idx_paper_investment_holding_user (user_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS paper_investment_trade (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    code CHAR(6) NOT NULL,
    name VARCHAR(128) NOT NULL,
    market VARCHAR(16) NOT NULL,
    instrument_type VARCHAR(16) NOT NULL,
    side VARCHAR(4) NOT NULL,
    quantity INT NOT NULL,
    price DECIMAL(18,4) NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    commission DECIMAL(18,2) NOT NULL,
    stamp_tax DECIMAL(18,2) NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME,
    update_time DATETIME,
    KEY idx_paper_investment_trade_user_time (user_id, deleted, create_time, id),
    KEY idx_paper_investment_trade_code (user_id, code, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO sys_permission (id, name, code, parent_id, type, path, icon, sort)
SELECT (SELECT COALESCE(MAX(id), 0) + 1 FROM sys_permission),
       '模拟投资', 'investment', 0, 1, '/investment', 'TrendCharts', 8
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'investment');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.code = 'investment';
