ALTER TABLE spider_task_creation_guard
    ADD COLUMN url_concurrency INT NOT NULL DEFAULT 8;
