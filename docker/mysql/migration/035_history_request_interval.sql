ALTER TABLE history_sync_setting
    ADD COLUMN request_interval_millis INT NOT NULL DEFAULT 1000;
