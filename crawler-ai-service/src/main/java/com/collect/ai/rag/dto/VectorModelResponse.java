package com.collect.ai.rag.dto;

import lombok.Data;

@Data
public class VectorModelResponse {
    private Long id;
    private String name;
    private String embeddingProvider;
    private String embeddingBaseUrl;
    private String embeddingModel;
    private Integer dimensions;
    private Integer topK;
    private Integer chunkSize;
    private Integer chunkOverlap;
}
