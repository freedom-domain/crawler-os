UPDATE sys_permission
SET type = 0, path = NULL
WHERE code = 'investment';

INSERT INTO sys_permission (name, code, parent_id, type, path, icon, sort)
SELECT menu.name, menu.code, parent.id, 1,
       menu.path, menu.icon, menu.sort
FROM (
    SELECT '投资总览' AS name, 'investment:overview' AS code, '/investment/overview' AS path, 'Odometer' AS icon, 1 AS sort
    UNION ALL
    SELECT '股票模拟交易', 'investment:stock', '/investment/stock', 'TrendCharts', 2
    UNION ALL
    SELECT '场内基金', 'investment:onshore-fund', '/investment/onshore-fund', 'Coin', 3
    UNION ALL
    SELECT '场外基金', 'investment:offshore-fund', '/investment/offshore-fund', 'DataLine', 4
) menu
CROSS JOIN sys_permission parent
WHERE parent.code = 'investment'
  AND NOT EXISTS (SELECT 1 FROM sys_permission existing WHERE existing.code = menu.code);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT parent_role.role_id, child.id
FROM sys_role_permission parent_role
JOIN sys_permission parent ON parent.id = parent_role.permission_id AND parent.code = 'investment'
JOIN sys_permission child ON child.parent_id = parent.id
WHERE child.code IN (
    'investment:overview',
    'investment:stock',
    'investment:onshore-fund',
    'investment:offshore-fund'
);
