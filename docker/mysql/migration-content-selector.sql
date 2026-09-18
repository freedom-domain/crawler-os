-- 为 spider 表新增内容选择器列（已存在的库需手动执行）
ALTER TABLE spider ADD COLUMN content_selector VARCHAR(512) AFTER start_urls;
