SET @cf_ip_drop_vless_ms = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'cf_ip_candidate' AND column_name = 'vless_ms') > 0,
    'ALTER TABLE cf_ip_candidate DROP COLUMN vless_ms',
    'SELECT 1'
);
PREPARE cf_ip_stmt FROM @cf_ip_drop_vless_ms;
EXECUTE cf_ip_stmt;
DEALLOCATE PREPARE cf_ip_stmt;

SET @cf_ip_drop_vless_status = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'cf_ip_candidate' AND column_name = 'vless_status') > 0,
    'ALTER TABLE cf_ip_candidate DROP COLUMN vless_status',
    'SELECT 1'
);
PREPARE cf_ip_stmt FROM @cf_ip_drop_vless_status;
EXECUTE cf_ip_stmt;
DEALLOCATE PREPARE cf_ip_stmt;

SET @cf_ip_drop_vless_error = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'cf_ip_candidate' AND column_name = 'vless_error') > 0,
    'ALTER TABLE cf_ip_candidate DROP COLUMN vless_error',
    'SELECT 1'
);
PREPARE cf_ip_stmt FROM @cf_ip_drop_vless_error;
EXECUTE cf_ip_stmt;
DEALLOCATE PREPARE cf_ip_stmt;
