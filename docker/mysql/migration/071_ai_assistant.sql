CREATE TABLE IF NOT EXISTS ai_assistant (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL,
    description VARCHAR(512) NOT NULL DEFAULT '',
    avatar VARCHAR(32) NOT NULL DEFAULT '',
    system_prompt LONGTEXT NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    chat_model_id BIGINT NULL,
    vector_model_id BIGINT NULL,
    sort INT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_ai_assistant_enabled_sort (enabled, sort, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO ai_assistant (
    name, description, avatar, system_prompt, enabled, chat_model_id, vector_model_id, sort
)
SELECT '知识库助手', '基于爬虫知识库回答问题，并附带资料来源。',
       'knowledge', '你是一个专业的中文知识库助手。请只依据提供的资料回答问题；资料不足时明确说明，不要编造。',
       1, c.id, v.id, 0
FROM ai_rag_chat_model c
JOIN ai_rag_vector_model v
WHERE c.id = (SELECT MIN(id) FROM ai_rag_chat_model)
  AND v.id = (SELECT MIN(id) FROM ai_rag_vector_model)
  AND NOT EXISTS (SELECT 1 FROM ai_assistant WHERE name = '知识库助手');

SET @ai_chat_assistant_column_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_chat_conversation'
       AND column_name = 'assistant_id') = 0,
    'ALTER TABLE ai_chat_conversation ADD COLUMN assistant_id BIGINT NULL AFTER user_id',
    'SELECT 1'
);
PREPARE ai_chat_assistant_column_stmt FROM @ai_chat_assistant_column_sql;
EXECUTE ai_chat_assistant_column_stmt;
DEALLOCATE PREPARE ai_chat_assistant_column_stmt;

SET @ai_chat_assistant_index_sql = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_chat_conversation'
       AND index_name = 'idx_ai_chat_conversation_assistant') = 0,
    'ALTER TABLE ai_chat_conversation ADD INDEX idx_ai_chat_conversation_assistant (assistant_id)',
    'SELECT 1'
);
PREPARE ai_chat_assistant_index_stmt FROM @ai_chat_assistant_index_sql;
EXECUTE ai_chat_assistant_index_stmt;
DEALLOCATE PREPARE ai_chat_assistant_index_stmt;

UPDATE ai_chat_conversation conversation
JOIN ai_assistant assistant ON assistant.name = '知识库助手'
SET conversation.assistant_id = assistant.id
WHERE conversation.assistant_id IS NULL;

INSERT INTO sys_permission (name, code, parent_id, type, path, icon, sort)
SELECT 'AI 助手管理', 'ai:assistant', 0, 1, '/ai-assistants', 'Service', 10
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'ai:assistant');

UPDATE sys_permission menu
SET menu.name = 'AI 助手管理',
    menu.parent_id = 0,
    menu.type = 1,
    menu.path = '/ai-assistants',
    menu.icon = 'Service',
    menu.sort = 10,
    menu.deleted = 0
WHERE menu.code = 'ai:assistant';

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
JOIN sys_permission permission ON permission.code = 'ai:assistant'
WHERE role.code = 'admin';
