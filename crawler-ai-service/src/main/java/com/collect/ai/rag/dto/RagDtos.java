package com.collect.ai.rag.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.List;

public final class RagDtos {

    private RagDtos() {
    }

    @Data
    public static class QueryRequest {
        private String question;
        private Integer topK;
        private Integer recallTopK;
        private Integer maxChunksPerDocument;
        private Long spiderId;
        private String spiderGroup;
        private Long knowledgeBaseId;
        private List<Long> knowledgeBaseIds;
        private Long chatModelId;
        /** 智能体（或其角色）生效的系统提示词，为空时使用默认提示词 */
        private String systemPrompt;
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
    public static class SyncTaskResponse {
        private String taskId;
        private Long knowledgeBaseId;
        /** 任务模式：FULL=全量重建，INCREMENTAL=增量同步新文档 */
        private String mode;
        private String status;
        private String message;
        private SyncResult result;
        private Long createdAt;
        private Long startedAt;
        private Long completedAt;
    }

    @Data
    public static class DocumentVectorizationResult {
        private String documentId;
        private long chunks;
    }

    @Data
    public static class SourceReference {
        private String id;
        private String chunkId;
        @JsonSerialize(using = ToStringSerializer.class)
        private Long knowledgeBaseId;
        private String title;
        /** 分块所在章节标题（Markdown 标题层级拼接，可为空） */
        private String heading;
        private String url;
        private String spiderName;
        private String sourceType;
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
        private Long waitDurationMs;
        private Long thinkingDurationMs;
        private Long generationDurationMs;
    }
}
