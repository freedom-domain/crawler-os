SET @cf_ip_add_range_status_id_index = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'cf_ip_candidate'
       AND index_name = 'idx_cf_ip_candidate_source_status_id') = 0,
    'ALTER TABLE cf_ip_candidate ADD INDEX idx_cf_ip_candidate_source_status_id (source_range_id, status, id)',
    'SELECT 1'
);
PREPARE cf_ip_range_status_id_index_stmt FROM @cf_ip_add_range_status_id_index;
EXECUTE cf_ip_range_status_id_index_stmt;
DEALLOCATE PREPARE cf_ip_range_status_id_index_stmt;