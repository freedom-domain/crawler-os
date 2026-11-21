SET @permission_menu_visibility_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'sys_permission'
       AND column_name = 'show_in_menu') = 0,
    'ALTER TABLE sys_permission ADD COLUMN show_in_menu TINYINT NOT NULL DEFAULT 1 COMMENT ''是否显示在侧边菜单'' AFTER sort',
    'SELECT 1'
);
PREPARE permission_menu_visibility_stmt FROM @permission_menu_visibility_sql;
EXECUTE permission_menu_visibility_stmt;
DEALLOCATE PREPARE permission_menu_visibility_stmt;
