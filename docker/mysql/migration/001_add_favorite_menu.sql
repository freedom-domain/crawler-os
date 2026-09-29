-- 为已有数据库增加“我的收藏”菜单
INSERT INTO sys_permission (id, name, code, parent_id, type, path, icon, sort)
SELECT 16, '我的收藏', 'favorite', 0, 1, '/favorite', 'Folder', 6
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'favorite');

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, id FROM sys_permission
WHERE code = 'favorite'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission WHERE role_id = 1 AND permission_id = sys_permission.id
  );
