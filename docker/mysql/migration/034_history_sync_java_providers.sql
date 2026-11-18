UPDATE history_sync_setting
SET enabled_apis = CASE
        WHEN FIND_IN_SET('EASTMONEY', enabled_apis) > 0 THEN 'TENCENT,EASTMONEY'
        ELSE 'TENCENT'
    END
WHERE enabled_apis IS NULL
   OR TRIM(enabled_apis) = ''
   OR enabled_apis REGEXP '(^|,)(BAOSTOCK|AKSHARE)(,|$)';
