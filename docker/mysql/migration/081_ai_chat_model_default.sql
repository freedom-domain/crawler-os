SET @add_chat_model_default_column_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_rag_chat_model'
       AND column_name = 'is_default') = 0,
    'ALTER TABLE ai_rag_chat_model ADD COLUMN is_default TINYINT(1) NOT NULL DEFAULT 0 AFTER api_key',
    'SELECT 1'
);
PREPARE add_chat_model_default_column_stmt FROM @add_chat_model_default_column_sql;
EXECUTE add_chat_model_default_column_stmt;
DEALLOCATE PREPARE add_chat_model_default_column_stmt;

SET @add_chat_model_default_guard_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_rag_chat_model'
       AND column_name = 'default_model_guard') = 0,
    'ALTER TABLE ai_rag_chat_model ADD COLUMN default_model_guard TINYINT GENERATED ALWAYS AS (CASE WHEN is_default = 1 THEN 1 ELSE NULL END) STORED',
    'SELECT 1'
);
PREPARE add_chat_model_default_guard_stmt FROM @add_chat_model_default_guard_sql;
EXECUTE add_chat_model_default_guard_stmt;
DEALLOCATE PREPARE add_chat_model_default_guard_stmt;

SET @add_chat_model_default_index_sql = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_rag_chat_model'
       AND index_name = 'uk_ai_rag_chat_model_default') = 0,
    'ALTER TABLE ai_rag_chat_model ADD UNIQUE KEY uk_ai_rag_chat_model_default (default_model_guard)',
    'SELECT 1'
);
PREPARE add_chat_model_default_index_stmt FROM @add_chat_model_default_index_sql;
EXECUTE add_chat_model_default_index_stmt;
DEALLOCATE PREPARE add_chat_model_default_index_stmt;

SELECT COALESCE(MAX(CASE WHEN is_default = 1 THEN id END), MIN(id))
INTO @default_chat_model_id
FROM ai_rag_chat_model;

UPDATE ai_rag_chat_model
SET is_default = 1
WHERE id = @default_chat_model_id;
