SET @cf_ip_add_region = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'cf_ip_candidate' AND column_name = 'region') = 0,
    'ALTER TABLE cf_ip_candidate ADD COLUMN region VARCHAR(64) NOT NULL DEFAULT ''全球任播'' AFTER ip_address',
    'SELECT 1'
);
PREPARE cf_ip_stmt FROM @cf_ip_add_region;
EXECUTE cf_ip_stmt;
DEALLOCATE PREPARE cf_ip_stmt;

SET @cf_ip_add_range_id = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'cf_ip_candidate' AND column_name = 'source_range_id') = 0,
    'ALTER TABLE cf_ip_candidate ADD COLUMN source_range_id BIGINT AFTER region',
    'SELECT 1'
);
PREPARE cf_ip_stmt FROM @cf_ip_add_range_id;
EXECUTE cf_ip_stmt;
DEALLOCATE PREPARE cf_ip_stmt;

SET @cf_ip_drop_tls_server_name = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'cf_ip_candidate' AND column_name = 'tls_server_name') > 0,
    'ALTER TABLE cf_ip_candidate DROP COLUMN tls_server_name',
    'SELECT 1'
);
PREPARE cf_ip_stmt FROM @cf_ip_drop_tls_server_name;
EXECUTE cf_ip_stmt;
DEALLOCATE PREPARE cf_ip_stmt;

SET @cf_ip_add_ping = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'cf_ip_candidate' AND column_name = 'ping_ms') = 0,
    'ALTER TABLE cf_ip_candidate ADD COLUMN ping_ms DECIMAL(9,3)',
    'SELECT 1'
);
PREPARE cf_ip_stmt FROM @cf_ip_add_ping;
EXECUTE cf_ip_stmt;
DEALLOCATE PREPARE cf_ip_stmt;

SET @cf_ip_add_tls = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'cf_ip_candidate' AND column_name = 'tls_ms') = 0,
    'ALTER TABLE cf_ip_candidate ADD COLUMN tls_ms DECIMAL(9,3)',
    'SELECT 1'
);
PREPARE cf_ip_stmt FROM @cf_ip_add_tls;
EXECUTE cf_ip_stmt;
DEALLOCATE PREPARE cf_ip_stmt;

SET @cf_ip_add_speed = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'cf_ip_candidate' AND column_name = 'speed_mbps') = 0,
    'ALTER TABLE cf_ip_candidate ADD COLUMN speed_mbps DECIMAL(10,3)',
    'SELECT 1'
);
PREPARE cf_ip_stmt FROM @cf_ip_add_speed;
EXECUTE cf_ip_stmt;
DEALLOCATE PREPARE cf_ip_stmt;
