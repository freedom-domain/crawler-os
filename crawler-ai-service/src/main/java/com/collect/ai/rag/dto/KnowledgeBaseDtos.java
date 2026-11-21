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
    }

    @Data
    public static class DocumentDetail {
        private String id;
        private String title;
        private String content;
        private String url;
        private String spiderName;
        private Long spiderId;
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
    public static class Stats {
        private boolean enabled;
        private String sourceIndex;
        private String vectorIndex;
        private long sourceDocuments;
        private long includedDocuments;
        private long excludedDocuments;
        private long vectorDocuments;
    }
}
