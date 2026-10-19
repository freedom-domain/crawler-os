SET @cf_ip_add_candidate_colo_updated_at = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'cf_ip_candidate' AND column_name = 'colo_updated_at') = 0,
    'ALTER TABLE cf_ip_candidate ADD COLUMN colo_updated_at DATETIME AFTER colo',
    'SELECT 1'
);
PREPARE cf_ip_stmt FROM @cf_ip_add_candidate_colo_updated_at;
EXECUTE cf_ip_stmt;
DEALLOCATE PREPARE cf_ip_stmt;

UPDATE cf_ip_candidate
SET colo_updated_at = last_tested_at
WHERE colo IS NOT NULL AND colo_updated_at IS NULL;

SET @cf_ip_add_range_colo = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'cf_ip_range' AND column_name = 'colo') = 0,
    'ALTER TABLE cf_ip_range ADD COLUMN colo VARCHAR(3) AFTER region',
    'SELECT 1'
);
PREPARE cf_ip_stmt FROM @cf_ip_add_range_colo;
EXECUTE cf_ip_stmt;
DEALLOCATE PREPARE cf_ip_stmt;

UPDATE cf_ip_range r
SET r.colo = (
    SELECT c.colo
    FROM cf_ip_candidate c
    WHERE c.source_range_id = r.id AND c.colo IS NOT NULL
    GROUP BY c.colo
    ORDER BY COUNT(*) DESC, MAX(c.colo_updated_at) DESC, c.colo ASC
    LIMIT 1
)
WHERE EXISTS (
    SELECT 1
    FROM cf_ip_candidate c
    WHERE c.source_range_id = r.id AND c.colo IS NOT NULL
);
