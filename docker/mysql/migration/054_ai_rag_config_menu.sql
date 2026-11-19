INSERT INTO sys_permission (name, code, parent_id, type, path, icon, sort)
SELECT '系统管理', 'system', 0, 0, NULL, 'Setting', 6
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission existing WHERE existing.code = 'system'
);

UPDATE sys_permission
SET deleted = 0
WHERE code = 'system';

INSERT INTO sys_permission (name, code, parent_id, type, path, icon, sort)
SELECT 'AI 配置', 'ai:config', parent.id, 1, '/ai-config', 'Setting', 5
FROM sys_permission parent
WHERE parent.code = 'system'
  AND parent.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_permission existing WHERE existing.code = 'ai:config'
  );

UPDATE sys_permission menu
JOIN sys_permission parent ON parent.code = 'system' AND parent.deleted = 0
SET menu.name = 'AI 配置',
    menu.parent_id = parent.id,
    menu.type = 1,
    menu.path = '/ai-config',
    menu.icon = 'Setting',
    menu.sort = 5,
    menu.deleted = 0
WHERE menu.code = 'ai:config';

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, permission.id
FROM sys_role r
JOIN sys_permission permission ON permission.code = 'ai:config'
WHERE r.code = 'admin';
