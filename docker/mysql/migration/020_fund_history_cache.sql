CREATE TABLE IF NOT EXISTS fund_history_cache (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    fund_type VARCHAR(16) NOT NULL,
    code CHAR(6) NOT NULL,
    frequency VARCHAR(8) NOT NULL,
    trade_date DATE NOT NULL,
    name VARCHAR(128) NOT NULL,
    open DECIMAL(20,8),
    close DECIMAL(20,8),
    high DECIMAL(20,8),
    low DECIMAL(20,8),
    volume DECIMAL(28,6),
    change_percent DECIMAL(12,6),
    unit_nav DECIMAL(20,8),
    cumulative_nav DECIMAL(20,8),
    UNIQUE KEY uk_fund_history_cache (fund_type, code, frequency, trade_date),
    KEY idx_fund_history_cache_date (code, fund_type, frequency, trade_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
