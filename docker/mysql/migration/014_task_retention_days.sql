ALTER TABLE spider_task_creation_guard
    ADD COLUMN retention_days INT NOT NULL DEFAULT 30;
