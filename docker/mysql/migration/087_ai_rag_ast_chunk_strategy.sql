-- AST（代码结构）分块策略：按声明边界（类/方法/函数）切分代码文件，
-- package/import 等前言作为上下文头前置到每个 chunk，使分块自包含。
-- 匹配按文档扩展名触发，priority=10 优先于默认固定字符策略。
INSERT IGNORE INTO ai_rag_chunk_strategy (
    name, strategy_type, chunk_size, chunk_overlap, similarity_threshold, priority,
    min_document_length, max_document_length, document_types, default_strategy
) VALUES (
    '代码分块', 'AST', 1500, 150, 0.700, 10,
    0, NULL,
    'java,py,ts,tsx,js,jsx,go,rs,c,cpp,h,cs,sql,xml,yml,yaml,json,sh,properties,cfg',
    0
);
