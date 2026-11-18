CREATE TABLE IF NOT EXISTS cf_ip_range_probe_schedule (
    id TINYINT PRIMARY KEY,
    enabled TINYINT NOT NULL DEFAULT 0,
    frequency VARCHAR(16) NOT NULL DEFAULT 'DAILY',
    samples_per_range TINYINT NOT NULL DEFAULT 1,
    tls_server_name VARCHAR(253) NOT NULL DEFAULT 'www.cloudflare.com',
    last_run_at DATETIME,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO cf_ip_range_probe_schedule (id, enabled, frequency, samples_per_range, tls_server_name)
VALUES (1, 0, 'DAILY', 1, 'www.cloudflare.com')
ON DUPLICATE KEY UPDATE id = VALUES(id);
