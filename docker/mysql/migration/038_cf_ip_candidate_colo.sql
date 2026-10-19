SET @cf_ip_add_colo = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'cf_ip_candidate' AND column_name = 'colo') = 0,
    'ALTER TABLE cf_ip_candidate ADD COLUMN colo VARCHAR(3) AFTER region',
    'SELECT 1'
);
PREPARE cf_ip_stmt FROM @cf_ip_add_colo;
EXECUTE cf_ip_stmt;
DEALLOCATE PREPARE cf_ip_stmt;
