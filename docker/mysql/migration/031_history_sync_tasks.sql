CREATE TABLE IF NOT EXISTS history_sync_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    instrument_type VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    total_count INT NOT NULL DEFAULT 0,
    finished_count INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    message VARCHAR(2000),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at DATETIME,
    completed_at DATETIME,
    KEY idx_history_sync_task_type_created (instrument_type, created_at),
    KEY idx_history_sync_task_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS history_sync_task_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    instrument_type VARCHAR(16) NOT NULL,
    fund_type VARCHAR(16),
    code CHAR(6) NOT NULL,
    name VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    message VARCHAR(2000),
    point_count INT,
    started_at DATETIME,
    finished_at DATETIME,
    KEY idx_history_sync_item_task_status (task_id, status),
    KEY idx_history_sync_item_instrument (instrument_type, fund_type, code),
    CONSTRAINT fk_history_sync_item_task
        FOREIGN KEY (task_id) REFERENCES history_sync_task (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
