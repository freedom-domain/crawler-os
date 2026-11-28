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
        @JsonSerialize(using = ToStringSerializer.class)
        private Long roleId;
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
        /** 选择角色后优先生效的系统提示词（角色已停用或提示词为空时回退智能体自身提示词） */
        private String effectiveSystemPrompt;
        @JsonSerialize(using = ToStringSerializer.class)
        private Long roleId;
        private String roleName;
        private Boolean enabled;
        @JsonSerialize(contentUsing = ToStringSerializer.class)
        private List<Long> knowledgeBaseIds;
        private List<String> knowledgeBaseNames;
        private Integer sort;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }
}
