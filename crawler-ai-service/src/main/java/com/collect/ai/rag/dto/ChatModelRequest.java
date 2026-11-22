package com.collect.ai.rag.dto;

import lombok.Data;

@Data
public class ChatModelRequest {
    private String name;
    private String chatProvider;
    private String chatBaseUrl;
    private String chatModel;
    private Integer contextLength;
    private String apiKey;
    private Boolean clearApiKey;
    private Boolean defaultModel;
}
