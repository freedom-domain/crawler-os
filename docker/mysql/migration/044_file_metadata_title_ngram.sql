SET @file_title_ngram_index_sql = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'file_metadata' AND index_name = 'idx_file_title_ngram') = 0,
    'ALTER TABLE file_metadata ADD FULLTEXT INDEX idx_file_title_ngram (title) WITH PARSER ngram',
    'SELECT 1'
);
PREPARE file_title_ngram_index_stmt FROM @file_title_ngram_index_sql;
EXECUTE file_title_ngram_index_stmt;
DEALLOCATE PREPARE file_title_ngram_index_stmt;