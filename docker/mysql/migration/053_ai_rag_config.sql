CREATE TABLE IF NOT EXISTS ai_rag_config (
    environment VARCHAR(32) NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 0,
    source_index VARCHAR(255) NOT NULL,
    embedding_provider VARCHAR(32) NOT NULL DEFAULT 'ollama',
    embedding_base_url VARCHAR(512) NOT NULL,
    embedding_model VARCHAR(128) NOT NULL,
    dimensions INT NOT NULL DEFAULT 1024,
    chat_provider VARCHAR(32) NOT NULL DEFAULT 'openai',
    chat_base_url VARCHAR(512) NOT NULL,
    chat_model VARCHAR(128) NOT NULL,
    api_key VARCHAR(1024) NOT NULL DEFAULT '',
    top_k INT NOT NULL DEFAULT 5,
    chunk_size INT NOT NULL DEFAULT 1500,
    chunk_overlap INT NOT NULL DEFAULT 200,
    vector_index_prefix VARCHAR(255) NOT NULL,
    vector_field_prefix VARCHAR(128) NOT NULL DEFAULT 'embedding',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (environment)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO ai_rag_config (
    environment, enabled, source_index, embedding_provider, embedding_base_url,
    embedding_model, dimensions, chat_provider, chat_base_url, chat_model,
    api_key, top_k, chunk_size, chunk_overlap, vector_index_prefix, vector_field_prefix
) VALUES
    ('default', 0, 'spider_content', 'ollama', 'http://192.168.31.2:11434',
     'bge-m3', 1024, 'openai', 'http://192.168.31.2:8099/v1', 'Qwen3.8-27B',
     '', 5, 1500, 200, 'spider_content_vector', 'embedding'),
    ('local', 0, 'spider_content_local', 'ollama', 'http://192.168.31.2:11434',
     'bge-m3', 1024, 'openai', 'http://192.168.31.2:8099/v1', 'Qwen3.8-27B',
     '', 5, 1500, 200, 'spider_content_vector_local', 'embedding');
