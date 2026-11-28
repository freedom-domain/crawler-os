-- ES 来源知识库支持按爬虫筛选：spider_ids 存储所选爬虫 ID 的 JSON 数组（如 [1,2]），
-- NULL/空数组表示收录全部爬虫的文档。
SET @ai_knowledge_base_spider_ids_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_knowledge_base'
       AND column_name = 'spider_ids') = 0,
    'ALTER TABLE ai_knowledge_base ADD COLUMN spider_ids JSON NULL AFTER upload_bucket',
    'SELECT 1'
);
PREPARE ai_knowledge_base_spider_ids_stmt FROM @ai_knowledge_base_spider_ids_sql;
EXECUTE ai_knowledge_base_spider_ids_stmt;
DEALLOCATE PREPARE ai_knowledge_base_spider_ids_stmt;
