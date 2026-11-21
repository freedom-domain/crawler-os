ALTER TABLE ai_chat_message
    ADD COLUMN wait_duration_ms BIGINT NULL AFTER duration_ms;
