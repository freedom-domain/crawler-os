INSERT INTO sys_permission (name, code, parent_id, type, path, icon, sort)
SELECT '模型管理', 'ai:models', parent.id, 1, '/ai-models', 'Cpu', 6
FROM sys_permission parent
WHERE parent.code = 'system'
  AND parent.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_permission existing WHERE existing.code = 'ai:models'
  );

UPDATE sys_permission menu
JOIN sys_permission parent ON parent.code = 'system' AND parent.deleted = 0
SET menu.name = '模型管理',
    menu.parent_id = parent.id,
    menu.type = 1,
    menu.path = '/ai-models',
    menu.icon = 'Cpu',
    menu.sort = 6,
    menu.deleted = 0
WHERE menu.code = 'ai:models';

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT role.id, model_menu.id
FROM sys_role role
JOIN sys_permission model_menu ON model_menu.code = 'ai:models'
WHERE role.code = 'admin'
   OR EXISTS (
       SELECT 1
       FROM sys_role_permission role_permission
       JOIN sys_permission previous_menu ON previous_menu.id = role_permission.permission_id
       WHERE role_permission.role_id = role.id
         AND previous_menu.code IN ('ai:vector-models', 'ai:chat-models')
   );

UPDATE sys_permission
SET deleted = 1
WHERE code IN ('ai:vector-models', 'ai:chat-models');
