DELETE rp
FROM sys_role_permission rp
JOIN sys_permission p ON p.id = rp.permission_id
WHERE p.code = 'investment:stock';

DELETE FROM sys_permission
WHERE code = 'investment:stock';

UPDATE sys_permission
SET name = '股票模拟交易'
WHERE code = 'investment:overview';

UPDATE sys_permission
SET sort = 2
WHERE code = 'investment:onshore-fund';

UPDATE sys_permission
SET sort = 3
WHERE code = 'investment:offshore-fund';
