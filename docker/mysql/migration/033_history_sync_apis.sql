ALTER TABLE history_sync_setting
    ADD COLUMN enabled_apis VARCHAR(128) NOT NULL DEFAULT 'TENCENT';
