package com.collect.ai.rag.dto;

import lombok.Data;

@Data
public class RerankModelRequest {
    private String name;
    private String rerankProvider;
    private String rerankBaseUrl;
    private String rerankModel;
    private String apiKey;
    private Boolean clearApiKey;
}
