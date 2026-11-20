DELETE config_role
FROM sys_role_permission config_role
JOIN sys_permission config_permission
    ON config_permission.id = config_role.permission_id
   AND config_permission.code = 'ai:config'
LEFT JOIN sys_role_permission chat_role
    ON chat_role.role_id = config_role.role_id
LEFT JOIN sys_permission chat_permission
    ON chat_permission.id = chat_role.permission_id
   AND chat_permission.code = 'ai:chat'
   AND chat_permission.deleted = 0
WHERE chat_permission.id IS NULL;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT chat_role.role_id, config_permission.id
FROM sys_role_permission chat_role
JOIN sys_permission chat_permission
    ON chat_permission.id = chat_role.permission_id
   AND chat_permission.code = 'ai:chat'
   AND chat_permission.deleted = 0
JOIN sys_permission config_permission
    ON config_permission.code = 'ai:config'
   AND config_permission.deleted = 0;
