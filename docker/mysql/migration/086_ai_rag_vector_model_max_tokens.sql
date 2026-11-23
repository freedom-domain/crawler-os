-- 向量模型增加 max_tokens 字段：记录模型最大输入 token 数（可选）。
-- 分块时若策略 chunk_size 超过该值会自动截断，避免 embedding 调用因超长输入失败。
-- ES 向量索引新增 heading / parent_text 字段（章节上下文 + small-to-big 父块），
-- 由服务启动时的索引初始化逻辑处理，无需数据库迁移。

SET @ai_rag_vector_model_max_tokens_column_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_rag_vector_model'
       AND column_name = 'max_tokens') = 0,
    'ALTER TABLE ai_rag_vector_model ADD COLUMN max_tokens INT NULL AFTER top_k',
    'SELECT 1'
);
PREPARE ai_rag_vector_model_max_tokens_column_stmt
    FROM @ai_rag_vector_model_max_tokens_column_sql;
EXECUTE ai_rag_vector_model_max_tokens_column_stmt;
DEALLOCATE PREPARE ai_rag_vector_model_max_tokens_column_stmt;
