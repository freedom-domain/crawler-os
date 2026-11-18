CREATE TABLE IF NOT EXISTS history_sync_setting (
    id TINYINT PRIMARY KEY,
    max_concurrent_requests INT NOT NULL DEFAULT 1,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO history_sync_setting (id, max_concurrent_requests) VALUES (1, 1);
