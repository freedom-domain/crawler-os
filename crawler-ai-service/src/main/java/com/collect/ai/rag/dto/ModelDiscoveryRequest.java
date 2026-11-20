package com.collect.ai.rag.dto;

import lombok.Data;

@Data
public class ModelDiscoveryRequest {
    private Long modelId;
    private String provider;
    private String baseUrl;
    private String apiKey;
}
