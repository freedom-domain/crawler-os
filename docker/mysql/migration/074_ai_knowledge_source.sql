SET @ai_knowledge_source_type_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_knowledge_base'
       AND column_name = 'source_type') = 0,
    'ALTER TABLE ai_knowledge_base ADD COLUMN source_type VARCHAR(16) NOT NULL DEFAULT ''ES_INDEX'' AFTER vector_field',
    'SELECT 1'
);
PREPARE ai_knowledge_source_type_stmt FROM @ai_knowledge_source_type_sql;
EXECUTE ai_knowledge_source_type_stmt;
DEALLOCATE PREPARE ai_knowledge_source_type_stmt;

SET @ai_knowledge_upload_bucket_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_knowledge_base'
       AND column_name = 'upload_bucket') = 0,
    'ALTER TABLE ai_knowledge_base ADD COLUMN upload_bucket VARCHAR(128) NOT NULL DEFAULT ''crawler-knowledge'' AFTER source_type',
    'SELECT 1'
);
PREPARE ai_knowledge_upload_bucket_stmt FROM @ai_knowledge_upload_bucket_sql;
EXECUTE ai_knowledge_upload_bucket_stmt;
DEALLOCATE PREPARE ai_knowledge_upload_bucket_stmt;

CREATE TABLE IF NOT EXISTS ai_knowledge_document (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    knowledge_base_id BIGINT NOT NULL,
    title VARCHAR(512) NOT NULL DEFAULT '',
    original_name VARCHAR(512) NOT NULL,
    content_type VARCHAR(128) NOT NULL DEFAULT '',
    file_size BIGINT NOT NULL DEFAULT 0,
    minio_bucket VARCHAR(128) NOT NULL,
    object_name VARCHAR(1024) NOT NULL,
    source_document_id VARCHAR(512) NOT NULL,
    vectorized TINYINT(1) NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ai_knowledge_document_object (minio_bucket, object_name(512)),
    KEY idx_ai_knowledge_document_base (knowledge_base_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
