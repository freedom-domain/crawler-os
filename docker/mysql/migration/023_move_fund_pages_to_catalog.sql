DELETE role_permission
FROM sys_role_permission role_permission
JOIN sys_permission permission ON permission.id = role_permission.permission_id
WHERE permission.code IN ('investment:onshore-fund', 'investment:offshore-fund');

DELETE FROM sys_permission
WHERE code IN ('investment:onshore-fund', 'investment:offshore-fund');
