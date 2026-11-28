-- 智能体角色：可复用的角色定义（名称、简介、系统提示词），智能体可选择角色，
-- 选择后角色系统提示词优先生效；角色停用或被清空时回退智能体自身提示词。
CREATE TABLE IF NOT EXISTS ai_assistant_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL,
    description VARCHAR(512) NOT NULL DEFAULT '',
    system_prompt LONGTEXT NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    sort INT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_ai_assistant_role_enabled_sort (enabled, sort, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO ai_assistant_role (name, description, system_prompt, enabled, sort)
SELECT '知识库问答助手', '基于知识库资料回答问题，并附带资料来源。',
       '你是一个专业的中文知识库助手。请只依据提供的资料回答问题；资料不足时明确说明，不要编造。',
       1, 0
WHERE NOT EXISTS (SELECT 1 FROM ai_assistant_role WHERE name = '知识库问答助手');

INSERT INTO ai_assistant_role (name, description, system_prompt, enabled, sort)
SELECT '数据分析助手', '面向业务数据与统计结果的分析与解读。',
       '你是一个专业的中文数据分析助手。请依据提供的数据与资料回答问题，先给出结论再解释依据；数据不足时明确说明不确定性，不要编造数据。',
       1, 1
WHERE NOT EXISTS (SELECT 1 FROM ai_assistant_role WHERE name = '数据分析助手');

INSERT INTO ai_assistant_role (name, description, system_prompt, enabled, sort)
SELECT '文档写作助手', '起草、整理与润色各类中文文档。',
       '你是一个专业的中文文档写作助手。请用规范、简洁的中文撰写内容，结构清晰、重点突出；依据资料写作时保持原意，不要虚构事实。',
       1, 2
WHERE NOT EXISTS (SELECT 1 FROM ai_assistant_role WHERE name = '文档写作助手');

INSERT INTO ai_assistant_role (name, description, system_prompt, enabled, sort)
SELECT '通用智能体', '不限定领域的通用问答角色。',
       '你是一个友好的中文通用智能助手。请直接、准确地回答用户问题，语气自然礼貌；不确定时明确说明，不要编造事实。',
       1, 3
WHERE NOT EXISTS (SELECT 1 FROM ai_assistant_role WHERE name = '通用智能体');

SET @ai_assistant_role_column_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_assistant'
       AND column_name = 'role_id') = 0,
    'ALTER TABLE ai_assistant ADD COLUMN role_id BIGINT NULL AFTER system_prompt',
    'SELECT 1'
);
PREPARE ai_assistant_role_column_stmt FROM @ai_assistant_role_column_sql;
EXECUTE ai_assistant_role_column_stmt;
DEALLOCATE PREPARE ai_assistant_role_column_stmt;

SET @ai_assistant_role_index_sql = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_assistant'
       AND index_name = 'idx_ai_assistant_role') = 0,
    'ALTER TABLE ai_assistant ADD INDEX idx_ai_assistant_role (role_id)',
    'SELECT 1'
);
PREPARE ai_assistant_role_index_stmt FROM @ai_assistant_role_index_sql;
EXECUTE ai_assistant_role_index_stmt;
DEALLOCATE PREPARE ai_assistant_role_index_stmt;

-- 将默认「知识库助手」关联到「知识库问答助手」角色，保持原有提示词语义
UPDATE ai_assistant assistant
JOIN ai_assistant_role role ON role.name = '知识库问答助手'
SET assistant.role_id = role.id
WHERE assistant.name = '知识库助手'
  AND assistant.role_id IS NULL;

INSERT INTO sys_permission (name, code, parent_id, type, path, icon, sort)
SELECT '智能体角色', 'ai:assistant-roles', 0, 1, '/ai-assistant-roles', 'Avatar', 11
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'ai:assistant-roles');

UPDATE sys_permission menu
SET menu.name = '智能体角色',
    menu.parent_id = 0,
    menu.type = 1,
    menu.path = '/ai-assistant-roles',
    menu.icon = 'Avatar',
    menu.sort = 11,
    menu.deleted = 0
WHERE menu.code = 'ai:assistant-roles';

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
JOIN sys_permission permission ON permission.code = 'ai:assistant-roles'
WHERE role.code = 'admin';

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.code = 'ai:assistant-roles'
WHERE EXISTS (
    SELECT 1
    FROM sys_role_permission assistant_grant
    JOIN sys_permission assistant_permission
        ON assistant_permission.id = assistant_grant.permission_id
       AND assistant_permission.code = 'ai:assistant'
    WHERE assistant_grant.role_id = r.id
);
