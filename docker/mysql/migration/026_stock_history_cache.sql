CREATE TABLE IF NOT EXISTS stock_history_cache (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code CHAR(6) NOT NULL,
    trade_date DATE NOT NULL,
    name VARCHAR(128) NOT NULL,
    open DECIMAL(20,8),
    close DECIMAL(20,8),
    high DECIMAL(20,8),
    low DECIMAL(20,8),
    volume DECIMAL(28,6),
    change_percent DECIMAL(12,6),
    UNIQUE KEY uk_stock_history_cache (code, trade_date),
    KEY idx_stock_history_cache_date (code, trade_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
