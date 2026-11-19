package com.collect.ai.rag.dto;

import lombok.Data;

@Data
public class RagConfigResponse {
    private String environment;
    private Boolean enabled;
    private String sourceIndex;
    private String embeddingProvider;
    private String embeddingBaseUrl;
    private String embeddingModel;
    private Integer dimensions;
    private String chatProvider;
    private String chatBaseUrl;
    private String chatModel;
    private boolean apiKeyConfigured;
    private Integer topK;
    private Integer chunkSize;
    private Integer chunkOverlap;
    private String vectorIndexPrefix;
    private String vectorFieldPrefix;
    private String vectorIndex;
    private String vectorField;
}
