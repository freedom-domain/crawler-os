ALTER TABLE spider
    ADD COLUMN force_overwrite_html TINYINT NOT NULL DEFAULT 0 AFTER overwrite_html;
