CREATE TABLE IF NOT EXISTS ai_assistant_knowledge_base (
    assistant_id BIGINT NOT NULL,
    knowledge_base_id BIGINT NOT NULL,
    PRIMARY KEY (assistant_id, knowledge_base_id),
    KEY idx_ai_assistant_knowledge_base_assistant (assistant_id),
    KEY idx_ai_assistant_knowledge_base_base (knowledge_base_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @migrate_ai_assistant_kb_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_assistant'
       AND column_name = 'knowledge_base_id') > 0,
    'INSERT IGNORE INTO ai_assistant_knowledge_base (assistant_id, knowledge_base_id)
     SELECT id, knowledge_base_id FROM ai_assistant
     WHERE knowledge_base_id IS NOT NULL',
    'SELECT 1'
);
PREPARE migrate_ai_assistant_kb_stmt FROM @migrate_ai_assistant_kb_sql;
EXECUTE migrate_ai_assistant_kb_stmt;
DEALLOCATE PREPARE migrate_ai_assistant_kb_stmt;

SET @drop_ai_assistant_kb_index_sql = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_assistant'
       AND index_name = 'idx_ai_assistant_knowledge_base') > 0,
    'ALTER TABLE ai_assistant DROP INDEX idx_ai_assistant_knowledge_base',
    'SELECT 1'
);
PREPARE drop_ai_assistant_kb_index_stmt FROM @drop_ai_assistant_kb_index_sql;
EXECUTE drop_ai_assistant_kb_index_stmt;
DEALLOCATE PREPARE drop_ai_assistant_kb_index_stmt;

SET @drop_ai_assistant_kb_column_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_assistant'
       AND column_name = 'knowledge_base_id') > 0,
    'ALTER TABLE ai_assistant DROP COLUMN knowledge_base_id',
    'SELECT 1'
);
PREPARE drop_ai_assistant_kb_column_stmt FROM @drop_ai_assistant_kb_column_sql;
EXECUTE drop_ai_assistant_kb_column_stmt;
DEALLOCATE PREPARE drop_ai_assistant_kb_column_stmt;
