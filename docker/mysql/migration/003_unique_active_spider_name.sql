ALTER TABLE spider
    ADD COLUMN active_name VARCHAR(128)
        GENERATED ALWAYS AS (IF(COALESCE(deleted, 0) = 0, name, NULL)) STORED,
    ADD UNIQUE KEY uk_spider_active_name (active_name);
