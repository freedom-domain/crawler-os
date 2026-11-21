SET @drop_ai_assistant_chat_model_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_assistant'
       AND column_name = 'chat_model_id') > 0,
    'ALTER TABLE ai_assistant DROP COLUMN chat_model_id',
    'SELECT 1'
);
PREPARE drop_ai_assistant_chat_model_stmt FROM @drop_ai_assistant_chat_model_sql;
EXECUTE drop_ai_assistant_chat_model_stmt;
DEALLOCATE PREPARE drop_ai_assistant_chat_model_stmt;

SET @drop_ai_assistant_vector_model_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_assistant'
       AND column_name = 'vector_model_id') > 0,
    'ALTER TABLE ai_assistant DROP COLUMN vector_model_id',
    'SELECT 1'
);
PREPARE drop_ai_assistant_vector_model_stmt FROM @drop_ai_assistant_vector_model_sql;
EXECUTE drop_ai_assistant_vector_model_stmt;
DEALLOCATE PREPARE drop_ai_assistant_vector_model_stmt;
