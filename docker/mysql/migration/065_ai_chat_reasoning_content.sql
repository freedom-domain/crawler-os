ALTER TABLE ai_chat_message
    ADD COLUMN reasoning_content LONGTEXT NULL AFTER content;
