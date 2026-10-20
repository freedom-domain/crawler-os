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
    update_time DATETIME,
    KEY idx_user_query (deleted, create_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 权限
CREATE TABLE IF NOT EXISTS sys_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL,
    code VARCHAR(128) NOT NULL,
    parent_id BIGINT DEFAULT 0,
    type TINYINT DEFAULT 1 COMMENT '类型: 0=目录, 1=菜单, 2=按钮',
    path VARCHAR(255),
    icon VARCHAR(64),
    sort INT DEFAULT 0,
    deleted TINYINT DEFAULT 0,
    create_time DATETIME,
    update_time DATETIME,
    KEY idx_permission_tree (deleted, parent_id, type, sort, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 角色-权限
CREATE TABLE IF NOT EXISTS sys_role_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    UNIQUE KEY uk_role_permission (role_id, permission_id),
    KEY idx_role_permission_permission (permission_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 爬虫
CREATE TABLE IF NOT EXISTS spider (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    type VARCHAR(32) DEFAULT 'http',
    start_urls TEXT,
    content_selector VARCHAR(512),
    image_selector VARCHAR(512),
    image_xpath VARCHAR(512),
    vip_selector VARCHAR(512),
    vip_selector_content VARCHAR(512),
    overwrite_html TINYINT DEFAULT 0,
    force_overwrite_html TINYINT NOT NULL DEFAULT 0,
    overwrite_image TINYINT DEFAULT 0,
    read_cache TINYINT NOT NULL DEFAULT 0,
    read_cache_miss_online TINYINT NOT NULL DEFAULT 0,
    is_public TINYINT NOT NULL DEFAULT 0,
    group VARCHAR(64),
    schedule VARCHAR(64),
    max_depth INT DEFAULT 2,
    timeout INT DEFAULT 15000,
    headers TEXT,
    image_headers TEXT,
    excluded_urls TEXT,
    follow_robots TINYINT DEFAULT 0,
    skip_tls_verify TINYINT NOT NULL DEFAULT 0,
    enabled TINYINT DEFAULT 1,
    creator_id BIGINT,
    status TINYINT DEFAULT 0,
    deleted TINYINT DEFAULT 0,
    active_name VARCHAR(128) GENERATED ALWAYS AS (IF(COALESCE(deleted, 0) = 0, name, NULL)) STORED,
    create_time DATETIME,
    update_time DATETIME,
    UNIQUE KEY uk_spider_active_name (active_name),
    KEY idx_spider_query (deleted, `group`, create_time, id),
    KEY idx_spider_schedule (deleted, enabled, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 爬虫任务
CREATE TABLE IF NOT EXISTS spider_task_creation_guard (
    id TINYINT PRIMARY KEY,
    max_concurrency INT NOT NULL DEFAULT 1,
    url_concurrency INT NOT NULL DEFAULT 8,
    retention_days INT NOT NULL DEFAULT 30
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO spider_task_creation_guard (id) VALUES (1);

CREATE TABLE IF NOT EXISTS spider_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    spider_id BIGINT NOT NULL,
    spider_name VARCHAR(128),
    task_id VARCHAR(64),
    task_message LONGTEXT,
    status VARCHAR(32) DEFAULT 'PENDING',
    start_time DATETIME(3),
    end_time DATETIME(3),
    total_cost_ms BIGINT DEFAULT 0,
    success_count INT DEFAULT 0,
    fail_count INT DEFAULT 0,
    error_message TEXT,
    paused_at DATETIME NULL,
    deleted TINYINT DEFAULT 0,
    create_time DATETIME,
    update_time DATETIME,
    KEY idx_spider_task_query (deleted, spider_id, status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 任务日志
CREATE TABLE IF NOT EXISTS spider_task_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    spider_id BIGINT,
    url VARCHAR(1024),
    status TINYINT,
    level VARCHAR(16),
    type VARCHAR(16) DEFAULT 'html',
    message VARCHAR(1024),
    cost_ms INT,
    deleted TINYINT DEFAULT 0,
    create_time DATETIME,
    update_time DATETIME,
    KEY idx_spider_task_log_stats (task_id, deleted, type, status),
    KEY idx_spider_task_log_page (task_id, deleted, create_time)
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
    update_time DATETIME,
    KEY idx_dict_tree (deleted, status, parent_id, sort, id),
    KEY idx_dict_value (deleted, status, value)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 文件元数据
CREATE TABLE IF NOT EXISTS file_metadata (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    bucket VARCHAR(64),
    object_name VARCHAR(512),
    file_name VARCHAR(255),
    title VARCHAR(512),
    content_type VARCHAR(128),
    file_size BIGINT,
    category VARCHAR(32),
    spider_id BIGINT,
    source VARCHAR(1024),
    deleted TINYINT DEFAULT 0,
    create_time DATETIME,
    update_time DATETIME,
    KEY idx_file_query (deleted, category, spider_id, create_time, id),
    KEY idx_file_object (deleted, object_name(191)),
    UNIQUE KEY uk_file_source (source(768))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 用户收藏及标签
CREATE TABLE IF NOT EXISTS user_favorite (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    content_id VARCHAR(128) NOT NULL,
    tags TEXT,
    deleted TINYINT DEFAULT 0,
    create_time DATETIME,
    update_time DATETIME,
    UNIQUE KEY uk_user_content (user_id, content_id),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 用户搜索历史
CREATE TABLE IF NOT EXISTS search_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    keyword VARCHAR(512) NOT NULL,
    deleted TINYINT DEFAULT 0,
    create_time DATETIME,
    update_time DATETIME,
    KEY idx_search_history_user_time (user_id, deleted, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 用户图片预览记录
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

CREATE TABLE IF NOT EXISTS paper_investment_account (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    initial_cash DECIMAL(18,2) NOT NULL,
    cash_balance DECIMAL(18,2) NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME,
    update_time DATETIME,
    UNIQUE KEY uk_paper_investment_user (user_id),
    KEY idx_paper_investment_account_deleted (deleted, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS paper_investment_holding (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    code CHAR(6) NOT NULL,
    name VARCHAR(128) NOT NULL,
    market VARCHAR(16) NOT NULL,
    instrument_type VARCHAR(16) NOT NULL,
    quantity INT NOT NULL DEFAULT 0,
    available_quantity INT NOT NULL DEFAULT 0,
    average_cost DECIMAL(18,6) NOT NULL DEFAULT 0,
    settled_date DATE,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME,
    update_time DATETIME,
    UNIQUE KEY uk_paper_investment_holding (user_id, code),
    KEY idx_paper_investment_holding_user (user_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS paper_investment_trade (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    code CHAR(6) NOT NULL,
    name VARCHAR(128) NOT NULL,
    market VARCHAR(16) NOT NULL,
    instrument_type VARCHAR(16) NOT NULL,
    side VARCHAR(4) NOT NULL,
    quantity INT NOT NULL,
    price DECIMAL(18,4) NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    commission DECIMAL(18,2) NOT NULL,
    stamp_tax DECIMAL(18,2) NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME,
    update_time DATETIME,
    KEY idx_paper_investment_trade_user_time (user_id, deleted, create_time, id),
    KEY idx_paper_investment_trade_code (user_id, code, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS fund_history_cache (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    fund_type VARCHAR(16) NOT NULL,
    code CHAR(6) NOT NULL,
    frequency VARCHAR(8) NOT NULL,
    trade_date DATE NOT NULL,
    name VARCHAR(128) NOT NULL,
    open DECIMAL(20,8),
    close DECIMAL(20,8),
    high DECIMAL(20,8),
    low DECIMAL(20,8),
    volume DECIMAL(28,6),
    change_percent DECIMAL(12,6),
    unit_nav DECIMAL(20,8),
    cumulative_nav DECIMAL(20,8),
    UNIQUE KEY uk_fund_history_cache (fund_type, code, frequency, trade_date),
    KEY idx_fund_history_cache_date (code, fund_type, frequency, trade_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS stock_history_cache (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code CHAR(6) NOT NULL,
    trade_date DATE NOT NULL,
    name VARCHAR(128) NOT NULL,
    open DECIMAL(20,8),
    close DECIMAL(20,8),
    high DECIMAL(20,8),
    low DECIMAL(20,8),
    volume DECIMAL(28,6),
    change_percent DECIMAL(12,6),
    UNIQUE KEY uk_stock_history_cache (code, trade_date),
    KEY idx_stock_history_cache_date (code, trade_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS stock_watchlist (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code CHAR(6) NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_stock_watchlist_code (code),
    KEY idx_stock_watchlist_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS stock_refresh_setting (
    id TINYINT UNSIGNED PRIMARY KEY,
    frequency VARCHAR(16) NOT NULL DEFAULT 'DAILY',
    last_run_slot VARCHAR(32),
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO stock_refresh_setting (id, frequency) VALUES (1, 'DAILY');

CREATE TABLE IF NOT EXISTS cf_ip_candidate (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    ip_address VARCHAR(45) NOT NULL,
    region VARCHAR(64) NOT NULL DEFAULT '全球任播',
    colo VARCHAR(3),
    colo_updated_at DATETIME,
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
    colo VARCHAR(3),
    active TINYINT NOT NULL DEFAULT 1,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_cf_ip_range_cidr (cidr),
    KEY idx_cf_ip_range_active_version (active, ip_version, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS history_sync_setting (
    id TINYINT PRIMARY KEY,
    max_concurrent_requests INT NOT NULL DEFAULT 1,
    enabled_apis VARCHAR(128) NOT NULL DEFAULT 'TENCENT',
    request_interval_millis INT NOT NULL DEFAULT 1000,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO history_sync_setting (id, max_concurrent_requests, enabled_apis, request_interval_millis)
VALUES (1, 1, 'TENCENT', 1000);

CREATE TABLE IF NOT EXISTS history_sync_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    instrument_type VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    total_count INT NOT NULL DEFAULT 0,
    finished_count INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    message VARCHAR(2000),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at DATETIME,
    completed_at DATETIME,
    KEY idx_history_sync_task_type_created (instrument_type, created_at),
    KEY idx_history_sync_task_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS history_sync_task_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    instrument_type VARCHAR(16) NOT NULL,
    fund_type VARCHAR(16),
    code CHAR(6) NOT NULL,
    name VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    message VARCHAR(2000),
    point_count INT,
    started_at DATETIME,
    finished_at DATETIME,
    KEY idx_history_sync_item_task_status (task_id, status),
    KEY idx_history_sync_item_instrument (instrument_type, fund_type, code),
    CONSTRAINT fk_history_sync_item_task
        FOREIGN KEY (task_id) REFERENCES history_sync_task (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS fund_watchlist (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    fund_type VARCHAR(16) NOT NULL,
    code CHAR(6) NOT NULL,
    frequency VARCHAR(8) NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_fund_watchlist (fund_type, code, frequency),
    KEY idx_fund_watchlist_code (code, fund_type, frequency)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS fund_refresh_setting (
    id TINYINT UNSIGNED PRIMARY KEY,
    frequency VARCHAR(16) NOT NULL DEFAULT 'DAILY',
    last_run_slot VARCHAR(32),
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO fund_refresh_setting (id, frequency) VALUES (1, 'DAILY');

CREATE TABLE IF NOT EXISTS fund_catalog (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    fund_type VARCHAR(16) NOT NULL,
    code CHAR(6) NOT NULL,
    name VARCHAR(128) NOT NULL,
    category VARCHAR(64),
    pinyin VARCHAR(128),
    synced_at DATETIME NOT NULL,
    UNIQUE KEY uk_fund_catalog (fund_type, code),
    KEY idx_fund_catalog_name (name, code),
    KEY idx_fund_catalog_type_code (fund_type, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS fund_profile (
    fund_type VARCHAR(16) NOT NULL,
    code CHAR(6) NOT NULL,
    fund_full_name VARCHAR(255),
    issue_date VARCHAR(64),
    established_date VARCHAR(64),
    establishment_scale VARCHAR(128),
    net_asset_scale VARCHAR(128),
    net_asset_scale_as_of VARCHAR(64),
    share_scale VARCHAR(128),
    share_scale_as_of VARCHAR(64),
    management_company VARCHAR(255),
    custodian VARCHAR(255),
    manager_names TEXT,
    distribution_since_launch VARCHAR(255),
    management_fee_rate VARCHAR(128),
    custodian_fee_rate VARCHAR(128),
    service_fee_rate VARCHAR(128),
    max_subscription_fee_rate VARCHAR(128),
    max_purchase_fee_rate VARCHAR(128),
    max_redemption_fee_rate VARCHAR(128),
    benchmark TEXT,
    tracking_target VARCHAR(255),
    investment_objective MEDIUMTEXT,
    investment_philosophy MEDIUMTEXT,
    investment_scope MEDIUMTEXT,
    investment_strategy MEDIUMTEXT,
    dividend_policy MEDIUMTEXT,
    risk_return_feature MEDIUMTEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (fund_type, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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

-- 初始化角色
INSERT INTO sys_role (id, name, code, description, status) VALUES
(1, '管理员', 'admin', '系统管理员', 1),
(2, '普通用户', 'user', '普通用户', 1);

-- 初始化权限
INSERT INTO sys_permission (id, name, code, parent_id, type, path, icon, sort) VALUES
(1, '用户管理', 'user', 0, 1, '/user', 'User', 1),
(2, '爬虫管理', 'spider', 0, 1, '/spider', 'Connection', 2),
(3, '任务管理', 'task', 0, 1, '/task', 'List', 3),
(4, '数据搜索', 'search', 0, 1, '/search', 'Search', 4),
(5, '文件管理', 'file', 0, 1, '/file', 'Folder', 5),
(6, '查看用户', 'user:list', 1, 2, NULL, 1),
(7, '创建用户', 'user:create', 1, 2, NULL, 2),
(8, '查看爬虫', 'spider:list', 2, 2, NULL, 1),
(9, '创建爬虫', 'spider:create', 2, 2, NULL, 2),
(10, '执行爬虫', 'spider:run', 2, 2, NULL, 3),
(11, '搜索数据', 'search:query', 4, 2, NULL, 1),
(12, '系统管理', 'system', 0, 0, NULL, 'Setting', 6),
(13, '角色管理', 'role', 12, 1, '/role', 'Avatar', 1),
(14, '权限管理', 'permission', 12, 1, '/permission', 'Lock', 2),
(15, '字典管理', 'dict', 12, 1, '/dict', 'PriceTag', 3),
(16, '我的收藏', 'favorite', 0, 1, '/favorite', 'Folder', 6),
(17, '图片预览记录', 'image-history', 0, 1, '/image-history', 'Picture', 7),
(18, '模拟投资', 'investment', 0, 0, NULL, 'TrendCharts', 8),
(23, '基金管理', 'investment:fund-management', 18, 1, '/investment/fund-management', 'Coin', 3),
(24, '股票管理', 'investment:stock-management', 18, 1, '/investment/stock-management', 'TrendCharts', 2),
(25, 'CF IP 优选', 'cf-ip:manage', 12, 1, '/cf-ip-management', 'Connection', 4);

-- 角色权限绑定（管理员拥有全部权限）
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
(1, 1),(1, 2),(1, 3),(1, 4),(1, 5),
(1, 6),(1, 7),(1, 8),(1, 9),(1, 10),(1, 11),
(1, 12),(1, 13),(1, 14),(1, 15),(1, 16),(1, 17),(1, 18),(1, 23),(1, 24),
(1, 25),
(2, 18),(2, 23),(2, 24);

-- 默认管理员 (密码: admin123, MD5)
INSERT INTO sys_user (id, username, password, nickname, status, role_id) VALUES
(1, 'admin', 'e10adc3949ba59abbe56e057f20f883e', '管理员', 1, 1),
(2, 'user', 'e10adc3949ba59abbe56e057f20f883e', '普通用户', 1, 2);
