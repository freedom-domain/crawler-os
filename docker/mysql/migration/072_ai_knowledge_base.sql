CREATE TABLE IF NOT EXISTS ai_knowledge_base (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL,
    description VARCHAR(512) NOT NULL DEFAULT '',
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    vector_model_id BIGINT NULL,
    source_index VARCHAR(255) NOT NULL,
    vector_index VARCHAR(255) NOT NULL,
    vector_field VARCHAR(255) NOT NULL,
    sort INT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ai_knowledge_base_name (name),
    KEY idx_ai_knowledge_base_enabled_sort (enabled, sort, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @ai_assistant_kb_column_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_assistant'
       AND column_name = 'knowledge_base_id') = 0,
    'ALTER TABLE ai_assistant ADD COLUMN knowledge_base_id BIGINT NULL AFTER vector_model_id',
    'SELECT 1'
);
PREPARE ai_assistant_kb_column_stmt FROM @ai_assistant_kb_column_sql;
EXECUTE ai_assistant_kb_column_stmt;
DEALLOCATE PREPARE ai_assistant_kb_column_stmt;

SET @ai_assistant_kb_index_sql = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_assistant'
       AND index_name = 'idx_ai_assistant_knowledge_base') = 0,
    'ALTER TABLE ai_assistant ADD INDEX idx_ai_assistant_knowledge_base (knowledge_base_id)',
    'SELECT 1'
);
PREPARE ai_assistant_kb_index_stmt FROM @ai_assistant_kb_index_sql;
EXECUTE ai_assistant_kb_index_stmt;
DEALLOCATE PREPARE ai_assistant_kb_index_stmt;

INSERT IGNORE INTO ai_knowledge_base (
    name, description, enabled, vector_model_id,
    source_index, vector_index, vector_field, sort
)
SELECT '默认知识库',
       '系统现有爬虫内容知识库。',
       1,
       config.vector_model_id,
       config.source_index,
       COALESCE(config.vector_index, CONCAT(config.vector_index_prefix, '_', LOWER(REPLACE(config.embedding_model, '-', '_')))),
       CONCAT(config.vector_field_prefix, '_', LOWER(REPLACE(config.embedding_model, '-', '_'))),
       0
FROM ai_rag_config config
WHERE NOT EXISTS (SELECT 1 FROM ai_knowledge_base);

UPDATE ai_assistant assistant
JOIN ai_knowledge_base base ON base.name = '默认知识库'
SET assistant.knowledge_base_id = base.id
WHERE assistant.knowledge_base_id IS NULL;
