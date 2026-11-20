package com.collect.ai.rag.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class VectorModelResponse {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String name;
    private String embeddingProvider;
    private String embeddingBaseUrl;
    private String embeddingModel;
    private boolean apiKeyConfigured;
    private Integer dimensions;
    private Integer topK;
    private Integer chunkSize;
    private Integer chunkOverlap;
}
