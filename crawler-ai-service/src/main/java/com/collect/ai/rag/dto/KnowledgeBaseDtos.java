package com.collect.ai.rag.dto;

import lombok.Data;

import java.util.List;

public final class KnowledgeBaseDtos {

    private KnowledgeBaseDtos() {
    }

    @Data
    public static class Document {
        private String id;
        private String title;
        private String snippet;
        private String url;
        private String spiderName;
        private Long spiderId;
        private boolean excluded;
        private boolean vectorized;
        private long chunkCount;
    }

    @Data
    public static class DocumentDetail {
        private String id;
        private String title;
        private String content;
        private String url;
        private String spiderName;
        private Long spiderId;
        private List<String> images;
        private boolean excluded;
    }

    @Data
    public static class DocumentPage {
        private List<Document> records;
        private long total;
        private int page;
        private int pageSize;
    }

    @Data
    public static class VectorChunk {
        private String chunkId;
        private int index;
        private String title;
        private String content;
        private int characterCount;
        private String embeddingModel;
        private String chunkStrategyId;
        private String chunkStrategyName;
    }

    @Data
    public static class Stats {
        private String sourceIndex;
        private String vectorIndex;
        private long sourceDocuments;
        private long includedDocuments;
        private long excludedDocuments;
        private long vectorDocuments;
    }
}
