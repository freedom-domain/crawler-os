ALTER TABLE ai_chat_message
    ADD COLUMN thinking_duration_ms BIGINT NULL AFTER duration_ms,
    ADD COLUMN generation_duration_ms BIGINT NULL AFTER thinking_duration_ms;
