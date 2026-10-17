CREATE TABLE IF NOT EXISTS fund_watchlist (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    fund_type VARCHAR(16) NOT NULL,
    code CHAR(6) NOT NULL,
    frequency VARCHAR(8) NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_fund_watchlist (fund_type, code, frequency),
    KEY idx_fund_watchlist_code (code, fund_type, frequency)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO sys_permission (name, code, parent_id, type, path, icon, sort)
SELECT '基金管理', 'investment:fund-management', parent.id, 1,
       '/investment/fund-management', 'Coin', 4
FROM sys_permission parent
WHERE parent.code = 'investment'
  AND NOT EXISTS (
      SELECT 1 FROM sys_permission existing WHERE existing.code = 'investment:fund-management'
  );

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT parent_role.role_id, child.id
FROM sys_role_permission parent_role
JOIN sys_permission parent ON parent.id = parent_role.permission_id AND parent.code = 'investment'
JOIN sys_permission child ON child.parent_id = parent.id
WHERE child.code = 'investment:fund-management';
