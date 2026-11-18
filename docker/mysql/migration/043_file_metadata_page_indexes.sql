SET @file_page_index_sql = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'file_metadata' AND index_name = 'idx_file_page') = 0,
    'ALTER TABLE file_metadata ADD INDEX idx_file_page (deleted, create_time, id)',
    'SELECT 1'
);
PREPARE file_page_index_stmt FROM @file_page_index_sql;
EXECUTE file_page_index_stmt;
DEALLOCATE PREPARE file_page_index_stmt;

SET @file_category_page_index_sql = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'file_metadata' AND index_name = 'idx_file_category_page') = 0,
    'ALTER TABLE file_metadata ADD INDEX idx_file_category_page (deleted, category, create_time, id)',
    'SELECT 1'
);
PREPARE file_category_page_index_stmt FROM @file_category_page_index_sql;
EXECUTE file_category_page_index_stmt;
DEALLOCATE PREPARE file_category_page_index_stmt;

SET @file_spider_page_index_sql = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'file_metadata' AND index_name = 'idx_file_spider_page') = 0,
    'ALTER TABLE file_metadata ADD INDEX idx_file_spider_page (deleted, spider_id, create_time, id)',
    'SELECT 1'
);
PREPARE file_spider_page_index_stmt FROM @file_spider_page_index_sql;
EXECUTE file_spider_page_index_stmt;
DEALLOCATE PREPARE file_spider_page_index_stmt;
