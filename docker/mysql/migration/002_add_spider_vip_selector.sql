ALTER TABLE spider
    ADD COLUMN vip_selector VARCHAR(512) NULL AFTER image_selector,
    ADD COLUMN vip_selector_content VARCHAR(512) NULL AFTER vip_selector;
