CREATE TABLE IF NOT EXISTS ai_rag_excluded_document (
    id BIGINT NOT NULL AUTO_INCREMENT,
    source_index VARCHAR(255) NOT NULL,
    document_id VARCHAR(512) NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_rag_excluded_source_document (source_index, document_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
