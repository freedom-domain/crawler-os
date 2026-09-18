CREATE DATABASE IF NOT EXISTS crawler_platform DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE crawler_platform;

-- 角色
CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL,
    code VARCHAR(64) NOT NULL,
    description VARCHAR(255),
    status TINYINT DEFAULT 1,
    deleted TINYINT DEFAULT 0,
    create_time DATETIME,
    update_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 用户
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL UNIQUE,
    password VARCHAR(128) NOT NULL,
    nickname VARCHAR(64),
    email VARCHAR(128),
    phone VARCHAR(32),
    status TINYINT DEFAULT 1,
    role_id BIGINT,
    deleted TINYINT DEFAULT 0,
    create_time DATETIME,
    update_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 权限
CREATE TABLE IF NOT EXISTS sys_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL,
    code VARCHAR(128) NOT NULL,
    parent_id BIGINT DEFAULT 0,
    type TINYINT DEFAULT 1,
    path VARCHAR(255),
    sort INT DEFAULT 0,
    deleted TINYINT DEFAULT 0,
    create_time DATETIME,
    update_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 角色-权限
CREATE TABLE IF NOT EXISTS sys_role_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 爬虫
CREATE TABLE IF NOT EXISTS spider (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    type VARCHAR(32) DEFAULT 'http',
    start_urls TEXT,
    selectors TEXT,
    image_selector VARCHAR(512),
    overwrite_html TINYINT DEFAULT 0,
    overwrite_image TINYINT DEFAULT 0,
    group VARCHAR(64),
    schedule VARCHAR(64),
    max_depth INT DEFAULT 2,
    timeout INT DEFAULT 15000,
    headers TEXT,
    enabled TINYINT DEFAULT 1,
    creator_id BIGINT,
    status TINYINT DEFAULT 0,
    deleted TINYINT DEFAULT 0,
    create_time DATETIME,
    update_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 爬虫任务
CREATE TABLE IF NOT EXISTS spider_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    spider_id BIGINT NOT NULL,
    spider_name VARCHAR(128),
    task_id VARCHAR(64),
    status VARCHAR(32) DEFAULT 'PENDING',
    start_time DATETIME,
    end_time DATETIME,
    success_count INT DEFAULT 0,
    fail_count INT DEFAULT 0,
    error_message TEXT,
    deleted TINYINT DEFAULT 0,
    create_time DATETIME,
    update_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 任务日志
CREATE TABLE IF NOT EXISTS spider_task_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    spider_id BIGINT,
    url VARCHAR(1024),
    status TINYINT,
    level VARCHAR(16),
    message VARCHAR(1024),
    cost_ms INT,
    deleted TINYINT DEFAULT 0,
    create_time DATETIME,
    update_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 字典（标签，支持父子项）
CREATE TABLE IF NOT EXISTS sys_dict (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    parent_id BIGINT DEFAULT 0,
    label VARCHAR(128) NOT NULL,
    value VARCHAR(128),
    sort INT DEFAULT 0,
    status TINYINT DEFAULT 1,
    deleted TINYINT DEFAULT 0,
    create_time DATETIME,
    update_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 文件元数据
CREATE TABLE IF NOT EXISTS file_metadata (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    bucket VARCHAR(64),
    object_name VARCHAR(512),
    file_name VARCHAR(255),
    content_type VARCHAR(128),
    file_size BIGINT,
    category VARCHAR(32),
    spider_id BIGINT,
    deleted TINYINT DEFAULT 0,
    create_time DATETIME,
    update_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 初始化角色
INSERT INTO sys_role (id, name, code, description, status) VALUES
(1, '管理员', 'admin', '系统管理员', 1),
(2, '普通用户', 'user', '普通用户', 1);

-- 初始化权限
INSERT INTO sys_permission (id, name, code, parent_id, type, path, sort) VALUES
(1, '用户管理', 'user', 0, 1, '/user', 1),
(2, '爬虫管理', 'spider', 0, 1, '/spider', 2),
(3, '任务管理', 'task', 0, 1, '/task', 3),
(4, '数据搜索', 'search', 0, 1, '/search', 4),
(5, '文件管理', 'file', 0, 1, '/file', 5),
(6, '查看用户', 'user:list', 1, 2, NULL, 1),
(7, '创建用户', 'user:create', 1, 2, NULL, 2),
(8, '查看爬虫', 'spider:list', 2, 2, NULL, 1),
(9, '创建爬虫', 'spider:create', 2, 2, NULL, 2),
(10, '执行爬虫', 'spider:run', 2, 2, NULL, 3),
(11, '搜索数据', 'search:query', 4, 2, NULL, 1),
(12, '角色管理', 'role', 0, 1, '/role', 6),
(13, '权限管理', 'permission', 0, 1, '/permission', 7),
(14, '字典管理', 'dict', 0, 1, '/dict', 8);

-- 角色权限绑定（管理员拥有全部权限）
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
(1, 1),(1, 2),(1, 3),(1, 4),(1, 5),
(1, 6),(1, 7),(1, 8),(1, 9),(1, 10),(1, 11),
(1, 12),(1, 13),(1, 14);

-- 默认管理员 (密码: admin123, MD5)
INSERT INTO sys_user (id, username, password, nickname, status, role_id) VALUES
(1, 'admin', 'e10adc3949ba59abbe56e057f20f883e', '管理员', 1, 1),
(2, 'user', 'e10adc3949ba59abbe56e057f20f883e', '普通用户', 1, 2);
