CREATE TABLE IF NOT EXISTS stock_watchlist (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code CHAR(6) NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_stock_watchlist_code (code),
    KEY idx_stock_watchlist_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
