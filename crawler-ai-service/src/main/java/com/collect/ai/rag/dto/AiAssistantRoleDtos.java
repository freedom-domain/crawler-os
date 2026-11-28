package com.collect.ai.rag.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

public final class AiAssistantRoleDtos {
    private AiAssistantRoleDtos() {}

    @Data
    public static class Request {
        private String name;
        private String description;
        private String systemPrompt;
        private Boolean enabled;
        private Integer sort;
    }

    @Data
    public static class Response {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        private String name;
        private String description;
        private String systemPrompt;
        private Boolean enabled;
        private Integer sort;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }
}
