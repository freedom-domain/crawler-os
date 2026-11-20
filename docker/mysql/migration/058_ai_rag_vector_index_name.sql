ALTER TABLE ai_rag_config
    ADD COLUMN vector_index VARCHAR(255) NULL AFTER chunk_overlap;

UPDATE ai_rag_config
SET vector_index = CONCAT(vector_index_prefix, '_bge_m3')
WHERE vector_index IS NULL;
