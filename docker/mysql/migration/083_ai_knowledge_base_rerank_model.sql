SET @add_knowledge_base_rerank_model_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_knowledge_base'
       AND column_name = 'rerank_model_id') = 0,
    'ALTER TABLE ai_knowledge_base ADD COLUMN rerank_model_id BIGINT NULL AFTER vector_model_id',
    'SELECT 1'
);
PREPARE add_knowledge_base_rerank_model_stmt FROM @add_knowledge_base_rerank_model_sql;
EXECUTE add_knowledge_base_rerank_model_stmt;
DEALLOCATE PREPARE add_knowledge_base_rerank_model_stmt;
