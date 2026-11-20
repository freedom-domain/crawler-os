ALTER TABLE ai_rag_chat_model
    ADD COLUMN context_length INT NOT NULL DEFAULT 8192 AFTER chat_model;
