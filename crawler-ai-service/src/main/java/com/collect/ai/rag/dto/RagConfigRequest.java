package com.collect.ai.rag.dto;

import lombok.Data;

@Data
public class RagConfigRequest {
    private String embeddingProvider;
    private String embeddingBaseUrl;
    private String embeddingModel;
    private Integer dimensions;
    private Long vectorModelId;
    private Long chatModelId;
    private String chatProvider;
    private String chatBaseUrl;
    private String chatModel;
    private String apiKey;
    private Boolean clearApiKey;
    private Integer topK;
    private String vectorIndex;
    private String vectorIndexPrefix;
}
