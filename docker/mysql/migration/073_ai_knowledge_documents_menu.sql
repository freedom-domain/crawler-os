INSERT INTO sys_permission (name, code, parent_id, type, path, icon, sort)
SELECT '知识库文档管理', 'ai:knowledge-documents', parent.id, 1,
       '/ai-knowledge-documents', 'Document', 9
FROM sys_permission parent
WHERE parent.code = 'system'
  AND parent.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_permission existing WHERE existing.code = 'ai:knowledge-documents'
  );

UPDATE sys_permission menu
JOIN sys_permission parent ON parent.code = 'system' AND parent.deleted = 0
SET menu.name = '知识库文档管理',
    menu.parent_id = parent.id,
    menu.type = 1,
    menu.path = '/ai-knowledge-documents',
    menu.icon = 'Document',
    menu.sort = 9,
    menu.deleted = 0
WHERE menu.code = 'ai:knowledge-documents';

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
JOIN sys_permission permission ON permission.code = 'ai:knowledge-documents'
WHERE role.code = 'admin';
