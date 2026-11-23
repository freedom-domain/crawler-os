CREATE TABLE IF NOT EXISTS ai_rag_chunk_strategy (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL,
    strategy_type VARCHAR(32) NOT NULL,
    chunk_size INT NOT NULL,
    chunk_overlap INT NOT NULL DEFAULT 0,
    similarity_threshold DECIMAL(4,3) NOT NULL DEFAULT 0.700,
    priority INT NOT NULL DEFAULT 0,
    min_document_length INT NOT NULL DEFAULT 0,
    max_document_length INT NULL,
    document_types VARCHAR(512) NOT NULL DEFAULT '',
    default_strategy TINYINT(1) NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_rag_chunk_strategy_name (name),
    KEY idx_ai_rag_chunk_strategy_default (default_strategy),
    KEY idx_ai_rag_chunk_strategy_priority (priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @ai_rag_chunk_strategy_priority_column_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_rag_chunk_strategy'
       AND column_name = 'priority') = 0,
    'ALTER TABLE ai_rag_chunk_strategy ADD COLUMN priority INT NOT NULL DEFAULT 0 AFTER similarity_threshold',
    'SELECT 1'
);
PREPARE ai_rag_chunk_strategy_priority_column_stmt
    FROM @ai_rag_chunk_strategy_priority_column_sql;
EXECUTE ai_rag_chunk_strategy_priority_column_stmt;
DEALLOCATE PREPARE ai_rag_chunk_strategy_priority_column_stmt;

SET @ai_rag_chunk_strategy_min_length_column_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_rag_chunk_strategy'
       AND column_name = 'min_document_length') = 0,
    'ALTER TABLE ai_rag_chunk_strategy ADD COLUMN min_document_length INT NOT NULL DEFAULT 0 AFTER priority',
    'SELECT 1'
);
PREPARE ai_rag_chunk_strategy_min_length_column_stmt
    FROM @ai_rag_chunk_strategy_min_length_column_sql;
EXECUTE ai_rag_chunk_strategy_min_length_column_stmt;
DEALLOCATE PREPARE ai_rag_chunk_strategy_min_length_column_stmt;

SET @ai_rag_chunk_strategy_max_length_column_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_rag_chunk_strategy'
       AND column_name = 'max_document_length') = 0,
    'ALTER TABLE ai_rag_chunk_strategy ADD COLUMN max_document_length INT NULL AFTER min_document_length',
    'SELECT 1'
);
PREPARE ai_rag_chunk_strategy_max_length_column_stmt
    FROM @ai_rag_chunk_strategy_max_length_column_sql;
EXECUTE ai_rag_chunk_strategy_max_length_column_stmt;
DEALLOCATE PREPARE ai_rag_chunk_strategy_max_length_column_stmt;

SET @ai_rag_chunk_strategy_document_types_column_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_rag_chunk_strategy'
       AND column_name = 'document_types') = 0,
    'ALTER TABLE ai_rag_chunk_strategy ADD COLUMN document_types VARCHAR(512) NOT NULL DEFAULT '''' AFTER max_document_length',
    'SELECT 1'
);
PREPARE ai_rag_chunk_strategy_document_types_column_stmt
    FROM @ai_rag_chunk_strategy_document_types_column_sql;
EXECUTE ai_rag_chunk_strategy_document_types_column_stmt;
DEALLOCATE PREPARE ai_rag_chunk_strategy_document_types_column_stmt;

SET @ai_rag_chunk_strategy_priority_index_sql = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_rag_chunk_strategy'
       AND index_name = 'idx_ai_rag_chunk_strategy_priority') = 0,
    'ALTER TABLE ai_rag_chunk_strategy ADD INDEX idx_ai_rag_chunk_strategy_priority (priority)',
    'SELECT 1'
);
PREPARE ai_rag_chunk_strategy_priority_index_stmt
    FROM @ai_rag_chunk_strategy_priority_index_sql;
EXECUTE ai_rag_chunk_strategy_priority_index_stmt;
DEALLOCATE PREPARE ai_rag_chunk_strategy_priority_index_stmt;

INSERT IGNORE INTO ai_rag_chunk_strategy (
    name, strategy_type, chunk_size, chunk_overlap, similarity_threshold, priority,
    min_document_length, document_types, default_strategy
) VALUES ('默认固定字符', 'FIXED_CHARACTER', 1500, 200, 0.700, 0, 0, '', 1);

SET @ai_rag_vector_model_chunk_strategy_index_sql = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_rag_vector_model'
       AND index_name = 'idx_ai_rag_vector_model_chunk_strategy') > 0,
    'ALTER TABLE ai_rag_vector_model DROP INDEX idx_ai_rag_vector_model_chunk_strategy',
    'SELECT 1'
);
PREPARE ai_rag_vector_model_chunk_strategy_index_stmt
    FROM @ai_rag_vector_model_chunk_strategy_index_sql;
EXECUTE ai_rag_vector_model_chunk_strategy_index_stmt;
DEALLOCATE PREPARE ai_rag_vector_model_chunk_strategy_index_stmt;

SET @ai_rag_vector_model_chunk_strategy_column_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_rag_vector_model'
       AND column_name = 'chunk_strategy_id') > 0,
    'ALTER TABLE ai_rag_vector_model DROP COLUMN chunk_strategy_id',
    'SELECT 1'
);
PREPARE ai_rag_vector_model_chunk_strategy_column_stmt
    FROM @ai_rag_vector_model_chunk_strategy_column_sql;
EXECUTE ai_rag_vector_model_chunk_strategy_column_stmt;
DEALLOCATE PREPARE ai_rag_vector_model_chunk_strategy_column_stmt;

SET @ai_rag_vector_model_chunk_size_column_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_rag_vector_model'
       AND column_name = 'chunk_size') > 0,
    'ALTER TABLE ai_rag_vector_model DROP COLUMN chunk_size',
    'SELECT 1'
);
PREPARE ai_rag_vector_model_chunk_size_column_stmt
    FROM @ai_rag_vector_model_chunk_size_column_sql;
EXECUTE ai_rag_vector_model_chunk_size_column_stmt;
DEALLOCATE PREPARE ai_rag_vector_model_chunk_size_column_stmt;

SET @ai_rag_vector_model_chunk_overlap_column_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_rag_vector_model'
       AND column_name = 'chunk_overlap') > 0,
    'ALTER TABLE ai_rag_vector_model DROP COLUMN chunk_overlap',
    'SELECT 1'
);
PREPARE ai_rag_vector_model_chunk_overlap_column_stmt
    FROM @ai_rag_vector_model_chunk_overlap_column_sql;
EXECUTE ai_rag_vector_model_chunk_overlap_column_stmt;
DEALLOCATE PREPARE ai_rag_vector_model_chunk_overlap_column_stmt;
