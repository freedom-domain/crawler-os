-- 为已有数据库补充任务总耗时字段，并保留任务起止时间的毫秒精度
ALTER TABLE spider_task
    ADD COLUMN total_cost_ms BIGINT DEFAULT 0 AFTER end_time,
    MODIFY COLUMN start_time DATETIME(3),
    MODIFY COLUMN end_time DATETIME(3);