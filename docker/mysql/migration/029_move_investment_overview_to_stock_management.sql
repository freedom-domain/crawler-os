INSERT INTO sys_role_permission (role_id, permission_id)
SELECT DISTINCT old_role.role_id, stock_permission.id
FROM sys_role_permission old_role
JOIN sys_permission old_permission
    ON old_permission.id = old_role.permission_id
JOIN sys_permission stock_permission
    ON stock_permission.code = 'investment:stock-management'
LEFT JOIN sys_role_permission stock_role
    ON stock_role.role_id = old_role.role_id
    AND stock_role.permission_id = stock_permission.id
WHERE old_permission.code = 'investment:overview'
    AND stock_role.role_id IS NULL;

DELETE rp
FROM sys_role_permission rp
JOIN sys_permission p ON p.id = rp.permission_id
WHERE p.code = 'investment:overview';

DELETE FROM sys_permission
WHERE code = 'investment:overview';
