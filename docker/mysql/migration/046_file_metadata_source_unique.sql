SET @file_metadata_source_unique_sql = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'file_metadata'
       AND index_name = 'uk_file_source') = 0,
    'ALTER TABLE file_metadata ADD UNIQUE KEY uk_file_source (source(768))',
    'SELECT 1'
);
PREPARE file_metadata_source_unique_stmt FROM @file_metadata_source_unique_sql;
EXECUTE file_metadata_source_unique_stmt;
DEALLOCATE PREPARE file_metadata_source_unique_stmt;
