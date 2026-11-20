package com.collect.ai.rag.dto;

import lombok.Data;

@Data
public class VectorModelRequest {
    private String name;
    private String embeddingProvider;
    private String embeddingBaseUrl;
    private String embeddingModel;
    private String apiKey;
    private Boolean clearApiKey;
    private Integer dimensions;
    private Integer topK;
    private Integer chunkSize;
    private Integer chunkOverlap;
}
