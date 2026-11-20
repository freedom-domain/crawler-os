ALTER TABLE ai_rag_vector_model
    ADD COLUMN api_key VARCHAR(1024) NOT NULL DEFAULT '' AFTER embedding_model;
