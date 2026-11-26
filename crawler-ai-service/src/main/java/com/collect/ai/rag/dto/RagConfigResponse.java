package com.collect.ai.rag.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class RagConfigResponse {
    private String environment;
    private String sourceIndex;
    private String embeddingProvider;
    private String embeddingBaseUrl;
    private String embeddingModel;
    private Integer dimensions;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long vectorModelId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long chatModelId;
    private String chatProvider;
    private String chatBaseUrl;
    private String chatModel;
    private boolean apiKeyConfigured;
    private Integer topK;
    private Integer maxChunksPerDocument;
    private String vectorIndexName;
    private String vectorIndexPrefix;
    private String vectorIndex;
    private String vectorField;
    private String uploadBucket;
}
