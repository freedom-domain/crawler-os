CREATE TABLE IF NOT EXISTS image_preview_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    content_id VARCHAR(128) NOT NULL,
    title TEXT,
    url TEXT,
    spider_name VARCHAR(255),
    image_count INT NOT NULL DEFAULT 0,
    deleted TINYINT DEFAULT 0,
    create_time DATETIME,
    update_time DATETIME,
    KEY idx_image_preview_user_time (user_id, deleted, create_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO sys_permission (id, name, code, parent_id, type, path, icon, sort)
SELECT COALESCE(MAX(id), 0) + 1, '图片预览记录', 'image-history', 0, 1, '/image-history', 'Picture', 7
FROM sys_permission
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'image-history');

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT srp.role_id, sp.id
FROM sys_role_permission srp
JOIN sys_permission search_permission ON search_permission.id = srp.permission_id
JOIN sys_permission sp ON sp.code = 'image-history'
WHERE search_permission.code = 'search:query'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission existing
    WHERE existing.role_id = srp.role_id AND existing.permission_id = sp.id
  );
