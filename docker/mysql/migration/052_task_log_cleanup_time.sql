ALTER TABLE spider_task_creation_guard
    ADD COLUMN cleanup_time TIME NOT NULL DEFAULT '03:00:00',
    ADD COLUMN cleanup_last_run_date DATE NULL;
