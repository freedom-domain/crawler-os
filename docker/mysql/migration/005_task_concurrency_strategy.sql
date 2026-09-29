ALTER TABLE spider_task_creation_guard
    ADD COLUMN max_concurrency INT NOT NULL DEFAULT 1;
