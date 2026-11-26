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
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(
                using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
        private Long assistantId;
        private String title;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    public static class SendMessageRequest {
        private String question;
        private Integer topK;
        private Integer recallTopK;
        private Integer maxChunksPerDocument;
        private Long chatModelId;
    }

    @Data
    public static class SuggestionRequest {
        private Long assistantId;
        private Long chatModelId;
    }

    @Data
    public static class RenameConversationRequest {
        private String title;
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
        private Long durationMs;
        private Long waitDurationMs;
        private Long thinkingDurationMs;
        private Long generationDurationMs;
        private String status;
        private List<RagDtos.SourceReference> sources;
        private LocalDateTime createdAt;
    }
}
