INSERT INTO sys_permission (name, code, parent_id, type, path, icon, sort)
SELECT 'AI 助手', 'ai:chat', 0, 1, '/ai-chat', 'ChatDotRound', 9
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission existing WHERE existing.code = 'ai:chat'
);

UPDATE sys_permission
SET name = 'AI 助手',
    parent_id = 0,
    type = 1,
    path = '/ai-chat',
    icon = 'ChatDotRound',
    sort = 9,
    deleted = 0
WHERE code = 'ai:chat';

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
JOIN sys_permission permission ON permission.code = 'ai:chat'
WHERE role.code = 'admin';
