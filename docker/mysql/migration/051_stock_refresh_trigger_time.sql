ALTER TABLE stock_refresh_setting
    ADD COLUMN trigger_time VARCHAR(5) NOT NULL DEFAULT '18:35' AFTER frequency;
