package com.collect.ai.rag.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

public final class AiAssistantDtos {
    private AiAssistantDtos() {}

    @Data
    public static class Request {
        private String name;
        private String welcomeMessage;
        private String description;
        private String avatar;
        private String systemPrompt;
        private Boolean enabled;
        @JsonSerialize(contentUsing = ToStringSerializer.class)
        private List<Long> knowledgeBaseIds;
        private Integer sort;
    }

    @Data
    public static class Response {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        private String name;
        private String welcomeMessage;
        private String description;
        private String avatar;
        private String systemPrompt;
        private Boolean enabled;
        @JsonSerialize(contentUsing = ToStringSerializer.class)
        private List<Long> knowledgeBaseIds;
        private List<String> knowledgeBaseNames;
        private Integer sort;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }
}
