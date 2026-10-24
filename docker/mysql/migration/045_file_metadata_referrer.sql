SET @file_metadata_referrer_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'file_metadata' AND column_name = 'referrer') = 0,
    'ALTER TABLE file_metadata ADD COLUMN referrer VARCHAR(1024) AFTER source',
    'SELECT 1'
);
PREPARE file_metadata_referrer_stmt FROM @file_metadata_referrer_sql;
EXECUTE file_metadata_referrer_stmt;
DEALLOCATE PREPARE file_metadata_referrer_stmt;
