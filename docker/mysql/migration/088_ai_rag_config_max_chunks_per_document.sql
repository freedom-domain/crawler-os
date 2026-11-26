-- RAG 配置增加 max_chunks_per_document 字段：召回融合阶段每个文档（doc_id）最多保留的 chunk（命中）条数。
-- 默认/NULL 按 1 处理：仅保留代表 chunk；>1 时允许同一文档多个章节进入回答上下文。
SET @ai_rag_config_max_chunks_column_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_rag_config'
       AND column_name = 'max_chunks_per_document') = 0,
    'ALTER TABLE ai_rag_config ADD COLUMN max_chunks_per_document INT NULL AFTER top_k',
    'SELECT 1'
);
PREPARE ai_rag_config_max_chunks_stmt
    FROM @ai_rag_config_max_chunks_column_sql;
EXECUTE ai_rag_config_max_chunks_stmt;
DEALLOCATE PREPARE ai_rag_config_max_chunks_stmt;
