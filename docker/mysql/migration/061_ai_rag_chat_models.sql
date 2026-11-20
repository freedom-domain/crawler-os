CREATE TABLE IF NOT EXISTS ai_rag_chat_model (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL,
    chat_provider VARCHAR(32) NOT NULL,
    chat_base_url VARCHAR(512) NOT NULL,
    chat_model VARCHAR(128) NOT NULL,
    api_key VARCHAR(1024) NOT NULL DEFAULT '',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_rag_chat_model_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE ai_rag_config
    ADD COLUMN chat_model_id BIGINT NULL AFTER vector_model_id;

INSERT IGNORE INTO ai_rag_chat_model (
    name, chat_provider, chat_base_url, chat_model, api_key
)
SELECT CONCAT('默认聊天模型-', environment), chat_provider, chat_base_url, chat_model, api_key
FROM ai_rag_config;

UPDATE ai_rag_config config
JOIN ai_rag_chat_model model
    ON model.name = CONCAT('默认聊天模型-', config.environment)
SET config.chat_model_id = model.id,
    config.api_key = '';

INSERT INTO sys_permission (name, code, parent_id, type, path, icon, sort)
SELECT '聊天模型', 'ai:chat-models', parent.id, 1, '/ai-chat-models', 'ChatDotRound', 7
FROM sys_permission parent
WHERE parent.code = 'system'
  AND parent.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_permission existing WHERE existing.code = 'ai:chat-models'
  );

UPDATE sys_permission menu
JOIN sys_permission parent ON parent.code = 'system' AND parent.deleted = 0
SET menu.name = '聊天模型',
    menu.parent_id = parent.id,
    menu.type = 1,
    menu.path = '/ai-chat-models',
    menu.icon = 'ChatDotRound',
    menu.sort = 7,
    menu.deleted = 0
WHERE menu.code = 'ai:chat-models';

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
JOIN sys_permission permission ON permission.code = 'ai:chat-models'
WHERE role.code = 'admin';
