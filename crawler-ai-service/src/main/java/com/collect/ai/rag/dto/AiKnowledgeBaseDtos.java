package com.collect.ai.rag.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
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
        @JsonSerialize(using = ToStringSerializer.class)
        private Long rerankModelId;

        @JsonSetter("rerankModelId")
        public void setRerankModelId(Object value) {
            if (value == null) {
                this.rerankModelId = null;
                return;
            }
            String text = value.toString().trim();
            if (text.isEmpty()) {
                this.rerankModelId = null;
                return;
            }
            try {
                this.rerankModelId = Long.valueOf(text);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("rerankModelId 必须是有效的模型 ID");
            }
        }
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
        @JsonSerialize(using = ToStringSerializer.class)
        private Long rerankModelId;
        private String rerankModelName;
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
