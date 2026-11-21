package com.collect.ai.rag.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

public final class AiKnowledgeBaseDtos {
    private AiKnowledgeBaseDtos() {}

    @Data
    public static class Request {
        private String name;
        private String description;
        private Boolean enabled;
        @JsonSerialize(using = ToStringSerializer.class)
        private Long vectorModelId;
        private String sourceIndex;
        private String vectorIndex;
        private String vectorField;
        private String sourceType;
        private String uploadBucket;
        private Integer sort;
    }

    @Data
    public static class Response {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        private String name;
        private String description;
        private Boolean enabled;
        @JsonSerialize(using = ToStringSerializer.class)
        private Long vectorModelId;
        private String vectorModelName;
        private String sourceIndex;
        private String vectorIndex;
        private String vectorField;
        private String sourceType;
        private String uploadBucket;
        private Integer sort;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }
}
