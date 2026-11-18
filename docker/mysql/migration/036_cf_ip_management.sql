CREATE TABLE IF NOT EXISTS cf_ip_candidate (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    ip_address VARCHAR(45) NOT NULL,
    region VARCHAR(64) NOT NULL DEFAULT '全球任播',
    source_range_id BIGINT,
    latency_ms INT,
    ping_ms DECIMAL(9,3),
    tls_ms DECIMAL(9,3),
    speed_mbps DECIMAL(10,3),
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    error_message VARCHAR(255),
    last_tested_at DATETIME,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_cf_ip_candidate_address (ip_address),
    KEY idx_cf_ip_candidate_latency (status, latency_ms)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cf_ip_range (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    cidr VARCHAR(64) NOT NULL,
    ip_version TINYINT NOT NULL,
    region VARCHAR(64) NOT NULL DEFAULT '全球任播',
    active TINYINT NOT NULL DEFAULT 1,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_cf_ip_range_cidr (cidr),
    KEY idx_cf_ip_range_active_version (active, ip_version, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO sys_permission (name, code, parent_id, type, path, icon, sort)
SELECT '系统管理', 'system', 0, 0, NULL, 'Setting', 6
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission existing WHERE existing.code = 'system'
);

UPDATE sys_permission
SET deleted = 0
WHERE code = 'system';

INSERT INTO sys_permission (name, code, parent_id, type, path, icon, sort)
SELECT 'CF IP 优选', 'cf-ip:manage', parent.id, 1,
       '/cf-ip-management', 'Connection', 4
FROM sys_permission parent
WHERE parent.code = 'system'
  AND parent.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_permission existing WHERE existing.code = 'cf-ip:manage'
  );

UPDATE sys_permission menu
JOIN sys_permission parent ON parent.code = 'system' AND parent.deleted = 0
SET menu.name = 'CF IP 优选',
    menu.parent_id = parent.id,
    menu.type = 1,
    menu.path = '/cf-ip-management',
    menu.icon = 'Connection',
    menu.sort = 4,
    menu.deleted = 0
WHERE menu.code = 'cf-ip:manage';

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, permission.id
FROM sys_role r
JOIN sys_permission permission ON permission.code = 'cf-ip:manage'
WHERE r.code = 'admin';
