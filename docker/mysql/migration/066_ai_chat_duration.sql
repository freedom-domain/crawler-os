ALTER TABLE ai_chat_message
    ADD COLUMN duration_ms BIGINT NULL AFTER reasoning_content;
