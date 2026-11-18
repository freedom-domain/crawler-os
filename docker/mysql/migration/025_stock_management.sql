CREATE TABLE IF NOT EXISTS stock_catalog (
    code CHAR(6) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    market VARCHAR(16) NOT NULL,
    industry VARCHAR(128),
    synced_at DATETIME NOT NULL,
    KEY idx_stock_catalog_name (name),
    KEY idx_stock_catalog_market_code (market, code),
    KEY idx_stock_catalog_industry (industry)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS stock_profile (
    code CHAR(6) PRIMARY KEY,
    company_name VARCHAR(255),
    former_names TEXT,
    legal_representative VARCHAR(128),
    registered_capital VARCHAR(128),
    established_date VARCHAR(64),
    province VARCHAR(64),
    employee_count VARCHAR(64),
    main_business MEDIUMTEXT,
    business_scope MEDIUMTEXT,
    company_profile MEDIUMTEXT,
    registered_address VARCHAR(512),
    website VARCHAR(255),
    email VARCHAR(255),
    phone VARCHAR(128),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO sys_permission (name, code, parent_id, type, path, icon, sort)
SELECT '股票管理', 'investment:stock-management', parent.id, 1,
       '/investment/stock-management', 'TrendCharts', 2
FROM sys_permission parent
WHERE parent.code = 'investment'
  AND NOT EXISTS (
      SELECT 1 FROM sys_permission existing WHERE existing.code = 'investment:stock-management'
  );

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT parent_role.role_id, child.id
FROM sys_role_permission parent_role
JOIN sys_permission parent ON parent.id = parent_role.permission_id AND parent.code = 'investment'
JOIN sys_permission child ON child.parent_id = parent.id
WHERE child.code = 'investment:stock-management';
