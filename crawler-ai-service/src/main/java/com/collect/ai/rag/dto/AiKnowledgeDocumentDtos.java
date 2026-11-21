package com.collect.ai.rag.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

public final class AiKnowledgeDocumentDtos {
    private AiKnowledgeDocumentDtos() {}

    @Data
    public static class Response {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        @JsonSerialize(using = ToStringSerializer.class)
        private Long knowledgeBaseId;
        private String title;
        private String originalName;
        private String contentType;
        private Long fileSize;
        private String minioBucket;
        private String objectName;
        private String sourceDocumentId;
        private Boolean vectorized;
        private LocalDateTime createTime;
    }
}
