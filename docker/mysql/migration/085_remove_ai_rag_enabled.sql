SET @ai_rag_enabled_column_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_rag_config'
       AND column_name = 'enabled') > 0,
    'ALTER TABLE ai_rag_config DROP COLUMN enabled',
    'SELECT 1'
);
PREPARE ai_rag_enabled_column_stmt FROM @ai_rag_enabled_column_sql;
EXECUTE ai_rag_enabled_column_stmt;
DEALLOCATE PREPARE ai_rag_enabled_column_stmt;
