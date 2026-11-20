package com.collect.ai.rag.dto;

import lombok.Data;

import java.util.List;

public final class RagDtos {

    private RagDtos() {
    }

    @Data
    public static class QueryRequest {
        private String question;
        private Integer topK;
        private Long spiderId;
        private String spiderGroup;
    }

    @Data
    public static class ChatHistoryMessage {
        private String role;
        private String content;
    }

    @Data
    public static class SyncResult {
        private long sourceDocuments;
        private long chunks;
        private long vectorDocuments;
    }

    @Data
    public static class SourceReference {
        private String id;
        private String title;
        private String url;
        private String spiderName;
        private double score;
        private String snippet;
    }

    @Data
    public static class QueryResponse {
        private String answer;
        private List<SourceReference> sources;
        private String model;
        private String vectorIndex;
        private String vectorField;
    }
}
