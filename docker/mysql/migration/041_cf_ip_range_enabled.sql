SET @cf_ip_add_range_enabled = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'cf_ip_range' AND column_name = 'enabled') = 0,
    'ALTER TABLE cf_ip_range ADD COLUMN enabled TINYINT NOT NULL DEFAULT 1 AFTER active',
    'SELECT 1'
);
PREPARE cf_ip_stmt FROM @cf_ip_add_range_enabled;
EXECUTE cf_ip_stmt;
DEALLOCATE PREPARE cf_ip_stmt;
