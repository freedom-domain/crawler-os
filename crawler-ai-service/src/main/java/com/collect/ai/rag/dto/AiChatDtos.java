package com.collect.ai.rag.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

public final class AiChatDtos {

    private AiChatDtos() {
    }

    @Data
    public static class ConversationResponse {
        private Long id;
        private String title;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    public static class SendMessageRequest {
        private String question;
        private Integer topK;
    }

    @Data
    public static class ContextWindowResponse {
        private int contextLength;
    }

    @Data
    public static class MessageResponse {
        private Long id;
        private String role;
        private String content;
        private String reasoningContent;
        private String status;
        private List<RagDtos.SourceReference> sources;
        private LocalDateTime createdAt;
    }
}
