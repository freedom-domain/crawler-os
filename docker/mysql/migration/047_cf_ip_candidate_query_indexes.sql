SET @cf_ip_add_region_index = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'cf_ip_candidate'
       AND index_name = 'idx_cf_ip_candidate_region') = 0,
    'ALTER TABLE cf_ip_candidate ADD INDEX idx_cf_ip_candidate_region (region)',
    'SELECT 1'
);
PREPARE cf_ip_region_index_stmt FROM @cf_ip_add_region_index;
EXECUTE cf_ip_region_index_stmt;
DEALLOCATE PREPARE cf_ip_region_index_stmt;

SET @cf_ip_add_colo_index = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'cf_ip_candidate'
       AND index_name = 'idx_cf_ip_candidate_colo') = 0,
    'ALTER TABLE cf_ip_candidate ADD INDEX idx_cf_ip_candidate_colo (colo)',
    'SELECT 1'
);
PREPARE cf_ip_colo_index_stmt FROM @cf_ip_add_colo_index;
EXECUTE cf_ip_colo_index_stmt;
DEALLOCATE PREPARE cf_ip_colo_index_stmt;

SET @cf_ip_add_status_id_index = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'cf_ip_candidate'
       AND index_name = 'idx_cf_ip_candidate_status_id') = 0,
    'ALTER TABLE cf_ip_candidate ADD INDEX idx_cf_ip_candidate_status_id (status, id)',
    'SELECT 1'
);
PREPARE cf_ip_status_id_index_stmt FROM @cf_ip_add_status_id_index;
EXECUTE cf_ip_status_id_index_stmt;
DEALLOCATE PREPARE cf_ip_status_id_index_stmt;