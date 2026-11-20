CREATE TABLE IF NOT EXISTS ai_rag_vector_model (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL,
    embedding_provider VARCHAR(32) NOT NULL,
    embedding_base_url VARCHAR(512) NOT NULL,
    embedding_model VARCHAR(128) NOT NULL,
    dimensions INT NOT NULL,
    top_k INT NOT NULL,
    chunk_size INT NOT NULL,
    chunk_overlap INT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_rag_vector_model_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE ai_rag_config
    ADD COLUMN vector_model_id BIGINT NULL AFTER dimensions;

INSERT IGNORE INTO ai_rag_vector_model (
    name, embedding_provider, embedding_base_url, embedding_model,
    dimensions, top_k, chunk_size, chunk_overlap
)
SELECT CONCAT('默认向量模型-', environment), embedding_provider, embedding_base_url,
       embedding_model, dimensions, top_k, chunk_size, chunk_overlap
FROM ai_rag_config;

UPDATE ai_rag_config config
JOIN ai_rag_vector_model model
    ON model.name = CONCAT('默认向量模型-', config.environment)
SET config.vector_model_id = model.id;

INSERT INTO sys_permission (name, code, parent_id, type, path, icon, sort)
SELECT '向量模型', 'ai:vector-models', parent.id, 1, '/ai-vector-models', 'Cpu', 6
FROM sys_permission parent
WHERE parent.code = 'system'
  AND parent.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_permission existing WHERE existing.code = 'ai:vector-models'
  );

UPDATE sys_permission menu
JOIN sys_permission parent ON parent.code = 'system' AND parent.deleted = 0
SET menu.name = '向量模型',
    menu.parent_id = parent.id,
    menu.type = 1,
    menu.path = '/ai-vector-models',
    menu.icon = 'Cpu',
    menu.sort = 6,
    menu.deleted = 0
WHERE menu.code = 'ai:vector-models';

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
JOIN sys_permission permission ON permission.code = 'ai:vector-models'
WHERE role.code = 'admin';
