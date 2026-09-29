CREATE TABLE IF NOT EXISTS spider_task_creation_guard (
    id TINYINT PRIMARY KEY
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO spider_task_creation_guard (id) VALUES (1);

ALTER TABLE spider_task
    ADD COLUMN task_message LONGTEXT NULL;
